package com.odyssey.service;

import com.odyssey.config.ScoringProperties;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.Option;
import com.odyssey.model.RejectedOption;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.OptionLabel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ScoringProperties scoringProperties;
    private final com.odyssey.config.RecoveryProperties recoveryProperties;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public RecoveryResponse scoreAndRank(Trip trip, Booking disruptedBooking, List<InventoryItem> candidates) {
        LocalDateTime earliestDeparture = null;
        if (disruptedBooking != null && disruptedBooking.getStartTime() != null) {
            earliestDeparture = disruptedBooking.getStatus() == BookingStatus.CANCELLED
                    ? disruptedBooking.getStartTime().toLocalDate().atStartOfDay()
                    : disruptedBooking.getStartTime();
        }
        return scoreAndRank(trip, disruptedBooking, candidates, earliestDeparture);
    }

    public RecoveryResponse scoreAndRank(Trip trip, Booking disruptedBooking, List<InventoryItem> candidates,
                                         LocalDateTime earliestDeparture) {
        return scoreAndRank(trip, disruptedBooking, candidates, earliestDeparture, null, 0);
    }

    public RecoveryResponse scoreAndRank(Trip trip, Booking disruptedBooking, List<InventoryItem> candidates,
                                         LocalDateTime earliestDeparture, LocalDateTime disruptionTime, int stationBufferMinutes) {
        if (candidates == null || candidates.isEmpty()) {
            return RecoveryResponse.builder()
                    .tripId(trip.getId())
                    .disruptedBookingId(disruptedBooking != null ? disruptedBooking.getId() : null)
                    .noPerfectOption(true)
                    .explanation("No candidate travel inventory found for recovery route.")
                    .options(Collections.emptyList())
                    .rejectedOptions(Collections.emptyList())
                    .build();
        }

        // Calculate financial baseline for net trip cost
        BigDecimal cancelledRefunds = BigDecimal.ZERO;
        BigDecimal nonCancelledCost = BigDecimal.ZERO;

        for (Booking b : trip.getBookings()) {
            boolean isCancelled = b.getStatus() == BookingStatus.CANCELLED
                    || (disruptedBooking != null && b.getId().equalsIgnoreCase(disruptedBooking.getId()));
            if (isCancelled) {
                if (b.getCost() != null) {
                    cancelledRefunds = cancelledRefunds.add(b.getCost());
                }
            } else {
                if (b.getCost() != null) {
                    nonCancelledCost = nonCancelledCost.add(b.getCost());
                }
            }
        }

        // Evaluate all candidates
        List<CandidateEvaluation> allEvaluations = new ArrayList<>();
        List<CandidateEvaluation> feasibleEvaluations = new ArrayList<>();

        for (InventoryItem item : candidates) {
            CandidateEvaluation eval = evaluateCandidate(trip, disruptedBooking, item, nonCancelledCost, cancelledRefunds,
                    earliestDeparture, disruptionTime, stationBufferMinutes);
            allEvaluations.add(eval);
            if (eval.isFeasible) {
                feasibleEvaluations.add(eval);
            }
        }

        // Sort rejected options by "closest to feasible" (distanceToFeasible ascending, then score descending)
        List<RejectedOption> rejectedOptions = allEvaluations.stream()
                .filter(e -> !e.isFeasible)
                .sorted(Comparator.comparingDouble((CandidateEvaluation e) -> e.distanceToFeasible)
                        .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                .map(e -> RejectedOption.builder()
                        .id(e.item.getId())
                        .mode(e.item.getMode())
                        .provider(e.item.getProvider())
                        .cost(e.item.getCost())
                        .departure(e.item.getDeparture())
                        .arrival(e.item.getArrival())
                        .reason(e.hardRejectionReason)
                        .blockedBy(e.blockedBy)
                        .build())
                .collect(Collectors.toList());

        List<Option> topOptions = new ArrayList<>();
        String explanation;
        boolean noPerfectOption;

        if (feasibleEvaluations.isEmpty()) {
            noPerfectOption = true;
            explanation = "No feasible options meet all hard constraints (budget, deadline, daytime travel). Showing best available alternatives.";

            // Take the 3 least-bad options sorted by distanceToFeasible
            List<CandidateEvaluation> leastBad = allEvaluations.stream()
                    .sorted(Comparator.comparingDouble((CandidateEvaluation e) -> e.distanceToFeasible)
                            .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                    .limit(3)
                    .collect(Collectors.toList());

            OptionLabel[] defaultLabels = new OptionLabel[]{OptionLabel.FASTEST, OptionLabel.CHEAPEST, OptionLabel.SAFEST};
            for (int i = 0; i < leastBad.size(); i++) {
                CandidateEvaluation eval = leastBad.get(i);
                OptionLabel label = i < defaultLabels.length ? defaultLabels[i] : OptionLabel.FASTEST;
                Option opt = buildOption(eval, label);
                String broken = eval.blockedBy != null ? eval.blockedBy : "Constraint";
                opt.setBestAvailableNote("Best available, breaks: " + broken);
                opt.setExplanation("Best available, breaks: " + broken + ". " + opt.getExplanation());
                topOptions.add(opt);
            }
        } else if (feasibleEvaluations.size() == 1) {
            noPerfectOption = false;
            CandidateEvaluation only = feasibleEvaluations.get(0);
            topOptions.add(buildOption(only, OptionLabel.FASTEST));

            Option safestOption = buildOption(only, OptionLabel.SAFEST);
            safestOption.setBestAvailableNote("Same result as FASTEST (only 1 feasible option available meeting constraints)");
            safestOption.setExplanation("Same result as FASTEST: only 1 feasible option available meeting constraints. " + safestOption.getExplanation());
            topOptions.add(safestOption);

            explanation = "Only 1 feasible option available meeting all hard constraints.";
        } else if (feasibleEvaluations.size() == 2) {
            noPerfectOption = false;
            CandidateEvaluation fastest = feasibleEvaluations.stream()
                    .min(Comparator.comparing((CandidateEvaluation e) -> e.item.getArrival())
                            .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                    .orElseThrow();
            topOptions.add(buildOption(fastest, OptionLabel.FASTEST));

            CandidateEvaluation cheapest = feasibleEvaluations.stream()
                    .filter(e -> !e.item.getId().equals(fastest.item.getId()))
                    .min(Comparator.comparing((CandidateEvaluation e) -> e.netTripCost))
                    .orElseThrow();
            topOptions.add(buildOption(cheapest, OptionLabel.CHEAPEST));

            // Select SAFEST among the 2 feasible options
            // Rule: Never label ESTIMATED as SAFEST while a MARKETPLACE or INVENTORY option exists
            boolean fastestIsNonEstimated = !"ESTIMATED".equalsIgnoreCase(fastest.item.getSource());
            boolean cheapestIsNonEstimated = !"ESTIMATED".equalsIgnoreCase(cheapest.item.getSource());

            CandidateEvaluation safest;
            String safestReason;
            if (fastestIsNonEstimated && !cheapestIsNonEstimated) {
                safest = fastest;
                safestReason = "Same result as FASTEST (confirmed inventory takes safety precedence over estimated service)";
            } else if (!fastestIsNonEstimated && cheapestIsNonEstimated) {
                safest = cheapest;
                safestReason = "Same result as CHEAPEST (confirmed inventory takes safety precedence over estimated service)";
            } else {
                if (fastest.safetyScore >= cheapest.safetyScore) {
                    safest = fastest;
                    safestReason = "Same result as FASTEST (higher safety buffer and reliability score)";
                } else {
                    safest = cheapest;
                    safestReason = "Same result as CHEAPEST (higher safety buffer and reliability score)";
                }
            }

            Option safestOption = buildOption(safest, OptionLabel.SAFEST);
            safestOption.setBestAvailableNote(safestReason);
            safestOption.setExplanation(safestReason + ". " + safestOption.getExplanation());
            topOptions.add(safestOption);

            explanation = "Only 2 feasible options available meeting all hard constraints.";
        } else {
            noPerfectOption = false;
            // 1. FASTEST: earliest arrival time
            CandidateEvaluation fastest = feasibleEvaluations.stream()
                    .min(Comparator.comparing((CandidateEvaluation e) -> e.item.getArrival())
                            .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                    .orElseThrow();
            topOptions.add(buildOption(fastest, OptionLabel.FASTEST));

            // Remainder pool 1
            List<CandidateEvaluation> pool1 = feasibleEvaluations.stream()
                    .filter(e -> !e.item.getId().equals(fastest.item.getId()))
                    .collect(Collectors.toList());

            // 2. CHEAPEST: lowest net trip cost from remainder
            CandidateEvaluation cheapest = pool1.stream()
                    .min(Comparator.comparing((CandidateEvaluation e) -> e.netTripCost)
                            .thenComparing(Comparator.comparing((CandidateEvaluation e) -> e.item.getCost()))
                            .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                    .orElseThrow();
            topOptions.add(buildOption(cheapest, OptionLabel.CHEAPEST));

            // Remainder pool 2
            List<CandidateEvaluation> pool2 = pool1.stream()
                    .filter(e -> !e.item.getId().equals(cheapest.item.getId()))
                    .collect(Collectors.toList());

            // 3. SAFEST: maximises safetyScore = (reliabilityScore * w_rel) + (arrivalBufferMinutes * w_buf)
            // Estimated options are never labelled SAFEST while an INVENTORY option exists
            boolean inventoryInPool2 = pool2.stream().anyMatch(e -> !"ESTIMATED".equalsIgnoreCase(e.item.getSource()));
            CandidateEvaluation safest;
            if (inventoryInPool2) {
                safest = pool2.stream()
                        .filter(e -> !"ESTIMATED".equalsIgnoreCase(e.item.getSource()))
                        .max(Comparator.comparingDouble((CandidateEvaluation e) -> e.safetyScore)
                                .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score)))
                        .orElseThrow();
                topOptions.add(buildOption(safest, OptionLabel.SAFEST));
            } else {
                boolean anyInventoryOption = feasibleEvaluations.stream().anyMatch(e -> !"ESTIMATED".equalsIgnoreCase(e.item.getSource()));
                if (anyInventoryOption) {
                    if (!"ESTIMATED".equalsIgnoreCase(cheapest.item.getSource())) {
                        safest = cheapest;
                        CandidateEvaluation replacementCheapest = pool2.stream()
                                .min(Comparator.comparing((CandidateEvaluation e) -> e.netTripCost)
                                        .thenComparing(Comparator.comparing((CandidateEvaluation e) -> e.item.getCost()))
                                        .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                                .orElseThrow();
                        topOptions.clear();
                        topOptions.add(buildOption(fastest, OptionLabel.FASTEST));
                        topOptions.add(buildOption(replacementCheapest, OptionLabel.CHEAPEST));
                        topOptions.add(buildOption(safest, OptionLabel.SAFEST));
                    } else if (!"ESTIMATED".equalsIgnoreCase(fastest.item.getSource())) {
                        safest = fastest;
                        CandidateEvaluation replacementFastest = pool2.stream()
                                .min(Comparator.comparing((CandidateEvaluation e) -> e.item.getArrival())
                                        .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score).reversed()))
                                .orElseThrow();
                        topOptions.clear();
                        topOptions.add(buildOption(replacementFastest, OptionLabel.FASTEST));
                        topOptions.add(buildOption(cheapest, OptionLabel.CHEAPEST));
                        topOptions.add(buildOption(safest, OptionLabel.SAFEST));
                    } else {
                        safest = pool2.stream()
                                .max(Comparator.comparingDouble((CandidateEvaluation e) -> e.safetyScore)
                                        .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score)))
                                .orElseThrow();
                        topOptions.add(buildOption(safest, OptionLabel.SAFEST));
                    }
                } else {
                    safest = pool2.stream()
                            .max(Comparator.comparingDouble((CandidateEvaluation e) -> e.safetyScore)
                                    .thenComparing(Comparator.comparingDouble((CandidateEvaluation e) -> e.score)))
                            .orElseThrow();
                    topOptions.add(buildOption(safest, OptionLabel.SAFEST));
                }
            }

            explanation = "Found 3 distinct feasible recovery options ranked by speed, net cost, and route safety.";
        }

        return RecoveryResponse.builder()
                .tripId(trip.getId())
                .disruptedBookingId(disruptedBooking != null ? disruptedBooking.getId() : null)
                .noPerfectOption(noPerfectOption)
                .explanation(explanation)
                .options(topOptions)
                .rejectedOptions(rejectedOptions)
                .build();
    }

    public CandidateEvaluation evaluateCandidate(Trip trip, InventoryItem item,
                                                BigDecimal nonCancelledCost, BigDecimal cancelledRefunds) {
        return evaluateCandidate(trip, null, item, nonCancelledCost, cancelledRefunds, null);
    }

    public CandidateEvaluation evaluateCandidate(Trip trip, Booking disruptedBooking, InventoryItem item,
                                                BigDecimal nonCancelledCost, BigDecimal cancelledRefunds,
                                                LocalDateTime earliestDeparture) {
        return evaluateCandidate(trip, disruptedBooking, item, nonCancelledCost, cancelledRefunds, earliestDeparture, null, 0);
    }

    public CandidateEvaluation evaluateCandidate(Trip trip, Booking disruptedBooking, InventoryItem item,
                                                BigDecimal nonCancelledCost, BigDecimal cancelledRefunds,
                                                LocalDateTime earliestDeparture, LocalDateTime disruptionTime,
                                                int stationBufferMinutes) {
        Constraints constraints = trip.getConstraints();
        List<String> constraintsMet = new ArrayList<>();
        List<String> constraintsViolated = new ArrayList<>();

        BigDecimal maxBudget = constraints != null && constraints.getMaxBudget() != null
                ? constraints.getMaxBudget()
                : new BigDecimal("15000.00");

        LocalDateTime mustArriveBy = constraints != null && constraints.getMustArriveBy() != null
                ? constraints.getMustArriveBy()
                : LocalDateTime.MAX;

        // Estimate replacement cab: FLIGHT arrives at airport -> Rs 800 cab; TRAIN/BUS arrives in city -> Rs 400 cab
        // Estimate replacement cab: only if trip needs cab
        Booking oldCab = trip.getBookings() != null ? trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.CAB)
                .findFirst()
                .orElse(null) : null;
        boolean oldCabDepended = oldCab != null && (disruptedBooking == null || (oldCab.getDependsOn() != null && oldCab.getDependsOn().contains(disruptedBooking.getId())) || oldCab.getStatus() == BookingStatus.CANCELLED);
        boolean needsCab = oldCabDepended || Boolean.TRUE.equals(trip.getNeedsDestinationCab());

        BigDecimal replacementCabCost = BigDecimal.ZERO;
        if (needsCab) {
            if (oldCab != null && oldCab.getCost() != null) {
                replacementCabCost = oldCab.getCost();
            } else if (item.getMode() == BookingType.FLIGHT) {
                replacementCabCost = new BigDecimal("800.00");
            } else {
                replacementCabCost = recoveryProperties != null ? recoveryProperties.getReplacementCabCost() : new BigDecimal("400.00");
            }
        }

        // Net trip cost = new leg + replacement cab + non-cancelled bookings - cancelled refunds
        BigDecimal netTripCost = item.getCost().add(replacementCabCost).add(nonCancelledCost).subtract(cancelledRefunds);

        boolean hardViolation = false;
        String hardRejectionReason = null;
        String blockedBy = null;
        double distanceToFeasible = 0.0;
        int hardViolationsCount = 0;

        // 1. Departure time check
        if (disruptionTime != null && item.getDeparture() != null) {
            LocalDateTime earliestFeasible = disruptionTime.plusMinutes(stationBufferMinutes);
            if (item.getDeparture().isBefore(earliestFeasible)) {
                long minsEarly = Duration.between(item.getDeparture(), earliestFeasible).toMinutes();
                String reason = String.format("Departs %s, before the disruption was reported at %s (earliest feasible departure %s)",
                        item.getDeparture().format(TIME_FORMAT),
                        disruptionTime.format(TIME_FORMAT),
                        earliestFeasible.format(TIME_FORMAT));
                constraintsViolated.add(reason);
                hardViolation = true;
                hardViolationsCount++;
                distanceToFeasible += minsEarly;
                if (hardRejectionReason == null) {
                    hardRejectionReason = reason;
                    blockedBy = "Departure time";
                }
            } else {
                constraintsMet.add(String.format("Departs %s, after earliest feasible departure %s",
                        item.getDeparture().format(TIME_FORMAT), earliestFeasible.format(TIME_FORMAT)));
            }
        } else if (earliestDeparture != null && item.getDeparture() != null && item.getDeparture().isBefore(earliestDeparture)) {
            long minsEarly = Duration.between(item.getDeparture(), earliestDeparture).toMinutes();
            String reason = String.format("Departs at %s, before earliest allowed departure at %s",
                    item.getDeparture().format(DATE_TIME_FORMAT), earliestDeparture.format(DATE_TIME_FORMAT));
            constraintsViolated.add(reason);
            hardViolation = true;
            hardViolationsCount++;
            distanceToFeasible += minsEarly;
            if (hardRejectionReason == null) {
                hardRejectionReason = reason;
                blockedBy = "Departure time";
            }
        } else if (item.getDeparture() != null) {
            constraintsMet.add("Departs on or after earliest allowed departure");
        }

        // 2. Seats available check
        if (item.getSeatsAvailable() != null && item.getSeatsAvailable() <= 0) {
            constraintsViolated.add("No seats available (sold out)");
            hardViolation = true;
            hardViolationsCount++;
            distanceToFeasible += 2000.0;
            if (hardRejectionReason == null) {
                hardRejectionReason = "No seats available (sold out)";
                blockedBy = "Seats available";
            }
        } else if (item.getSeatsAvailable() != null) {
            constraintsMet.add(String.format("%d seats available", item.getSeatsAvailable()));
        }

        // 3. Overnight travel check (evaluated before arrival deadline so overnight services get the overnight rejection reason)
        java.time.LocalTime dayTravelStart = recoveryProperties != null && recoveryProperties.getDayTravelStart() != null
                ? recoveryProperties.getDayTravelStart() : java.time.LocalTime.of(6, 0);
        java.time.LocalTime dayTravelEnd = recoveryProperties != null && recoveryProperties.getDayTravelEnd() != null
                ? recoveryProperties.getDayTravelEnd() : java.time.LocalTime.of(21, 0);

        boolean avoidOvernight = constraints != null && Boolean.TRUE.equals(constraints.getAvoidOvernightTravel());
        boolean isOvernight = isOvernightJourney(item.getDeparture(), item.getArrival());

        LocalDateTime cabPickup = (needsCab && item.getArrival() != null)
                ? item.getArrival().plusMinutes(recoveryProperties != null ? recoveryProperties.getReplacementCabBufferMinutes() : 30)
                : null;
        boolean cabOutsideWindow = cabPickup != null && (cabPickup.toLocalTime().isAfter(dayTravelEnd) || cabPickup.toLocalTime().isBefore(dayTravelStart));

        if (avoidOvernight && (isOvernight || cabOutsideWindow)) {
            String reason;
            long minsOut = 0;
            if (item.getDeparture().toLocalDate().isBefore(item.getArrival().toLocalDate())) {
                minsOut = 720;
                reason = String.format("Overnight travel violates avoid-overnight constraint (departs %s, arrives %s)",
                        item.getDeparture().format(DATE_TIME_FORMAT), item.getArrival().format(DATE_TIME_FORMAT));
            } else if (item.getArrival().toLocalTime().isAfter(dayTravelEnd)) {
                minsOut = Duration.between(dayTravelEnd, item.getArrival().toLocalTime()).toMinutes();
                reason = String.format("Arrives at %s, after the day-travel window ends at %s; violates avoid-overnight constraint",
                        item.getArrival().format(DATE_TIME_FORMAT), dayTravelEnd.format(TIME_FORMAT));
            } else if (item.getDeparture().toLocalTime().isBefore(dayTravelStart)) {
                minsOut = Duration.between(item.getDeparture().toLocalTime(), dayTravelStart).toMinutes();
                reason = String.format("Departs at %s, before the day-travel window starts at %s; violates avoid-overnight constraint",
                        item.getDeparture().format(DATE_TIME_FORMAT), dayTravelStart.format(TIME_FORMAT));
            } else if (cabOutsideWindow) {
                if (cabPickup.toLocalTime().isAfter(dayTravelEnd)) {
                    minsOut = Duration.between(dayTravelEnd, cabPickup.toLocalTime()).toMinutes();
                } else {
                    minsOut = Duration.between(cabPickup.toLocalTime(), dayTravelStart).toMinutes();
                }
                reason = String.format("Replacement cab pickup at %s is outside the day-travel window (%s - %s); violates avoid-overnight constraint",
                        cabPickup.format(DATE_TIME_FORMAT), dayTravelStart.format(TIME_FORMAT), dayTravelEnd.format(TIME_FORMAT));
            } else {
                minsOut = 60;
                reason = String.format("Overnight travel violates avoid-overnight constraint (%s to %s)",
                        item.getDeparture().format(DATE_TIME_FORMAT), item.getArrival().format(DATE_TIME_FORMAT));
            }
            constraintsViolated.add(reason);
            hardViolation = true;
            hardViolationsCount++;
            distanceToFeasible += minsOut;
            if (hardRejectionReason == null) {
                hardRejectionReason = reason;
                blockedBy = "Day-travel window";
            }
        } else if (!isOvernight && !cabOutsideWindow) {
            constraintsMet.add(String.format("Day travel complies with avoid-overnight-travel window (%s - %s)",
                    dayTravelStart.format(TIME_FORMAT), dayTravelEnd.format(TIME_FORMAT)));
        }

        // 4. Arrival vs mustArriveBy check
        long arrivalBufferMinutes = 0;
        if (!item.getArrival().isAfter(mustArriveBy)) {
            arrivalBufferMinutes = Duration.between(item.getArrival(), mustArriveBy).toMinutes();
            constraintsMet.add(String.format("Arrives %s, %d min before the %s deadline",
                    item.getArrival().format(TIME_FORMAT), arrivalBufferMinutes, mustArriveBy.format(TIME_FORMAT)));
        } else {
            long minLate = Duration.between(mustArriveBy, item.getArrival()).toMinutes();
            String reason = String.format("Arrives %s, breaches the %s deadline by %d min",
                    item.getArrival().format(DATE_TIME_FORMAT), mustArriveBy.format(DATE_TIME_FORMAT), minLate);
            constraintsViolated.add(reason);
            hardViolation = true;
            hardViolationsCount++;
            distanceToFeasible += minLate;
            if (hardRejectionReason == null) {
                hardRejectionReason = String.format("Arrives %s, after the %s deadline",
                        item.getArrival().format(DATE_TIME_FORMAT), mustArriveBy.format(TIME_FORMAT));
                blockedBy = "Arrival deadline";
            }
        }

        // 5. Budget check on net trip cost
        if (netTripCost.compareTo(maxBudget) <= 0) {
            BigDecimal remaining = maxBudget.subtract(netTripCost);
            constraintsMet.add(String.format("Net trip cost Rs %s within Rs %s budget (Rs %s remaining)",
                    formatCurrency(netTripCost), formatCurrency(maxBudget), formatCurrency(remaining)));
        } else {
            BigDecimal over = netTripCost.subtract(maxBudget);
            String reason = String.format("Net trip cost Rs %s exceeds Rs %s budget by Rs %s",
                    formatCurrency(netTripCost), formatCurrency(maxBudget), formatCurrency(over));
            constraintsViolated.add(reason);
            hardViolation = true;
            hardViolationsCount++;
            double pctOver = (over.doubleValue() / maxBudget.doubleValue()) * 100.0;
            distanceToFeasible += pctOver * 10.0;
            if (hardRejectionReason == null) {
                hardRejectionReason = reason;
                blockedBy = "Budget";
            }
        }

        double totalFeasibilityDistance = (hardViolationsCount * 10000.0) + distanceToFeasible;

        // 6. Soft constraint: Preferred modes check
        if (constraints != null && constraints.getPreferredModes() != null && !constraints.getPreferredModes().isEmpty()) {
            if (constraints.getPreferredModes().contains(item.getMode())) {
                constraintsMet.add(String.format("Matches preferred mode: %s", item.getMode()));
            } else {
                String preferredList = constraints.getPreferredModes().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));
                constraintsViolated.add(String.format("Mode %s is not a preferred mode (%s)",
                        item.getMode(), preferredList));
            }
        }

        // 7. Direct route check
        if (item.getTransfers() != null && item.getTransfers() == 0) {
            constraintsMet.add("Direct route with 0 transfers");
        } else if (item.getTransfers() != null && item.getTransfers() > 0) {
            constraintsMet.add(String.format("%d transfer(s) required", item.getTransfers()));
        }

        // 8. Hard commitments check
        if (constraints != null && constraints.getHardCommitments() != null) {
            for (String commitment : constraints.getHardCommitments()) {
                if (item.getArrival().isAfter(mustArriveBy)) {
                    constraintsViolated.add(String.format("Breaches hard commitment: '%s'", commitment));
                } else {
                    constraintsMet.add(String.format("Preserves hard commitment: '%s'", commitment));
                }
            }
        }

        // Base score calculation
        double costScore = Math.max(0.0, (1.0 - (netTripCost.doubleValue() / maxBudget.doubleValue())) * 100.0);
        double timeScore;
        if (!item.getArrival().isAfter(mustArriveBy)) {
            timeScore = Math.min(100.0, 50.0 + (arrivalBufferMinutes / 360.0) * 50.0);
        } else {
            long minLate = Duration.between(mustArriveBy, item.getArrival()).toMinutes();
            timeScore = Math.max(0.0, 50.0 - (minLate / 120.0) * 50.0);
        }

        double relScore;
        if (item.getSource() != null && "ESTIMATED".equalsIgnoreCase(item.getSource())) {
            relScore = recoveryProperties.getSyntheticReliabilityScore();
        } else if (item.getReliabilityScore() != null) {
            relScore = item.getReliabilityScore().doubleValue();
        } else {
            relScore = recoveryProperties.getSyntheticReliabilityScore();
        }
        double transferScore = Math.max(0.0, 100.0 - ((item.getTransfers() != null ? item.getTransfers() : 0) * 35.0));

        var w = scoringProperties.getWeights();
        double baseScore = (w.getCost() * costScore)
                + (w.getTime() * timeScore)
                + (w.getReliability() * relScore)
                + (w.getTransfers() * transferScore);

        var p = scoringProperties.getPenalties();
        if (avoidOvernight && isOvernight) {
            baseScore -= p.getOvernightTravel();
        }
        if (hardViolation) {
            baseScore -= p.getConstraintViolation();
        }

        // Phase 17: Traveller Needs Preferences (applied to safetyScore and as tie-breaker in finalScore)
        double needsAdjustment = 0.0;
        if (trip != null && trip.hasAnySpecialNeed()) {
            var tn = scoringProperties.getTravellerNeeds();
            if (tn == null) {
                tn = new ScoringProperties.TravellerNeeds();
            }

            // 1. Fewer transfers preference
            int transfers = item.getTransfers() != null ? item.getTransfers() : 0;
            if (transfers == 0) {
                needsAdjustment += tn.getDirectRouteBonus();
                constraintsMet.add(String.format("Traveller needs preference: Direct route with 0 transfers (+%.1f pts)", tn.getDirectRouteBonus()));
            } else {
                double penalty = transfers * tn.getTransferPenalty();
                needsAdjustment -= penalty;
                constraintsViolated.add(String.format("Traveller needs preference: %d transfer(s) required (-%.1f pts)", transfers, penalty));
            }

            // 2. Daytime travel preference
            boolean isDaytime = !isOvernight && (item.getDeparture() != null && item.getArrival() != null
                    && !item.getDeparture().toLocalTime().isBefore(dayTravelStart)
                    && !item.getArrival().toLocalTime().isAfter(dayTravelEnd));
            if (isDaytime) {
                needsAdjustment += tn.getDaytimeBonus();
                constraintsMet.add(String.format("Traveller needs preference: Daytime travel departing at %s and arriving at %s (+%.1f pts)",
                        item.getDeparture().format(TIME_FORMAT), item.getArrival().format(TIME_FORMAT), tn.getDaytimeBonus()));
            } else {
                needsAdjustment -= tn.getDaytimeBonus();
                constraintsViolated.add(String.format("Traveller needs preference: Non-daytime schedule departing at %s or arriving at %s (-%.1f pts)",
                        item.getDeparture() != null ? item.getDeparture().format(TIME_FORMAT) : "N/A",
                        item.getArrival() != null ? item.getArrival().format(TIME_FORMAT) : "N/A",
                        tn.getDaytimeBonus()));
            }

            // 3. Shorter waiting preference before departure
            if (disruptionTime != null && item.getDeparture() != null && !item.getDeparture().isBefore(disruptionTime)) {
                long waitMinutes = Duration.between(disruptionTime, item.getDeparture()).toMinutes();
                if (waitMinutes <= 120) {
                    needsAdjustment += tn.getShortWaitBonus();
                    constraintsMet.add(String.format("Traveller needs preference: Shorter waiting time of %d min before departure (+%.1f pts)",
                            waitMinutes, tn.getShortWaitBonus()));
                }
            }

            // 4. Extended commitment buffer preference
            if (arrivalBufferMinutes >= 60) {
                needsAdjustment += tn.getExtraBufferBonus();
                constraintsMet.add(String.format("Traveller needs preference: Extended commitment buffer of %d min before deadline (+%.1f pts)",
                        arrivalBufferMinutes, tn.getExtraBufferBonus()));
            }
        }

        double finalScore = BigDecimal.valueOf(Math.max(0.0, Math.min(100.0, baseScore + (needsAdjustment * 0.001))))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        // Safety score for SAFEST candidate ranking:
        // safetyScore = (reliabilityScore * safetyReliability) + (arrivalBufferMinutes * safetyBuffer) + needsAdjustment
        double safetyScore = (relScore * w.getSafetyReliability()) + (arrivalBufferMinutes * w.getSafetyBuffer()) + needsAdjustment;

        return new CandidateEvaluation(item, !hardViolation, hardRejectionReason, blockedBy, totalFeasibilityDistance,
                finalScore, safetyScore, netTripCost, replacementCabCost, nonCancelledCost, cancelledRefunds, maxBudget,
                arrivalBufferMinutes, constraintsMet, constraintsViolated);
    }

    private Option buildOption(CandidateEvaluation eval, OptionLabel label) {
        InventoryItem item = eval.item;
        String source = item.getSource() != null ? item.getSource() : "INVENTORY";
        int reliability = item.getReliabilityScore() != null ? item.getReliabilityScore() : (int) Math.round(recoveryProperties.getSyntheticReliabilityScore());

        Booking leg = Booking.builder()
                .id("BKG-REC-" + item.getId())
                .type(item.getMode())
                .provider(item.getProvider())
                .from(item.getFrom())
                .to(item.getTo())
                .startTime(item.getDeparture())
                .endTime(item.getArrival())
                .cost(item.getCost())
                .status(BookingStatus.CONFIRMED)
                .source(source)
                .departureId(item.getServiceDepartureId())
                .operator(item.getOperator() != null ? item.getOperator() : item.getProvider())
                .vehicleNumber(item.getVehicleNumber())
                .seatsAvailable(item.getSeatsAvailable())
                .scheduleLabel(item.getScheduleLabel())
                .build();

        String breakdown = String.format("Net trip cost Rs %s (New leg Rs %s + Cab Rs %s + Non-cancelled Rs %s - Refunds Rs %s), within Rs %s budget.",
                formatCurrency(eval.netTripCost), formatCurrency(item.getCost()), formatCurrency(eval.replacementCabCost),
                formatCurrency(eval.nonCancelledCost), formatCurrency(eval.cancelledRefunds), formatCurrency(eval.maxBudget));

        String explanation = String.format("%s option via %s: arrives at %s with %d%% reliability and %d min deadline buffer. %s",
                label, item.getProvider(), item.getArrival().format(TIME_FORMAT),
                reliability, eval.arrivalBufferMinutes, breakdown);

        return Option.builder()
                .id("OPT-" + label.name() + "-" + item.getId())
                .label(label)
                .legs(List.of(leg))
                .totalCost(item.getCost())
                .arrivalTime(item.getArrival())
                .score(eval.score)
                .constraintsMet(eval.constraintsMet)
                .constraintsViolated(eval.constraintsViolated)
                .explanation(explanation)
                .source(source)
                .build();
    }

    private boolean isOvernightJourney(LocalDateTime departure, LocalDateTime arrival) {
        if (departure.toLocalDate().isBefore(arrival.toLocalDate())) {
            return true;
        }
        java.time.LocalTime dayTravelStart = recoveryProperties != null && recoveryProperties.getDayTravelStart() != null
                ? recoveryProperties.getDayTravelStart() : java.time.LocalTime.of(6, 0);
        java.time.LocalTime dayTravelEnd = recoveryProperties != null && recoveryProperties.getDayTravelEnd() != null
                ? recoveryProperties.getDayTravelEnd() : java.time.LocalTime.of(21, 0);

        java.time.LocalTime depTime = departure.toLocalTime();
        java.time.LocalTime arrTime = arrival.toLocalTime();

        return depTime.isBefore(dayTravelStart) || depTime.isAfter(dayTravelEnd)
                || arrTime.isBefore(dayTravelStart) || arrTime.isAfter(dayTravelEnd);
    }

    private String formatCurrency(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    public static class CandidateEvaluation {
        public final InventoryItem item;
        public final boolean isFeasible;
        public final String hardRejectionReason;
        public final String blockedBy;
        public final double distanceToFeasible;
        public final double score;
        public final double safetyScore;
        public final BigDecimal netTripCost;
        public final BigDecimal replacementCabCost;
        public final BigDecimal nonCancelledCost;
        public final BigDecimal cancelledRefunds;
        public final BigDecimal maxBudget;
        public final long arrivalBufferMinutes;
        public final List<String> constraintsMet;
        public final List<String> constraintsViolated;

        public CandidateEvaluation(InventoryItem item, boolean isFeasible, String hardRejectionReason,
                                   String blockedBy, double distanceToFeasible,
                                   double score, double safetyScore, BigDecimal netTripCost,
                                   BigDecimal replacementCabCost, BigDecimal nonCancelledCost,
                                   BigDecimal cancelledRefunds, BigDecimal maxBudget,
                                   long arrivalBufferMinutes, List<String> constraintsMet,
                                   List<String> constraintsViolated) {
            this.item = item;
            this.isFeasible = isFeasible;
            this.hardRejectionReason = hardRejectionReason;
            this.blockedBy = blockedBy;
            this.distanceToFeasible = distanceToFeasible;
            this.score = score;
            this.safetyScore = safetyScore;
            this.netTripCost = netTripCost;
            this.replacementCabCost = replacementCabCost;
            this.nonCancelledCost = nonCancelledCost;
            this.cancelledRefunds = cancelledRefunds;
            this.maxBudget = maxBudget;
            this.arrivalBufferMinutes = arrivalBufferMinutes;
            this.constraintsMet = constraintsMet;
            this.constraintsViolated = constraintsViolated;
        }

        public CandidateEvaluation(InventoryItem item, boolean isFeasible, String hardRejectionReason,
                                   double score, double safetyScore, BigDecimal netTripCost,
                                   BigDecimal replacementCabCost, BigDecimal nonCancelledCost,
                                   BigDecimal cancelledRefunds, BigDecimal maxBudget,
                                   long arrivalBufferMinutes, List<String> constraintsMet,
                                   List<String> constraintsViolated) {
            this(item, isFeasible, hardRejectionReason, null, 0.0, score, safetyScore, netTripCost,
                    replacementCabCost, nonCancelledCost, cancelledRefunds, maxBudget,
                    arrivalBufferMinutes, constraintsMet, constraintsViolated);
        }
    }
}
