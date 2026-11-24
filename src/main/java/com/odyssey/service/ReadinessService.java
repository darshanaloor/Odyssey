package com.odyssey.service;

import com.odyssey.config.ReadinessProperties;
import com.odyssey.dto.readiness.ReadinessBand;
import com.odyssey.dto.readiness.ReadinessFactorDto;
import com.odyssey.dto.readiness.ReadinessRecommendationDto;
import com.odyssey.dto.readiness.TripReadinessDto;
import com.odyssey.entity.ServiceDepartureEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.CityInfo;
import com.odyssey.model.Constraints;
import com.odyssey.model.HardCommitment;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingType;
import com.odyssey.repository.jpa.ServiceDepartureJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing the deterministic Trip Readiness Score (Phase 15).
 * Operates in a strictly read-only transaction with zero modifications to any table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadinessService {

    private final ReadinessProperties properties;
    private final InventoryService inventoryService;

    @Autowired(required = false)
    private ServiceDepartureJpaRepository departureRepo;

    /**
     * Evaluates trip data across 5 deterministic factors and returns the readiness score and recommendations.
     */
    @Transactional(readOnly = true)
    public TripReadinessDto calculateReadiness(Trip trip) {
        if (trip == null) {
            throw new IllegalArgumentException("Trip cannot be null");
        }

        List<ReadinessFactorDto> knownFactors = new ArrayList<>();
        List<ReadinessFactorDto> unknownFactors = new ArrayList<>();
        List<ReadinessRecommendationDto> recommendations = new ArrayList<>();
        Map<String, Object> dataQuality = new HashMap<>();

        List<Booking> bookings = trip.getBookings() != null ? trip.getBookings() : Collections.emptyList();
        dataQuality.put("bookingCount", bookings.size());

        // Factor 1: Connection buffers
        ReadinessFactorDto f1 = evaluateConnectionBuffers(trip, bookings, recommendations);
        categorizeFactor(f1, knownFactors, unknownFactors);

        // Factor 2: Hard-commitment slack
        ReadinessFactorDto f2 = evaluateHardCommitmentSlack(trip, bookings, recommendations);
        categorizeFactor(f2, knownFactors, unknownFactors);

        // Factor 3: Booking completeness
        ReadinessFactorDto f3 = evaluateBookingCompleteness(trip, bookings, recommendations);
        categorizeFactor(f3, knownFactors, unknownFactors);

        // Factor 4: Recovery alternatives (for least-buffered transport leg)
        ReadinessFactorDto f4 = evaluateRecoveryAlternatives(trip, bookings, recommendations);
        categorizeFactor(f4, knownFactors, unknownFactors);

        // Factor 5: Monitoring coverage
        ReadinessFactorDto f5 = evaluateMonitoringCoverage(trip, bookings, recommendations);
        categorizeFactor(f5, knownFactors, unknownFactors);

        dataQuality.put("knownFactorsCount", knownFactors.size());
        dataQuality.put("unknownFactorsCount", unknownFactors.size());

        // Score aggregation
        Double overallScore = null;
        ReadinessBand band = null;
        boolean reliable = knownFactors.size() >= 3;
        String statusMessage = null;

        if (reliable) {
            double totalEarned = 0.0;
            double totalPossible = 0.0;
            for (ReadinessFactorDto factor : knownFactors) {
                if (factor.getPointsEarned() != null && factor.getPointsPossible() != null && factor.getPointsPossible() > 0) {
                    totalEarned += factor.getPointsEarned();
                    totalPossible += factor.getPointsPossible();
                }
            }

            if (totalPossible > 0) {
                double rawScore = (totalEarned / totalPossible) * 100.0;
                overallScore = round(rawScore, 1);

                if (overallScore >= properties.getThresholds().getHigh()) {
                    band = ReadinessBand.HIGH;
                } else if (overallScore >= properties.getThresholds().getMedium()) {
                    band = ReadinessBand.MEDIUM;
                } else {
                    band = ReadinessBand.LOW;
                }
            }
        } else {
            statusMessage = "Not enough data for a reliable score";
        }

        List<ReadinessFactorDto> allFactors = new ArrayList<>(knownFactors);
        allFactors.addAll(unknownFactors);

        return TripReadinessDto.builder()
                .tripId(trip.getId())
                .score(overallScore)
                .band(band)
                .reliable(reliable)
                .statusMessage(statusMessage)
                .disclaimer("Readiness checks your trip data. It does not predict disruptions.")
                .factors(allFactors)
                .unknownFactors(unknownFactors)
                .recommendations(recommendations)
                .dataQuality(dataQuality)
                .build();
    }

    private void categorizeFactor(ReadinessFactorDto factor, List<ReadinessFactorDto> known, List<ReadinessFactorDto> unknown) {
        if ("KNOWN".equalsIgnoreCase(factor.getStatus())) {
            known.add(factor);
        } else {
            unknown.add(factor);
        }
    }

    // ==========================================
    // 1. Connection Buffers Factor
    // ==========================================
    private ReadinessFactorDto evaluateConnectionBuffers(Trip trip, List<Booking> bookings, List<ReadinessRecommendationDto> recs) {
        double maxPoints = properties.getWeights().getConnectionBuffers();
        Map<String, Booking> bookingMap = bookings.stream().collect(Collectors.toMap(Booking::getId, b -> b, (a, b) -> a));

        // Find all dependency pairs
        List<Map<String, Object>> linkData = new ArrayList<>();
        int weakestBufferDiff = Integer.MAX_VALUE;
        String weakestLinkDesc = null;
        Booking weakestPredecessor = null;
        Booking weakestDependent = null;
        int weakestActual = 0;
        int weakestRequired = 0;

        for (Booking dependent : bookings) {
            if (dependent.getDependsOn() != null) {
                for (String predId : dependent.getDependsOn()) {
                    Booking predecessor = bookingMap.get(predId);
                    if (predecessor != null && predecessor.getEndTime() != null && dependent.getStartTime() != null) {
                        LocalDateTime predEnd = predecessor.getType() == BookingType.HOTEL && predecessor.getStartTime() != null
                                ? predecessor.getStartTime() : predecessor.getEndTime();
                        long actualBuffer = Duration.between(predEnd, dependent.getStartTime()).toMinutes();
                        int reqBuffer = properties.getBuffers().getRequiredBuffer(
                                predecessor.getType() != null ? predecessor.getType().name() : "DEFAULT",
                                dependent.getType() != null ? dependent.getType().name() : "DEFAULT"
                        );

                        int diff = (int) actualBuffer - reqBuffer;
                        Map<String, Object> link = new HashMap<>();
                        link.put("predecessorId", predecessor.getId());
                        link.put("dependentId", dependent.getId());
                        link.put("actualBufferMinutes", actualBuffer);
                        link.put("requiredBufferMinutes", reqBuffer);
                        link.put("differenceMinutes", diff);
                        linkData.add(link);

                        if (diff < weakestBufferDiff) {
                            weakestBufferDiff = diff;
                            weakestActual = (int) actualBuffer;
                            weakestRequired = reqBuffer;
                            weakestPredecessor = predecessor;
                            weakestDependent = dependent;
                            weakestLinkDesc = String.format("%s (%s) to %s (%s): %d min buffer (min required: %d min)",
                                    predecessor.getType(), predecessor.getId(), dependent.getType(), dependent.getId(), actualBuffer, reqBuffer);
                        }
                    }
                }
            }
        }

        if (linkData.isEmpty()) {
            return ReadinessFactorDto.builder()
                    .id("connection-buffers")
                    .name("Connection Buffers")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("No connection dependencies found between bookings in this trip.")
                    .dataUsed(Map.of("linkCount", 0))
                    .build();
        }

        // Aggregate ratio
        double ratio;
        if ("AVERAGE".equalsIgnoreCase(properties.getBuffers().getAggregation())) {
            double sumRatio = 0.0;
            for (Map<String, Object> l : linkData) {
                long act = ((Number) l.get("actualBufferMinutes")).longValue();
                int req = ((Number) l.get("requiredBufferMinutes")).intValue();
                sumRatio += req > 0 ? Math.max(0.0, Math.min(1.0, (double) act / req)) : 1.0;
            }
            ratio = sumRatio / linkData.size();
        } else {
            // MINIMUM
            double minRatio = 1.0;
            for (Map<String, Object> l : linkData) {
                long act = ((Number) l.get("actualBufferMinutes")).longValue();
                int req = ((Number) l.get("requiredBufferMinutes")).intValue();
                double r = req > 0 ? Math.max(0.0, Math.min(1.0, (double) act / req)) : 1.0;
                if (r < minRatio) minRatio = r;
            }
            ratio = minRatio;
        }

        double earned = round(ratio * maxPoints, 1);
        Map<String, Object> dataUsed = new HashMap<>();
        dataUsed.put("linksEvaluated", linkData.size());
        dataUsed.put("weakestLink", weakestLinkDesc);
        dataUsed.put("weakestBufferMinutes", weakestActual);
        dataUsed.put("weakestRequiredBufferMinutes", weakestRequired);

        String explanation = String.format("Evaluated %d connection link(s). Weakest link: %s.",
                linkData.size(), weakestLinkDesc != null ? weakestLinkDesc : "None");

        if (weakestBufferDiff < 0 && weakestPredecessor != null && weakestDependent != null) {
            int deficit = -weakestBufferDiff;
            recs.add(ReadinessRecommendationDto.builder()
                    .factorId("connection-buffers")
                    .message(String.format("The %s arrives %d minutes before the %s pickup; the recommended minimum is %d. Consider adding %d or more minutes.",
                            weakestPredecessor.getType().name().toLowerCase(), weakestActual,
                            weakestDependent.getType().name().toLowerCase(), weakestRequired, deficit))
                    .build());
        }

        return ReadinessFactorDto.builder()
                .id("connection-buffers")
                .name("Connection Buffers")
                .pointsEarned(earned)
                .pointsPossible(maxPoints)
                .status("KNOWN")
                .explanation(explanation)
                .dataUsed(dataUsed)
                .build();
    }

    // ==========================================
    // 2. Hard-commitment Slack Factor
    // ==========================================
    private ReadinessFactorDto evaluateHardCommitmentSlack(Trip trip, List<Booking> bookings, List<ReadinessRecommendationDto> recs) {
        double maxPoints = properties.getWeights().getHardCommitmentSlack();
        Constraints constraints = trip.getConstraints();
        int minRequiredSlack = properties.getSlack().getMinSlackMinutes();

        LocalDateTime latestArrival = null;
        for (Booking b : bookings) {
            if (b.getEndTime() != null && (latestArrival == null || b.getEndTime().isAfter(latestArrival))) {
                latestArrival = b.getEndTime();
            }
        }

        List<Map<String, Object>> slackItems = new ArrayList<>();
        Long minSlackMinutes = null;
        String tightestCommitmentName = null;

        // Check mustArriveBy
        if (constraints != null && constraints.getMustArriveBy() != null && latestArrival != null) {
            long slack = Duration.between(latestArrival, constraints.getMustArriveBy()).toMinutes();
            Map<String, Object> item = new HashMap<>();
            item.put("type", "MUST_ARRIVE_BY");
            item.put("targetTime", constraints.getMustArriveBy().toString());
            item.put("plannedArrival", latestArrival.toString());
            item.put("slackMinutes", slack);
            slackItems.add(item);
            minSlackMinutes = slack;
            tightestCommitmentName = "must-arrive-by deadline";
        }

        // Check structuredCommitments
        if (constraints != null && constraints.getStructuredCommitments() != null) {
            for (HardCommitment hc : constraints.getStructuredCommitments()) {
                if (hc.getCommitmentTime() != null) {
                    LocalDateTime arrivalBeforeCommitment = null;
                    for (Booking b : bookings) {
                        if (b.getEndTime() != null && !b.getEndTime().isAfter(hc.getCommitmentTime())) {
                            if (arrivalBeforeCommitment == null || b.getEndTime().isAfter(arrivalBeforeCommitment)) {
                                arrivalBeforeCommitment = b.getEndTime();
                            }
                        }
                    }
                    if (arrivalBeforeCommitment == null) {
                        arrivalBeforeCommitment = latestArrival;
                    }
                    if (arrivalBeforeCommitment != null) {
                        long slack = Duration.between(arrivalBeforeCommitment, hc.getCommitmentTime()).toMinutes();
                        Map<String, Object> item = new HashMap<>();
                        item.put("type", "HARD_COMMITMENT");
                        item.put("name", hc.getName());
                        item.put("targetTime", hc.getCommitmentTime().toString());
                        item.put("slackMinutes", slack);
                        slackItems.add(item);

                        if (minSlackMinutes == null || slack < minSlackMinutes) {
                            minSlackMinutes = slack;
                            tightestCommitmentName = hc.getName() != null ? hc.getName() : "hard commitment";
                        }
                    }
                }
            }
        }

        if (slackItems.isEmpty() || minSlackMinutes == null) {
            return ReadinessFactorDto.builder()
                    .id("hard-commitment-slack")
                    .name("Hard-commitment Slack")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("No hard commitments or deadline constraints specified for this trip.")
                    .dataUsed(Map.of("commitmentsEvaluated", 0))
                    .build();
        }

        double ratio;
        if (minSlackMinutes >= minRequiredSlack) {
            ratio = 1.0;
        } else if (minSlackMinutes <= 0) {
            ratio = 0.0;
        } else {
            ratio = (double) minSlackMinutes / minRequiredSlack;
        }

        double earned = round(ratio * maxPoints, 1);
        Map<String, Object> dataUsed = new HashMap<>();
        dataUsed.put("commitmentsEvaluated", slackItems.size());
        dataUsed.put("tightestSlackMinutes", minSlackMinutes);
        dataUsed.put("tightestCommitment", tightestCommitmentName);
        dataUsed.put("requiredSlackMinutes", minRequiredSlack);

        String explanation = String.format("Minimum slack to %s is %d minutes (recommended buffer: %d minutes).",
                tightestCommitmentName, minSlackMinutes, minRequiredSlack);

        if (minSlackMinutes < minRequiredSlack) {
            long deficit = minRequiredSlack - minSlackMinutes;
            recs.add(ReadinessRecommendationDto.builder()
                    .factorId("hard-commitment-slack")
                    .message(String.format("Slack to %s is %d minutes; recommended minimum is %d. Consider adding %d or more minutes to protect this commitment.",
                            tightestCommitmentName, minSlackMinutes, minRequiredSlack, deficit))
                    .build());
        }

        return ReadinessFactorDto.builder()
                .id("hard-commitment-slack")
                .name("Hard-commitment Slack")
                .pointsEarned(earned)
                .pointsPossible(maxPoints)
                .status("KNOWN")
                .explanation(explanation)
                .dataUsed(dataUsed)
                .build();
    }

    // ==========================================
    // 3. Booking Completeness Factor
    // ==========================================
    private ReadinessFactorDto evaluateBookingCompleteness(Trip trip, List<Booking> bookings, List<ReadinessRecommendationDto> recs) {
        double maxPoints = properties.getWeights().getBookingCompleteness();

        if (bookings.isEmpty()) {
            return ReadinessFactorDto.builder()
                    .id("booking-completeness")
                    .name("Booking Completeness")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("No bookings present in this trip to evaluate completeness.")
                    .dataUsed(Map.of("bookingCount", 0))
                    .build();
        }

        int totalChecks = bookings.size() * 5;
        int passedChecks = 0;
        int missingRefs = 0;
        int missingStops = 0;
        int missingTimes = 0;
        int missingPassenger = 0;
        int missingContact = 0;

        for (Booking b : bookings) {
            // 1. Reference
            if (b.getBookingReference() != null && !b.getBookingReference().isBlank()) {
                passedChecks++;
            } else {
                missingRefs++;
            }

            // 2. Stops (from and to)
            if (b.getFrom() != null && !b.getFrom().isBlank() && b.getTo() != null && !b.getTo().isBlank()) {
                passedChecks++;
            } else {
                missingStops++;
            }

            // 3. Times (start and end)
            if (b.getStartTime() != null && b.getEndTime() != null) {
                passedChecks++;
            } else {
                missingTimes++;
            }

            // 4. Passenger/guest details
            if ((trip.getTravellerName() != null && !trip.getTravellerName().isBlank())
                    || (b.getUserId() != null && !b.getUserId().isBlank())) {
                passedChecks++;
            } else {
                missingPassenger++;
            }

            // 5. Contact info (hotel email, operator contact, or traveller contact)
            if ((b.getHotelContactEmail() != null && !b.getHotelContactEmail().isBlank())
                    || (trip.getTravellerPhone() != null && !trip.getTravellerPhone().isBlank())
                    || (trip.getTravellerEmail() != null && !trip.getTravellerEmail().isBlank())) {
                passedChecks++;
            } else {
                missingContact++;
            }
        }

        double ratio = (double) passedChecks / totalChecks;
        double earned = round(ratio * maxPoints, 1);

        Map<String, Object> dataUsed = new HashMap<>();
        dataUsed.put("totalBookings", bookings.size());
        dataUsed.put("passedChecks", passedChecks);
        dataUsed.put("totalChecks", totalChecks);
        dataUsed.put("missingReferenceCount", missingRefs);
        dataUsed.put("missingStopsCount", missingStops);
        dataUsed.put("missingTimesCount", missingTimes);
        dataUsed.put("missingContactCount", missingContact);

        String explanation = String.format("%d of %d completeness criteria verified across %d booking(s) (%.0f%% complete).",
                passedChecks, totalChecks, bookings.size(), ratio * 100.0);

        if (missingRefs > 0 || missingStops > 0 || missingContact > 0) {
            List<String> details = new ArrayList<>();
            if (missingRefs > 0) details.add(missingRefs + " booking(s) missing reference number");
            if (missingStops > 0) details.add(missingStops + " booking(s) missing origin or destination stops");
            if (missingContact > 0) details.add(missingContact + " booking(s) missing contact information");

            recs.add(ReadinessRecommendationDto.builder()
                    .factorId("booking-completeness")
                    .message("Trip data completeness: " + String.join(", ", details) + ". Add missing booking details to prevent check-in delays.")
                    .build());
        }

        return ReadinessFactorDto.builder()
                .id("booking-completeness")
                .name("Booking Completeness")
                .pointsEarned(earned)
                .pointsPossible(maxPoints)
                .status("KNOWN")
                .explanation(explanation)
                .dataUsed(dataUsed)
                .build();
    }

    // ==========================================
    // 4. Recovery Alternatives Factor (Read-Only)
    // ==========================================
    private ReadinessFactorDto evaluateRecoveryAlternatives(Trip trip, List<Booking> bookings, List<ReadinessRecommendationDto> recs) {
        double maxPoints = properties.getWeights().getRecoveryAlternatives();

        // 1. Find least-buffered transport leg
        List<Booking> transportLegs = bookings.stream()
                .filter(b -> b.getType() == BookingType.FLIGHT || b.getType() == BookingType.TRAIN || b.getType() == BookingType.BUS || b.getType() == BookingType.CAB)
                .collect(Collectors.toList());

        if (transportLegs.isEmpty()) {
            return ReadinessFactorDto.builder()
                    .id("recovery-alternatives")
                    .name("Recovery Alternatives")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("No transport bookings found to evaluate recovery alternatives.")
                    .dataUsed(Map.of("transportLegsCount", 0))
                    .build();
        }

        // Determine least-buffered transport leg
        Map<String, Booking> map = bookings.stream().collect(Collectors.toMap(Booking::getId, b -> b, (a, b) -> a));
        Booking leastBufferedLeg = null;
        long minBuffer = Long.MAX_VALUE;

        for (Booking leg : transportLegs) {
            long legBuffer = Long.MAX_VALUE;
            // Downstream connection buffer
            for (Booking dependent : bookings) {
                if (dependent.getDependsOn() != null && dependent.getDependsOn().contains(leg.getId())) {
                    if (leg.getEndTime() != null && dependent.getStartTime() != null) {
                        long buf = Duration.between(leg.getEndTime(), dependent.getStartTime()).toMinutes();
                        if (buf < legBuffer) legBuffer = buf;
                    }
                }
            }
            // If no dependent, buffer to mustArriveBy
            if (legBuffer == Long.MAX_VALUE && trip.getConstraints() != null && trip.getConstraints().getMustArriveBy() != null && leg.getEndTime() != null) {
                legBuffer = Duration.between(leg.getEndTime(), trip.getConstraints().getMustArriveBy()).toMinutes();
            }
            if (legBuffer == Long.MAX_VALUE) {
                legBuffer = 120; // default nominal buffer
            }

            if (legBuffer < minBuffer) {
                minBuffer = legBuffer;
                leastBufferedLeg = leg;
            }
        }

        if (leastBufferedLeg == null || leastBufferedLeg.getFrom() == null || leastBufferedLeg.getTo() == null) {
            return ReadinessFactorDto.builder()
                    .id("recovery-alternatives")
                    .name("Recovery Alternatives")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("Could not determine valid origin and destination for the least-buffered transport leg.")
                    .dataUsed(Map.of("reason", "Missing origin/destination"))
                    .build();
        }

        String origin = leastBufferedLeg.getFrom();
        String destination = leastBufferedLeg.getTo();
        LocalDateTime searchTime = leastBufferedLeg.getStartTime() != null ? leastBufferedLeg.getStartTime() : LocalDateTime.now();

        // 2. Perform READ-ONLY search without saving or modifying departures
        double weightedAlternativesCount = 0.0;
        int realCount = 0;
        int generatedCount = 0;
        int estimatedCount = 0;

        // Search in-memory inventory
        try {
            List<InventoryItem> invItems = inventoryService.search(origin, destination, searchTime);
            for (InventoryItem inv : invItems) {
                if (!inv.getId().equalsIgnoreCase(leastBufferedLeg.getId())) {
                    realCount++;
                    weightedAlternativesCount += properties.getAlternatives().getRealWeight();
                }
            }
        } catch (Exception e) {
            log.debug("Inventory search failed during readiness check: {}", e.getMessage());
        }

        // Search existing database departures (read-only query)
        if (departureRepo != null) {
            try {
                CityInfo c1 = InventoryService.findCity(origin);
                CityInfo c2 = InventoryService.findCity(destination);
                String c1Key = c1 != null ? c1.getKey() : origin;
                String c2Key = c2 != null ? c2.getKey() : destination;
                String c1Name = c1 != null ? c1.getName() : origin;
                String c2Name = c2 != null ? c2.getName() : destination;
                LocalDate startDate = searchTime.toLocalDate();
                LocalDate endDate = startDate.plusDays(1);

                List<ServiceDepartureEntity> departures = departureRepo.findDeparturesBetweenDates(
                        c1Key, c1Name, c2Key, c2Name, startDate, endDate);

                for (ServiceDepartureEntity dep : departures) {
                    if (dep.getService() != null && Boolean.TRUE.equals(dep.getService().getActive())) {
                        String schedType = dep.getService().getSource();
                        if ("GENERATED".equalsIgnoreCase(schedType)) {
                            generatedCount++;
                            weightedAlternativesCount += properties.getAlternatives().getGeneratedWeight();
                        } else if ("ESTIMATED".equalsIgnoreCase(schedType)) {
                            estimatedCount++;
                            weightedAlternativesCount += properties.getAlternatives().getEstimatedWeight();
                        } else {
                            realCount++;
                            weightedAlternativesCount += properties.getAlternatives().getRealWeight();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("DepartureRepo search failed during readiness check: {}", e.getMessage());
            }
        }

        int targetCount = properties.getAlternatives().getTargetCount();
        double ratio = targetCount > 0 ? Math.min(1.0, weightedAlternativesCount / targetCount) : 1.0;
        double earned = round(ratio * maxPoints, 1);

        Map<String, Object> dataUsed = new HashMap<>();
        dataUsed.put("leastBufferedLegId", leastBufferedLeg.getId());
        dataUsed.put("origin", origin);
        dataUsed.put("destination", destination);
        dataUsed.put("bufferMinutes", minBuffer);
        dataUsed.put("realAlternativesCount", realCount);
        dataUsed.put("generatedAlternativesCount", generatedCount);
        dataUsed.put("estimatedAlternativesCount", estimatedCount);
        dataUsed.put("weightedAlternativesCount", round(weightedAlternativesCount, 2));
        dataUsed.put("targetCount", targetCount);

        String explanation = String.format("Found %.1f weighted alternative(s) (%d real, %d generated, %d estimated) for weakest leg %s to %s.",
                weightedAlternativesCount, realCount, generatedCount, estimatedCount, origin, destination);

        if (weightedAlternativesCount < targetCount) {
            recs.add(ReadinessRecommendationDto.builder()
                    .factorId("recovery-alternatives")
                    .message(String.format("The least-buffered leg (%s to %s) has only %.1f effective alternative(s) (recommended: %d). Review backup transport before departure.",
                            origin, destination, weightedAlternativesCount, targetCount))
                    .build());
        }

        return ReadinessFactorDto.builder()
                .id("recovery-alternatives")
                .name("Recovery Alternatives")
                .pointsEarned(earned)
                .pointsPossible(maxPoints)
                .status("KNOWN")
                .explanation(explanation)
                .dataUsed(dataUsed)
                .build();
    }

    // ==========================================
    // 5. Monitoring Coverage Factor
    // ==========================================
    private ReadinessFactorDto evaluateMonitoringCoverage(Trip trip, List<Booking> bookings, List<ReadinessRecommendationDto> recs) {
        double maxPoints = properties.getWeights().getMonitoringCoverage();

        List<Booking> transportLegs = bookings.stream()
                .filter(b -> b.getType() == BookingType.FLIGHT || b.getType() == BookingType.TRAIN || b.getType() == BookingType.BUS || b.getType() == BookingType.CAB)
                .collect(Collectors.toList());

        if (transportLegs.isEmpty()) {
            return ReadinessFactorDto.builder()
                    .id("monitoring-coverage")
                    .name("Monitoring Coverage")
                    .pointsEarned(null)
                    .pointsPossible(maxPoints)
                    .status("UNKNOWN")
                    .explanation("No transport bookings found for status monitoring.")
                    .dataUsed(Map.of("transportLegsCount", 0))
                    .build();
        }

        int liveCount = 0;
        int simulatedCount = 0;
        int unmonitoredCount = 0;
        double sumWeights = 0.0;
        double simWeight = properties.getMonitoring().getSimulatedWeight();

        for (Booking b : transportLegs) {
            String source = b.getLastStatusSource();
            if ("LIVE".equalsIgnoreCase(source)) {
                liveCount++;
                sumWeights += 1.0;
            } else if ("SIMULATED".equalsIgnoreCase(source)) {
                simulatedCount++;
                sumWeights += simWeight;
            } else {
                unmonitoredCount++;
            }
        }

        double ratio = sumWeights / transportLegs.size();
        double earned = round(ratio * maxPoints, 1);

        Map<String, Object> dataUsed = new HashMap<>();
        dataUsed.put("totalTransportBookings", transportLegs.size());
        dataUsed.put("liveCount", liveCount);
        dataUsed.put("simulatedCount", simulatedCount);
        dataUsed.put("unmonitoredCount", unmonitoredCount);

        String explanation = String.format("%d of %d transport booking(s) tracked (%d LIVE, %d SIMULATED, %d unmonitored).",
                liveCount + simulatedCount, transportLegs.size(), liveCount, simulatedCount, unmonitoredCount);

        if (unmonitoredCount > 0) {
            recs.add(ReadinessRecommendationDto.builder()
                    .factorId("monitoring-coverage")
                    .message(String.format("%d transport booking(s) lack active status monitoring. Check carrier timetables manually before travel.",
                            unmonitoredCount))
                    .build());
        }

        return ReadinessFactorDto.builder()
                .id("monitoring-coverage")
                .name("Monitoring Coverage")
                .pointsEarned(earned)
                .pointsPossible(maxPoints)
                .status("KNOWN")
                .explanation(explanation)
                .dataUsed(dataUsed)
                .build();
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
