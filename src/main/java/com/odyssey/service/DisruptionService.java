package com.odyssey.service;

import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.response.DisruptionResponse;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.odyssey.entity.ServiceDepartureEntity;
import com.odyssey.entity.ServiceEntity;
import com.odyssey.repository.jpa.ServiceDepartureJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptionService {

    private final TripRepository tripRepository;
    private final DependencyGraph dependencyGraph;
    private final NotificationService notificationService;
    private final InventoryService inventoryService;
    private final ScoringService scoringService;
    private final com.odyssey.config.RecoveryProperties recoveryProperties;

    @Autowired(required = false)
    private MarketplaceService marketplaceService;

    @Autowired(required = false)
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired(required = false)
    private com.odyssey.repository.jpa.ProviderJpaRepository providerRepo;

    public void setMarketplaceService(MarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    public void setDepartureRepo(ServiceDepartureJpaRepository departureRepo) {
        this.departureRepo = departureRepo;
    }

    public DisruptionResponse reportDisruption(String tripId, ReportDisruptionRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        Booking disruptedBooking = trip.getBookings().stream()
                .filter(b -> b.getId().equalsIgnoreCase(request.getBookingId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + request.getBookingId()));

        LocalDateTime disruptionTime = request.getDisruptionTime() != null
                ? request.getDisruptionTime()
                : ("TRIP-DEMO-001".equalsIgnoreCase(trip.getId())
                        ? recoveryProperties.getDemoDisruptionTime()
                        : LocalDateTime.now());

        // If options already generated for previous disruption, clear them and set note
        if (trip.getOptions() != null && !trip.getOptions().isEmpty()) {
            log.info("Clearing stale recovery options for trip {} due to updated disruption on booking {}",
                    trip.getId(), disruptedBooking.getId());
            trip.getOptions().clear();
            trip.setRecoveryNote("Status changed, please search again");
        }

        // 1. Create and record Disruption
        Disruption disruption = Disruption.builder()
                .id(IdGenerator.generate("DIS"))
                .type(request.getType())
                .bookingId(disruptedBooking.getId())
                .delayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0)
                .reason(request.getReason())
                .disruptionTime(disruptionTime)
                .reportedAt(LocalDateTime.now())
                .source(request.getSource() != null ? request.getSource() : "MANUAL")
                .actor(request.getActor() != null ? request.getActor().name() : "USER")
                .build();

        trip.getActiveDisruptions().add(disruption);

        // 2. Execute DependencyGraph BFS impact analysis
        TripImpactReport impactReport = dependencyGraph.analyze(trip, disruptedBooking, disruption);

        // 3. Update root booking
        if (disruption.getType() == DisruptionType.CANCELLATION) {
            disruptedBooking.setStatus(BookingStatus.CANCELLED);
            disruptedBooking.setLastStatusState("CANCELLED");
            disruptedBooking.setLastDelayMinutes(0);
        } else if (disruption.getType() == DisruptionType.FLIGHT_DELAY) {
            disruptedBooking.setStatus(BookingStatus.DELAYED);
            disruptedBooking.setLastStatusState("DELAYED");
            disruptedBooking.setLastDelayMinutes(disruption.getDelayMinutes());
            if (disruptedBooking.getEndTime() != null && disruption.getDelayMinutes() > 0) {
                disruptedBooking.setEndTime(disruptedBooking.getEndTime().plusMinutes(disruption.getDelayMinutes()));
            }
        } else {
            disruptedBooking.setStatus(BookingStatus.AT_RISK);
            disruptedBooking.setLastStatusState("DELAYED");
            disruptedBooking.setLastDelayMinutes(disruption.getDelayMinutes());
        }
        disruptedBooking.setLastStatusSource(request.getSource() != null ? request.getSource() : "MANUAL");
        disruptedBooking.setLastCheckedAt(LocalDateTime.now());

        // 4. Update downstream bookings according to BFS computation
        Map<String, BookingImpact> impactMap = impactReport.getImpactedBookings().stream()
                .collect(Collectors.toMap(BookingImpact::getBookingId, bi -> bi));

        for (Booking b : trip.getBookings()) {
            if (impactMap.containsKey(b.getId())) {
                BookingImpact bi = impactMap.get(b.getId());
                b.setStatus(bi.getStatus());
            }
        }

        // 5. Generate simulated SMS / WhatsApp alert
        NotificationMessage notification = notificationService.generateAlert(trip, disruptedBooking, disruption, impactReport);
        impactReport.setNotification(notification);
        trip.setLatestImpact(impactReport);
        trip.setStatus("DISRUPTED");

        try {
            notificationService.sendDisruptionAlert(trip, disruptedBooking, disruption, impactReport);
        } catch (Exception e) {
            log.error("Failed to dispatch disruption notification for trip {} disruption {}: {}",
                    tripId, disruption.getId(), e.getClass().getSimpleName());
        }

        // 6. Write AuditEntry records (SYSTEM and AGENT)
        AuditActor actor = request.getActor() != null ? request.getActor() : AuditActor.SYSTEM;
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(actor)
                .action("DISRUPTION_RECORDED")
                .details(String.format("Recorded [%s] on booking %s (%s). Delay: %d mins. Reason: %s",
                        disruption.getType(), disruptedBooking.getId(), disruptedBooking.getProvider(),
                        disruption.getDelayMinutes(), disruption.getReason()))
                .build());

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.AGENT)
                .action("IMPACT_ANALYSIS_COMPLETED")
                .details("BFS traversal analyzed " + impactReport.getImpactedBookings().size() +
                        " downstream bookings. Summary: " + impactReport.getSummary())
                .build());

        String maskedRecip = com.odyssey.util.NotificationMasker.maskPhone(notification.getRecipient());
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.AGENT)
                .action("NOTIFICATION_DISPATCHED")
                .details("Alert dispatched via " + notification.getChannel() +
                        " [Level: " + notification.getAlertLevel() + "] to " + maskedRecip)
                .build());

        Trip saved = tripRepository.save(trip);

        return DisruptionResponse.builder()
                .trip(saved)
                .impact(impactReport)
                .notification(notification)
                .build();
    }

    public TripImpactReport getImpactReport(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        if (trip.getLatestImpact() != null) {
            return trip.getLatestImpact();
        }

        // If not stored but disruptions exist, analyze last disruption
        if (!trip.getActiveDisruptions().isEmpty()) {
            Disruption lastDisruption = trip.getActiveDisruptions().get(trip.getActiveDisruptions().size() - 1);
            Booking root = trip.getBookings().stream()
                    .filter(b -> b.getId().equalsIgnoreCase(lastDisruption.getBookingId()))
                    .findFirst()
                    .orElse(null);
            if (root != null) {
                TripImpactReport report = dependencyGraph.analyze(trip, root, lastDisruption);
                NotificationMessage notification = notificationService.generateAlert(trip, root, lastDisruption, report);
                report.setNotification(notification);
                trip.setLatestImpact(report);
                tripRepository.save(trip);
                return report;
            }
        }

        // Return empty baseline report
        return TripImpactReport.builder()
                .tripId(trip.getId())
                .summary("No active disruptions currently recorded for trip " + tripId + ". Itinerary is on schedule.")
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    public RecoveryResponse generateRecoveryOptions(String tripId) {
        return generateRecoveryOptions(tripId, null);
    }

    public RecoveryResponse generateRecoveryOptions(String tripId, com.odyssey.dto.request.RelaxConstraintsRequest relaxRequest) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));
        boolean isDisruptedTrip = "DISRUPTED".equalsIgnoreCase(trip.getStatus())
                || (trip.getActiveDisruptions() != null && !trip.getActiveDisruptions().isEmpty())
                || (trip.getBookings() != null && trip.getBookings().stream()
                        .anyMatch(b -> b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.DELAYED || b.getStatus() == BookingStatus.AT_RISK));

        if (!isDisruptedTrip) {
            throw new IllegalStateException("Trip is not disrupted. Recovery options can only be generated for disrupted trips.");
        }

        // Find the disrupted booking to recover
        Booking disruptedBooking = null;
        if (!trip.getActiveDisruptions().isEmpty()) {
            Disruption lastDisruption = trip.getActiveDisruptions().get(trip.getActiveDisruptions().size() - 1);
            disruptedBooking = trip.getBookings().stream()
                    .filter(b -> b.getId().equalsIgnoreCase(lastDisruption.getBookingId()))
                    .findFirst()
                    .orElse(null);
        }

        if (disruptedBooking == null) {
            disruptedBooking = trip.getBookings().stream()
                    .filter(b -> b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.DELAYED || b.getStatus() == BookingStatus.AT_RISK)
                    .findFirst()
                    .orElse(trip.getBookings().isEmpty() ? null : trip.getBookings().get(0));
        }

        if (disruptedBooking == null) {
            throw new IllegalStateException("No bookings available on trip to generate recovery options for.");
        }

        // Search inventory
        String origin = disruptedBooking.getFrom();
        String destination = disruptedBooking.getTo();

        // Disruption time and station buffer calculation
        LocalDateTime now = LocalDateTime.now();
        int stationBufferMinutes = recoveryProperties.getStationBufferMinutes();

        LocalDateTime disruptionTime = null;
        if (!trip.getActiveDisruptions().isEmpty()) {
            Disruption lastDisruption = trip.getActiveDisruptions().get(trip.getActiveDisruptions().size() - 1);
            disruptionTime = lastDisruption.getDisruptionTime();
        }

        LocalDateTime earliestFeasibleDeparture;
        if ("TRIP-DEMO-001".equalsIgnoreCase(trip.getId())) {
            if (disruptionTime == null) {
                disruptionTime = recoveryProperties.getDemoDisruptionTime();
            }
            earliestFeasibleDeparture = disruptionTime.plusMinutes(stationBufferMinutes);
        } else {
            // User trip: earliest feasible departure must be max(now + stationBufferMinutes, trip start time)
            LocalDateTime tripStartTime = null;
            if (disruptedBooking != null && disruptedBooking.getStartTime() != null) {
                tripStartTime = disruptedBooking.getStartTime();
            } else if (trip.getBookings() != null && !trip.getBookings().isEmpty()) {
                tripStartTime = trip.getBookings().stream()
                        .map(Booking::getStartTime)
                        .filter(java.util.Objects::nonNull)
                        .min(LocalDateTime::compareTo)
                        .orElse(null);
            }
            if (tripStartTime == null) {
                tripStartTime = now;
            }

            LocalDateTime minBufferFromNow = (disruptionTime != null ? disruptionTime : now).plusMinutes(stationBufferMinutes);
            // If the trip date is in the future, search from that date, not from now
            if (tripStartTime.isAfter(minBufferFromNow)) {
                earliestFeasibleDeparture = tripStartTime;
            } else {
                earliestFeasibleDeparture = minBufferFromNow;
            }

            if (disruptionTime == null) {
                disruptionTime = now;
            }
        }

        String originKey = InventoryService.normalizeCityKey(origin);
        String destinationKey = InventoryService.normalizeCityKey(destination);
        int totalInventorySize = inventoryService.getAll().size();

        List<InventoryItem> candidates = new ArrayList<>();
        List<RejectedOption> marketplaceRejected = new ArrayList<>();

        // 1. Search marketplace departures for this city pair if available
        CityInfo c1 = InventoryService.findCity(origin);
        CityInfo c2 = InventoryService.findCity(destination);

        if (c1 != null && c2 != null && !c1.getKey().equalsIgnoreCase(c2.getKey()) && departureRepo != null) {
            LocalDate startDate = earliestFeasibleDeparture.toLocalDate();
            LocalDate endDate = startDate.plusDays(1);
            if (trip.getConstraints() != null && trip.getConstraints().getMustArriveBy() != null) {
                LocalDate dl = trip.getConstraints().getMustArriveBy().toLocalDate();
                if (dl.isAfter(endDate)) {
                    endDate = dl;
                }
            }

            // Addition 6: On-demand generation during recovery covers only the dates needed, is idempotent and transactional, and never blocks recovery if generation fails
            if (marketplaceService != null) {
                try {
                    marketplaceService.ensureServicesAndDeparturesForDates(c1, c2, startDate, endDate);
                } catch (Exception e) {
                    log.warn("On-demand marketplace generation failed for {} -> {}: {}. Falling back to ESTIMATED.", c1.getKey(), c2.getKey(), e.getMessage());
                }
            }

            List<ServiceDepartureEntity> departures = departureRepo.findDeparturesBetweenDates(
                    c1.getKey(), c1.getName(), c2.getKey(), c2.getName(), startDate, endDate);

            int passengers = 1;
            if (disruptedBooking.getSeatNumbers() != null && !disruptedBooking.getSeatNumbers().isBlank()) {
                passengers = Math.max(1, disruptedBooking.getSeatNumbers().split(",").length);
            } else if (disruptedBooking.getPassengerNames() != null && !disruptedBooking.getPassengerNames().isBlank()) {
                passengers = Math.max(1, disruptedBooking.getPassengerNames().split(",").length);
            }

            String disruptedDepId = disruptedBooking.getDepartureId();

            for (ServiceDepartureEntity dep : departures) {
                ServiceEntity s = dep.getService();
                LocalDate depDate = dep.getTravelDate();
                LocalDate arrDate = Boolean.TRUE.equals(s.getArrivesNextDay()) ? depDate.plusDays(1) : depDate;
                int delay = dep.getDelayMinutes() != null ? dep.getDelayMinutes() : 0;
                LocalDateTime depDateTime = depDate.atTime(s.getDepartureTime()).plusMinutes(delay);
                LocalDateTime arrDateTime = arrDate.atTime(s.getArrivalTime()).plusMinutes(delay);
                BigDecimal totalLegCost = s.getPrice().multiply(BigDecimal.valueOf(passengers));

                BookingType mode;
                try {
                    mode = BookingType.valueOf(s.getMode().toUpperCase());
                } catch (Exception e) {
                    mode = BookingType.BUS;
                }

                String opName = s.getOperator() != null ? s.getOperator().getName() : "Odyssey Partner";
                boolean isGen = "GENERATED".equalsIgnoreCase(s.getSource());
                boolean isProvider = "PROVIDER".equalsIgnoreCase(s.getSource()) || "PROVIDER".equalsIgnoreCase(dep.getSource());

                if (isProvider) {
                    String provId = dep.getProviderId() != null ? dep.getProviderId() : s.getProviderId();
                    if (provId == null || providerRepo == null) {
                        continue;
                    }
                    com.odyssey.entity.ProviderEntity prov = providerRepo.findById(provId).orElse(null);
                    if (prov == null || prov.getStatus() != com.odyssey.model.enums.ProviderStatus.APPROVED) {
                        continue; // Exclude unapproved, suspended, or rejected provider departures
                    }
                }

                String schedLabel;
                if (isProvider) {
                    schedLabel = "Provider-listed";
                } else if (isGen) {
                    schedLabel = "Generated schedule, not an official timetable";
                } else {
                    schedLabel = "Official schedule";
                }

                // Addition 7: Exclude the disrupted departure itself from recovery candidates
                if (disruptedDepId != null && disruptedDepId.equals(dep.getId())) {
                    marketplaceRejected.add(RejectedOption.builder()
                            .id(dep.getId())
                            .mode(mode)
                            .provider(opName)
                            .cost(totalLegCost)
                            .departure(depDateTime)
                            .arrival(arrDateTime)
                            .reason("Disrupted departure (cannot rebook the same service)")
                            .blockedBy("Disrupted")
                            .build());
                    continue;
                }

                if ("CANCELLED".equalsIgnoreCase(dep.getStatus())) {
                    marketplaceRejected.add(RejectedOption.builder()
                            .id(dep.getId())
                            .mode(mode)
                            .provider(opName)
                            .cost(totalLegCost)
                            .departure(depDateTime)
                            .arrival(arrDateTime)
                            .reason("Departure is cancelled by carrier")
                            .blockedBy("Cancelled")
                            .build());
                    continue;
                }

                if (dep.getSeatsAvailable() < passengers) {
                    marketplaceRejected.add(RejectedOption.builder()
                            .id(dep.getId())
                            .mode(mode)
                            .provider(opName)
                            .cost(totalLegCost)
                            .departure(depDateTime)
                            .arrival(arrDateTime)
                            .reason("Sold out (no seats available)")
                            .blockedBy("Capacity")
                            .build());
                    continue;
                }

                candidates.add(InventoryItem.builder()
                        .id(dep.getId())
                        .mode(mode)
                        .provider(opName)
                        .from(s.getOriginCity())
                        .to(s.getDestinationCity())
                        .departure(depDateTime)
                        .arrival(arrDateTime)
                        .cost(totalLegCost)
                        .transfers(0)
                        .seatsAvailable(dep.getSeatsAvailable())
                        .reliabilityScore(s.getReliabilityScore())
                        .source("MARKETPLACE")
                        .operator(opName)
                        .vehicleNumber(s.getVehicleNumber())
                        .serviceDepartureId(dep.getId())
                        .scheduleLabel(schedLabel)
                        .build());
            }
        }

        // Also search inventory items
        List<InventoryItem> invItems = inventoryService.search(origin, destination, earliestFeasibleDeparture);
        if (candidates.isEmpty()) {
            candidates.addAll(invItems);
        } else {
            for (InventoryItem inv : invItems) {
                if (!"ESTIMATED".equalsIgnoreCase(inv.getSource())) {
                    candidates.add(inv);
                }
            }
            if (candidates.isEmpty()) {
                candidates.addAll(invItems);
            }
        }

        // Effective constraints (cloned for temporary relaxation without modifying saved trip)
        Constraints savedConstraints = trip.getConstraints();
        Constraints effectiveConstraints = savedConstraints != null
                ? Constraints.builder()
                        .maxBudget(savedConstraints.getMaxBudget())
                        .mustArriveBy(savedConstraints.getMustArriveBy())
                        .avoidOvernightTravel(savedConstraints.getAvoidOvernightTravel())
                        .preferredModes(savedConstraints.getPreferredModes() != null ? new java.util.ArrayList<>(savedConstraints.getPreferredModes()) : new java.util.ArrayList<>())
                        .hardCommitments(savedConstraints.getHardCommitments() != null ? new java.util.ArrayList<>(savedConstraints.getHardCommitments()) : new java.util.ArrayList<>())
                        .structuredCommitments(savedConstraints.getStructuredCommitments() != null ? new java.util.ArrayList<>(savedConstraints.getStructuredCommitments()) : new java.util.ArrayList<>())
                        .build()
                : new Constraints();

        List<String> relaxations = new java.util.ArrayList<>();
        if (relaxRequest != null) {
            if (Boolean.TRUE.equals(relaxRequest.getAllowOvernight())) {
                effectiveConstraints.setAvoidOvernightTravel(false);
                relaxations.add("Allow overnight travel (avoidOvernightTravel=false)");
            }
            if (relaxRequest.getExtendDeadlineHours() != null && relaxRequest.getExtendDeadlineHours() > 0) {
                LocalDateTime dl = effectiveConstraints.getMustArriveBy() != null
                        ? effectiveConstraints.getMustArriveBy()
                        : LocalDateTime.now().plusHours(12);
                effectiveConstraints.setMustArriveBy(dl.plusHours(relaxRequest.getExtendDeadlineHours()));
                relaxations.add(String.format("Extend deadline by %d hours (new deadline %s)",
                        relaxRequest.getExtendDeadlineHours(),
                        effectiveConstraints.getMustArriveBy().format(DateTimeFormatter.ofPattern("HH:mm"))));
            }
            if (relaxRequest.getRaiseBudgetPercent() != null && relaxRequest.getRaiseBudgetPercent() > 0) {
                BigDecimal budget = effectiveConstraints.getMaxBudget() != null ? effectiveConstraints.getMaxBudget() : new BigDecimal("15000.00");
                BigDecimal factor = BigDecimal.valueOf(1.0 + (relaxRequest.getRaiseBudgetPercent() / 100.0));
                BigDecimal newBudget = budget.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
                effectiveConstraints.setMaxBudget(newBudget);
                relaxations.add(String.format("Raise budget by %d%% (new limit Rs %s)",
                        relaxRequest.getRaiseBudgetPercent(), newBudget.setScale(0, java.math.RoundingMode.HALF_UP)));
            }
        }

        trip.setConstraints(effectiveConstraints);

        // Score and rank with effective constraints
        RecoveryResponse response = scoringService.scoreAndRank(trip, disruptedBooking, candidates,
                earliestFeasibleDeparture, disruptionTime, stationBufferMinutes);

        // Phase 19: Check if provider offered an alternative vehicle for this disrupted departure
        String altDepId = disruptedBooking.getAlternativeDepartureId();
        if (altDepId == null && disruptedBooking.getDepartureId() != null && departureRepo != null) {
            ServiceDepartureEntity depEntity = departureRepo.findById(disruptedBooking.getDepartureId()).orElse(null);
            if (depEntity != null) {
                altDepId = depEntity.getAlternativeDepartureId();
            }
        }
        if (altDepId != null && departureRepo != null) {
            ServiceDepartureEntity altDep = departureRepo.findById(altDepId).orElse(null);
            if (altDep != null && altDep.getSeatsAvailable() != null && altDep.getSeatsAvailable() > 0) {
                String provName = "Provider";
                if (altDep.getProviderId() != null && providerRepo != null) {
                    provName = providerRepo.findById(altDep.getProviderId()).map(com.odyssey.entity.ProviderEntity::getBusinessName).orElse("Provider");
                }
                String note = "Suggested by " + provName + " as a direct replacement";

                boolean tagged = false;
                if (response.getOptions() != null) {
                    for (Option opt : response.getOptions()) {
                        if (opt.getLegs() != null) {
                            for (Booking leg : opt.getLegs()) {
                                if (altDepId.equals(leg.getDepartureId()) || altDepId.equals(leg.getId())) {
                                    opt.setBestAvailableNote(note);
                                    tagged = true;
                                    break;
                                }
                            }
                        }
                        if (tagged) break;
                    }
                }

                if (!tagged) {
                    ServiceEntity s = altDep.getService();
                    BookingType mode = BookingType.BUS;
                    try {
                        if (s != null && s.getMode() != null) mode = BookingType.valueOf(s.getMode().toUpperCase());
                    } catch (Exception ignored) {}

                    int passengers = 1;
                    if (disruptedBooking.getSeatNumbers() != null && !disruptedBooking.getSeatNumbers().isBlank()) {
                        passengers = Math.max(1, disruptedBooking.getSeatNumbers().split(",").length);
                    }
                    BigDecimal legCost = (s != null && s.getPrice() != null ? s.getPrice() : BigDecimal.valueOf(500)).multiply(BigDecimal.valueOf(passengers));
                    LocalDateTime depTime = altDep.getTravelDate().atTime(s != null && s.getDepartureTime() != null ? s.getDepartureTime() : java.time.LocalTime.of(8, 0));
                    LocalDate arrDate = (s != null && Boolean.TRUE.equals(s.getArrivesNextDay())) ? altDep.getTravelDate().plusDays(1) : altDep.getTravelDate();
                    LocalDateTime arrTime = arrDate.atTime(s != null && s.getArrivalTime() != null ? s.getArrivalTime() : java.time.LocalTime.of(12, 0));

                    Booking altLeg = Booking.builder()
                            .id("LEG-" + altDep.getId())
                            .type(mode)
                            .provider(provName)
                            .from(s != null ? s.getOriginCity() : disruptedBooking.getFrom())
                            .to(s != null ? s.getDestinationCity() : disruptedBooking.getTo())
                            .startTime(depTime)
                            .endTime(arrTime)
                            .cost(legCost)
                            .status(BookingStatus.PROPOSED)
                            .source("MARKETPLACE")
                            .departureId(altDep.getId())
                            .operator(provName)
                            .vehicleNumber(s != null ? s.getVehicleNumber() : "-")
                            .seatsAvailable(altDep.getSeatsAvailable())
                            .scheduleLabel("Provider-listed")
                            .build();

                    Option altOption = Option.builder()
                            .id("OPT-ALT-" + altDep.getId())
                            .label(OptionLabel.SAFEST)
                            .legs(List.of(altLeg))
                            .totalCost(legCost)
                            .arrivalTime(arrTime)
                            .score(90.0)
                            .source("MARKETPLACE")
                            .bestAvailableNote(note)
                            .explanation(String.format("Alternative departure offered directly by %s (%s). Arrives at %s.",
                                    provName, s != null ? s.getVehicleNumber() : "", arrTime.format(DateTimeFormatter.ofPattern("HH:mm"))))
                            .build();

                    if (response.getOptions() == null) {
                        response.setOptions(new ArrayList<>());
                    } else if (response.getOptions() instanceof java.util.Collections) {
                        response.setOptions(new ArrayList<>(response.getOptions()));
                    }
                    response.getOptions().add(0, altOption);
                }
            }
        }

        if (!marketplaceRejected.isEmpty()) {
            List<RejectedOption> allRejected = new ArrayList<>(response.getRejectedOptions() != null ? response.getRejectedOptions() : List.of());
            allRejected.addAll(marketplaceRejected);
            response.setRejectedOptions(allRejected);
        }

        if (!relaxations.isEmpty()) {
            response.setRelaxationApplied(String.join("; ", relaxations));
            response.setExplanation(response.getExplanation() + " (Temporary relaxation applied: " + String.join("; ", relaxations) + ")");
        }

        // Restore original saved constraints so the trip in database is NOT modified
        trip.setConstraints(savedConstraints);

        // Store options on trip
        trip.setOptions(response.getOptions());

        // Audit entry
        String optionSummary = response.getOptions().stream()
                .map(o -> String.format("%s: %s (Rs %s, Arr %s)",
                        o.getLabel(),
                        o.getLegs().isEmpty() ? "None" : o.getLegs().get(0).getProvider(),
                        o.getTotalCost(),
                        o.getArrivalTime() != null ? o.getArrivalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "N/A"))
                .collect(Collectors.joining("; "));

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.AGENT)
                .action("RECOVERY_OPTIONS_GENERATED")
                .details(String.format("Generated %d options for disrupted booking %s. %s. NoPerfectOption: %s%s",
                        response.getOptions().size(), disruptedBooking.getId(), optionSummary, response.isNoPerfectOption(),
                        relaxations.isEmpty() ? "" : ". Relaxations: " + String.join("; ", relaxations)))
                .build());

        tripRepository.save(trip);
        return response;
    }
}
