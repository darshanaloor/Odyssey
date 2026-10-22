package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.repository.TripRepository;
import com.odyssey.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecoveryService {

    private final TripRepository tripRepository;
    private final RecoveryProperties recoveryProperties;
    private final LlmService llmService;
    private final NotificationService notificationService;

    @Autowired
    public RecoveryService(TripRepository tripRepository, RecoveryProperties recoveryProperties, LlmService llmService, NotificationService notificationService) {
        this.tripRepository = tripRepository;
        this.recoveryProperties = recoveryProperties;
        this.llmService = llmService;
        this.notificationService = notificationService;
    }

    public RecoveryService(TripRepository tripRepository, RecoveryProperties recoveryProperties, LlmService llmService) {
        this.tripRepository = tripRepository;
        this.recoveryProperties = recoveryProperties;
        this.llmService = llmService;
        this.notificationService = null;
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public RecoveryPlan buildPlan(String tripId, String optionId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        validateForPlan(trip, optionId);

        Option chosenOption = trip.getOptions().stream()
                .filter(o -> o.getId().equalsIgnoreCase(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with ID: " + optionId));

        RecoveryPlan plan = generatePlan(trip, chosenOption);

        // Save latest plan as uncommitted draft without modifying trip or booking statuses
        trip.setLatestPlan(plan);
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.AGENT)
                .action("RECOVERY_PLAN_DRAFTED")
                .details(String.format("Generated uncommitted recovery plan for option %s (%s). Trip statuses unchanged.",
                        chosenOption.getId(), chosenOption.getLabel()))
                .build());

        tripRepository.save(trip);

        if (notificationService != null) {
            try {
                notificationService.sendPlanReadyAlert(trip, chosenOption, plan);
            } catch (Exception e) {
                log.error("Failed to dispatch plan ready notification: {}", e.getMessage());
            }
        }

        return plan;
    }

    public RecoveryPlan approvePlan(String tripId, String optionId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        validateForPlan(trip, optionId);

        Option chosenOption = trip.getOptions().stream()
                .filter(o -> o.getId().equalsIgnoreCase(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with ID: " + optionId));

        RecoveryPlan plan = generatePlan(trip, chosenOption);

        // Commit affected bookings based on plan's itinerary diff:
        java.util.Map<String, ItineraryDiffItem> diffMap = plan.getItineraryDiff().stream()
                .filter(d -> d.getBookingId() != null)
                .collect(Collectors.toMap(ItineraryDiffItem::getBookingId, d -> d, (a, b) -> a));

        for (Booking b : trip.getBookings()) {
            if (diffMap.containsKey(b.getId())) {
                ItineraryDiffItem diff = diffMap.get(b.getId());
                if (diff.getNewStatus() != null) {
                    b.setStatus(diff.getNewStatus());
                }
                if (diff.getNewStart() != null) {
                    b.setStartTime(diff.getNewStart());
                }
                if (diff.getNewEnd() != null) {
                    b.setEndTime(diff.getNewEnd());
                }
            }
        }

        // Add new travel leg and replacement cab with status REBOOKED
        if (plan.getNewLeg() != null) {
            Booking newLeg = plan.getNewLeg();
            newLeg.setStatus(BookingStatus.REBOOKED);
            trip.getBookings().add(newLeg);
        }

        if (plan.getReplacementCab() != null) {
            Booking replacementCab = plan.getReplacementCab();
            replacementCab.setStatus(BookingStatus.REBOOKED);
            trip.getBookings().add(replacementCab);
        }

        trip.getBookings().sort(java.util.Comparator.comparing(Booking::getStartTime, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        // 5. Update checklist to done
        for (ActionChecklistItem item : plan.getChecklist()) {
            item.setDone(true);
        }

        // 6. Update trip status to RESOLVED
        trip.setStatus("RESOLVED");
        trip.setLatestPlan(plan);

        // 7. Record USER approval audit entry
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("RECOVERY_PLAN_APPROVED")
                .details(String.format("User approved recovery option %s (%s). Net trip cost: Rs %s. Trip status set to RESOLVED.",
                        chosenOption.getId(), chosenOption.getLabel(), plan.getCostBreakdown().getNetTripCost()))
                .build());

        tripRepository.save(trip);

        if (notificationService != null) {
            try {
                notificationService.sendPlanApprovedAlert(trip, plan);
            } catch (Exception e) {
                log.error("Failed to dispatch plan approved notification: {}", e.getMessage());
            }
        }

        return plan;
    }

    public Trip rejectPlan(String tripId, String optionId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        if ("RESOLVED".equalsIgnoreCase(trip.getStatus())) {
            throw new ConflictException("Trip " + tripId + " is already RESOLVED.");
        }

        Option chosenOption = trip.getOptions().stream()
                .filter(o -> o.getId().equalsIgnoreCase(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with ID: " + optionId));

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("RECOVERY_PLAN_REJECTED")
                .details(String.format("User rejected recovery option %s (%s). Trip remains DISRUPTED.",
                        chosenOption.getId(), chosenOption.getLabel()))
                .build());

        trip.setStatus("DISRUPTED");
        return tripRepository.save(trip);
    }

    public RecoveryPlan getLatestPlan(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        if (trip.getLatestPlan() == null) {
            throw new ResourceNotFoundException("No recovery plan has been generated yet for trip: " + tripId);
        }
        return trip.getLatestPlan();
    }

    private void validateForPlan(Trip trip, String optionId) {
        if (optionId == null || optionId.isBlank()) {
            throw new IllegalArgumentException("optionId is required");
        }
        if ("RESOLVED".equalsIgnoreCase(trip.getStatus())) {
            throw new ConflictException("Trip " + trip.getId() + " is already RESOLVED. Recovery plan cannot be generated.");
        }
        if (trip.getOptions() == null || trip.getOptions().isEmpty()) {
            throw new ConflictException("No recovery options exist for trip " + trip.getId() + ". Run /recover first.");
        }
    }

    private String toTitleCase(String text) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            if (w.length() == 1) {
                sb.append(w.toUpperCase());
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private RecoveryPlan generatePlan(Trip trip, Option chosenOption) {
        Booking chosenLeg = chosenOption.getLegs().get(0);
        LocalDateTime arrival = chosenOption.getArrivalTime();

        // 0. Identify disrupted booking
        String disruptedBookingId = null;
        if (trip.getActiveDisruptions() != null && !trip.getActiveDisruptions().isEmpty()) {
            disruptedBookingId = trip.getActiveDisruptions().get(trip.getActiveDisruptions().size() - 1).getBookingId();
        }
        final String finalDisruptedId = disruptedBookingId;
        Booking disruptedBooking = trip.getBookings().stream()
                .filter(b -> finalDisruptedId != null && b.getId().equalsIgnoreCase(finalDisruptedId))
                .findFirst()
                .orElseGet(() -> trip.getBookings().stream()
                        .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                        .findFirst()
                        .orElse(null));
        if (disruptedBooking == null && !trip.getBookings().isEmpty()) {
            disruptedBooking = trip.getBookings().stream()
                    .filter(b -> b.getType() != BookingType.HOTEL && b.getType() != BookingType.CAB && b.getType() != BookingType.EVENT)
                    .findFirst()
                    .orElse(trip.getBookings().get(0));
        }

        // Destination city from disrupted leg's 'to' or chosen leg's 'to'
        String destinationLocation = disruptedBooking != null && disruptedBooking.getTo() != null
                ? disruptedBooking.getTo() : (chosenLeg != null ? chosenLeg.getTo() : "");
        CityInfo destCityInfo = InventoryService.findCity(destinationLocation);
        String destinationCity = destCityInfo != null ? destCityInfo.getName()
                : toTitleCase(InventoryService.normalizeCityKey(destinationLocation));

        // Bookings lookup
        Booking oldCabBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.CAB)
                .findFirst()
                .orElse(null);

        boolean oldCabDependedOnDisruption = oldCabBooking != null && (
                disruptedBooking == null
                || oldCabBooking.getDependsOn() == null
                || oldCabBooking.getDependsOn().isEmpty()
                || oldCabBooking.getDependsOn().contains(disruptedBooking.getId())
                || oldCabBooking.getStatus() == BookingStatus.CANCELLED
        );

        boolean createCab = oldCabBooking != null || Boolean.TRUE.equals(trip.getNeedsDestinationCab());

        Booking hotelBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.HOTEL)
                .findFirst()
                .orElse(null);

        Booking eventBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.EVENT)
                .findFirst()
                .orElse(null);

        boolean hasCommitments = trip.getHardCommitments() != null && !trip.getHardCommitments().isEmpty();

        // 1. Replacement Cab
        LocalDateTime cabPickup = arrival.plusMinutes(recoveryProperties.getReplacementCabBufferMinutes());
        LocalDateTime cabDropoff = cabPickup.plusMinutes(45);

        String cabTo;
        if (oldCabBooking != null && oldCabBooking.getTo() != null && !oldCabBooking.getTo().isBlank()) {
            cabTo = oldCabBooking.getTo();
        } else if (trip.getDestinationCabDropoff() != null && !trip.getDestinationCabDropoff().isBlank()) {
            cabTo = trip.getDestinationCabDropoff();
        } else if (hotelBooking != null && hotelBooking.getFrom() != null && !hotelBooking.getFrom().isBlank()) {
            cabTo = hotelBooking.getFrom();
        } else {
            cabTo = destinationCity;
        }

        String cabProvider;
        if (oldCabBooking != null && oldCabBooking.getProvider() != null && !oldCabBooking.getProvider().isBlank()) {
            cabProvider = oldCabBooking.getProvider();
        } else {
            cabProvider = "Local cab at " + destinationCity;
        }

        BigDecimal cabCost;
        boolean cabIsAssumed;
        if (oldCabBooking != null && oldCabBooking.getCost() != null) {
            cabCost = oldCabBooking.getCost();
            cabIsAssumed = false;
        } else {
            cabCost = recoveryProperties.getReplacementCabCost();
            cabIsAssumed = true;
        }

        Booking plannedLeg = Booking.builder()
                .id(chosenLeg.getId())
                .type(chosenLeg.getType())
                .provider(chosenLeg.getProvider())
                .from(chosenLeg.getFrom())
                .to(chosenLeg.getTo())
                .startTime(chosenLeg.getStartTime())
                .endTime(chosenLeg.getEndTime())
                .cost(chosenLeg.getCost())
                .status(BookingStatus.PROPOSED)
                .dependsOn(chosenLeg.getDependsOn() != null ? new ArrayList<>(chosenLeg.getDependsOn()) : new ArrayList<>())
                .build();

        Booking replacementCab = null;
        if (createCab) {
            replacementCab = Booking.builder()
                    .id("BKG-CAB-" + chosenOption.getId())
                    .type(BookingType.CAB)
                    .provider(cabProvider)
                    .from(chosenLeg.getTo())
                    .to(cabTo)
                    .startTime(cabPickup)
                    .endTime(cabDropoff)
                    .cost(cabCost)
                    .status(BookingStatus.PROPOSED)
                    .dependsOn(List.of(chosenLeg.getId()))
                    .build();
        }

        // 2. Hotel re-timing (ONLY if trip has HOTEL booking)
        LocalDateTime originalHotelCheckIn = null;
        LocalDateTime newHotelCheckIn = null;
        LocalDateTime hotelCheckOut = null;
        if (hotelBooking != null) {
            originalHotelCheckIn = hotelBooking.getStartTime() != null ? hotelBooking.getStartTime() : arrival.plusHours(1);
            LocalDateTime handoverTime = createCab ? cabDropoff : arrival;
            newHotelCheckIn = handoverTime.isAfter(originalHotelCheckIn) ? handoverTime : originalHotelCheckIn;
            hotelCheckOut = hotelBooking.getEndTime() != null ? hotelBooking.getEndTime() : newHotelCheckIn.plusDays(1).withHour(11);
        }

        // 3. Event re-timing (ONLY if trip has EVENT booking)
        LocalDateTime eventStart = null;
        LocalDateTime eventEnd = null;
        BookingStatus newEventStatus = null;
        String eventChangeDesc = null;
        if (eventBooking != null) {
            eventStart = eventBooking.getStartTime() != null ? eventBooking.getStartTime() : arrival.plusHours(3);
            eventEnd = eventBooking.getEndTime() != null ? eventBooking.getEndTime() : eventStart.plusHours(4);
            LocalDateTime venueHandover = hotelBooking != null ? newHotelCheckIn : (createCab ? cabDropoff : arrival);
            LocalDateTime eventReadyTime = venueHandover.plusMinutes(30);
            boolean isEventSafe = !eventReadyTime.isAfter(eventStart);
            newEventStatus = isEventSafe ? BookingStatus.SAFE : BookingStatus.AT_RISK;
            eventChangeDesc = isEventSafe
                    ? String.format("Attendance preserved; arrival by %s, before %s event start.",
                            eventReadyTime.format(TIME_FORMAT), eventStart.format(TIME_FORMAT))
                    : String.format("At risk; arrival at %s, breaching %s event start by %d min.",
                            eventReadyTime.format(TIME_FORMAT), eventStart.format(TIME_FORMAT),
                            Duration.between(eventStart, eventReadyTime).toMinutes());
        }

        // 4. Before/after itinerary diff
        List<ItineraryDiffItem> diffItems = new ArrayList<>();

        if (disruptedBooking != null) {
            diffItems.add(ItineraryDiffItem.builder()
                    .bookingId(disruptedBooking.getId())
                    .type(disruptedBooking.getType())
                    .provider(disruptedBooking.getProvider())
                    .oldStart(disruptedBooking.getStartTime())
                    .oldEnd(disruptedBooking.getEndTime())
                    .oldStatus(disruptedBooking.getStatus() != null ? disruptedBooking.getStatus() : BookingStatus.CONFIRMED)
                    .newStart(disruptedBooking.getStartTime())
                    .newEnd(disruptedBooking.getEndTime())
                    .newStatus(BookingStatus.CANCELLED)
                    .changeDescription(String.format("Cancelled due to disruption; %s full refund requested.",
                            disruptedBooking.getType().name().toLowerCase()))
                    .build());
        }

        if (oldCabBooking != null && oldCabDependedOnDisruption) {
            diffItems.add(ItineraryDiffItem.builder()
                    .bookingId(oldCabBooking.getId())
                    .type(oldCabBooking.getType())
                    .provider(oldCabBooking.getProvider())
                    .oldStart(oldCabBooking.getStartTime())
                    .oldEnd(oldCabBooking.getEndTime())
                    .oldStatus(oldCabBooking.getStatus() != null ? oldCabBooking.getStatus() : BookingStatus.CONFIRMED)
                    .newStart(oldCabBooking.getStartTime())
                    .newEnd(oldCabBooking.getEndTime())
                    .newStatus(BookingStatus.CANCELLED)
                    .changeDescription(String.format("Cancelled because preceding %s was cancelled; replaced by new cab.",
                            disruptedBooking != null ? disruptedBooking.getType().name().toLowerCase() : "transit"))
                    .build());
        }

        if (hotelBooking != null) {
            diffItems.add(ItineraryDiffItem.builder()
                    .bookingId(hotelBooking.getId())
                    .type(hotelBooking.getType())
                    .provider(hotelBooking.getProvider())
                    .oldStart(originalHotelCheckIn)
                    .oldEnd(hotelCheckOut)
                    .oldStatus(hotelBooking.getStatus() != null ? hotelBooking.getStatus() : BookingStatus.CONFIRMED)
                    .newStart(newHotelCheckIn)
                    .newEnd(hotelCheckOut)
                    .newStatus(BookingStatus.REBOOKED)
                    .changeDescription(String.format("Check-in retimed from %s to %s to accommodate replacement transit.",
                            originalHotelCheckIn.format(TIME_FORMAT), newHotelCheckIn.format(TIME_FORMAT)))
                    .build());
        }

        if (eventBooking != null) {
            diffItems.add(ItineraryDiffItem.builder()
                    .bookingId(eventBooking.getId())
                    .type(eventBooking.getType())
                    .provider(eventBooking.getProvider())
                    .oldStart(eventStart)
                    .oldEnd(eventEnd)
                    .oldStatus(eventBooking.getStatus() != null ? eventBooking.getStatus() : BookingStatus.CONFIRMED)
                    .newStart(eventStart)
                    .newEnd(eventEnd)
                    .newStatus(newEventStatus)
                    .changeDescription(eventChangeDesc)
                    .build());
        }

        // New leg booking
        diffItems.add(ItineraryDiffItem.builder()
                .bookingId(plannedLeg.getId())
                .type(plannedLeg.getType())
                .provider(plannedLeg.getProvider())
                .oldStart(null)
                .oldEnd(null)
                .oldStatus(null)
                .newStart(plannedLeg.getStartTime())
                .newEnd(plannedLeg.getEndTime())
                .newStatus(BookingStatus.PROPOSED)
                .changeDescription(String.format("New replacement %s journey booked via %s.",
                        plannedLeg.getType(), plannedLeg.getProvider()))
                .build());

        // Replacement cab booking (ONLY if created)
        if (createCab && replacementCab != null) {
            diffItems.add(ItineraryDiffItem.builder()
                    .bookingId(replacementCab.getId())
                    .type(replacementCab.getType())
                    .provider(replacementCab.getProvider())
                    .oldStart(null)
                    .oldEnd(null)
                    .oldStatus(null)
                    .newStart(cabPickup)
                    .newEnd(cabDropoff)
                    .newStatus(BookingStatus.PROPOSED)
                    .changeDescription(String.format("Replacement cab booked for pickup at %s at %s.",
                            cabPickup.format(TIME_FORMAT), plannedLeg.getTo()))
                    .build());
        }

        // 5. Net cost breakdown line by line
        List<CostBreakdownLine> costLines = new ArrayList<>();

        // New leg
        costLines.add(CostBreakdownLine.builder()
                .label("New " + plannedLeg.getType() + " Ticket (" + plannedLeg.getProvider() + ")")
                .amount(plannedLeg.getCost())
                .isDeduction(false)
                .assumptionNote("Confirmed ticket fare for replacement journey")
                .build());

        // Replacement cab (ONLY if created)
        if (createCab && replacementCab != null) {
            String cabAssumption = cabIsAssumed
                    ? String.format("Cab Rs %s (assumed, %s to %s)",
                            cabCost.setScale(0, RoundingMode.HALF_UP).toPlainString(), plannedLeg.getTo(), cabTo)
                    : "Confirmed cab reservation";
            costLines.add(CostBreakdownLine.builder()
                    .label("Replacement Cab (" + replacementCab.getProvider() + ")")
                    .amount(cabCost)
                    .isDeduction(false)
                    .assumptionNote(cabAssumption)
                    .build());
        }

        // Hotel (ONLY if hotel exists)
        if (hotelBooking != null) {
            costLines.add(CostBreakdownLine.builder()
                    .label("Hotel Reservation (" + hotelBooking.getProvider() + ")")
                    .amount(hotelBooking.getCost())
                    .isDeduction(false)
                    .assumptionNote("Existing confirmed booking (unchanged)")
                    .build());
        }

        // Event (ONLY if event exists)
        if (eventBooking != null) {
            costLines.add(CostBreakdownLine.builder()
                    .label("Reservation (" + eventBooking.getProvider() + ")")
                    .amount(eventBooking.getCost())
                    .isDeduction(false)
                    .assumptionNote("Existing confirmed booking (unchanged)")
                    .build());
        }

        // Refunds (ONLY for cancelled bookings that exist)
        if (disruptedBooking != null && disruptedBooking.getCost() != null && disruptedBooking.getCost().compareTo(BigDecimal.ZERO) > 0) {
            costLines.add(CostBreakdownLine.builder()
                    .label(String.format("Refund for Cancelled %s (%s)",
                            toTitleCase(disruptedBooking.getType().name()), disruptedBooking.getProvider()))
                    .amount(disruptedBooking.getCost().negate())
                    .isDeduction(true)
                    .assumptionNote(String.format("Refund Rs %s (assumed full refund, %s-cancelled)",
                            disruptedBooking.getCost().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                            disruptedBooking.getType().name().toLowerCase()))
                    .build());
        }

        if (oldCabBooking != null && oldCabDependedOnDisruption && oldCabBooking.getCost() != null && oldCabBooking.getCost().compareTo(BigDecimal.ZERO) > 0) {
            costLines.add(CostBreakdownLine.builder()
                    .label("Refund for Cancelled Cab (" + oldCabBooking.getProvider() + ")")
                    .amount(oldCabBooking.getCost().negate())
                    .isDeduction(true)
                    .assumptionNote(String.format("Refund Rs %s (assumed full refund, prepaid taxi cancellation)",
                            oldCabBooking.getCost().setScale(0, RoundingMode.HALF_UP).toPlainString()))
                    .build());
        }

        BigDecimal netTripCost = BigDecimal.ZERO;
        for (CostBreakdownLine line : costLines) {
            netTripCost = netTripCost.add(line.getAmount());
        }

        BigDecimal maxBudget = trip.getConstraints() != null && trip.getConstraints().getMaxBudget() != null
                ? trip.getConstraints().getMaxBudget()
                : new BigDecimal("15000.00");
        BigDecimal remainingBudget = maxBudget.subtract(netTripCost);

        CostBreakdown costBreakdown = CostBreakdown.builder()
                .lines(costLines)
                .netTripCost(netTripCost)
                .maxBudget(maxBudget)
                .remainingBudget(remainingBudget)
                .build();

        // 6. Action checklist (order: 1. book leg, 2. book cab, 3. message hotel, 4. cancel old cab, 5. refund, 6. confirm arrival)
        List<ActionChecklistItem> checklist = new ArrayList<>();

        // 1. Book replacement leg
        checklist.add(ActionChecklistItem.builder()
                .id("BOOK_NEW_LEG")
                .text(String.format("Book the replacement %s leg (%s) departing at %s",
                        plannedLeg.getType(), plannedLeg.getProvider(), plannedLeg.getStartTime().format(TIME_FORMAT)))
                .done(false)
                .build());

        // 2. Book replacement cab (if any)
        if (createCab && replacementCab != null) {
            checklist.add(ActionChecklistItem.builder()
                    .id("BOOK_REPLACEMENT_CAB")
                    .text(String.format("Book the replacement cab at the computed time (%s) from %s",
                            cabPickup.format(TIME_FORMAT), plannedLeg.getTo()))
                    .done(false)
                    .build());
        }

        // 3. Message the hotel (if any)
        if (hotelBooking != null) {
            checklist.add(ActionChecklistItem.builder()
                    .id("MESSAGE_HOTEL")
                    .text(String.format("Message the hotel (%s) about late check-in at %s",
                            hotelBooking.getProvider(), newHotelCheckIn.format(TIME_FORMAT)))
                    .done(false)
                    .build());
        }

        // 4. Cancel old bookings (only those actually cancelled)
        if (oldCabBooking != null && oldCabDependedOnDisruption) {
            checklist.add(ActionChecklistItem.builder()
                    .id("CANCEL_OLD_CAB")
                    .text(String.format("Cancel the old cab booking %s (%s)", oldCabBooking.getId(), oldCabBooking.getProvider()))
                    .done(false)
                    .build());
        }

        // 5. Request refunds
        if (disruptedBooking != null) {
            String refundId = disruptedBooking.getType() == BookingType.FLIGHT ? "REQUEST_FLIGHT_REFUND" : ("REQUEST_REFUND_" + disruptedBooking.getId());
            checklist.add(ActionChecklistItem.builder()
                    .id(refundId)
                    .text(String.format("Request a refund for %s (%s)", disruptedBooking.getId(), disruptedBooking.getProvider()))
                    .done(false)
                    .build());
        }

        // 6. Confirm arrival (only if event / hard commitments exist)
        if (eventBooking != null || hasCommitments) {
            String contactDesc = eventBooking != null ? eventBooking.getProvider()
                    : trip.getHardCommitments().get(0).getName();
            checklist.add(ActionChecklistItem.builder()
                    .id("CONFIRM_FAMILY")
                    .text(String.format("Confirm the final arrival with the family for %s", contactDesc))
                    .done(false)
                    .build());
        }

        // 7. Ready-to-send hotel message (null if no hotel) and trade-off explanation
        String hotelMessage = null;
        if (hotelBooking != null) {
            hotelMessage = llmService.draftHotelMessage(trip, hotelBooking, newHotelCheckIn, chosenOption);
        }
        String tradeOffExplanation = llmService.generateTradeOffExplanation(trip, chosenOption, trip.getOptions());

        return RecoveryPlan.builder()
                .id(IdGenerator.generate("PLAN"))
                .tripId(trip.getId())
                .chosenOptionId(chosenOption.getId())
                .chosenOptionLabel(chosenOption.getLabel())
                .newLeg(plannedLeg)
                .replacementCab(replacementCab)
                .itineraryDiff(diffItems)
                .costBreakdown(costBreakdown)
                .checklist(checklist)
                .hotelMessage(hotelMessage)
                .tradeOffExplanation(tradeOffExplanation)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
