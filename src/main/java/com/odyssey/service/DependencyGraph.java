package com.odyssey.service;

import com.odyssey.model.*;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class DependencyGraph {

    private String formatDisplayTime(LocalDateTime time) {
        if (time == null) return "";
        if (time.getMinute() == 0) {
            return time.format(DateTimeFormatter.ofPattern("h a", Locale.ENGLISH));
        }
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
    }

    /**
     * Builds a downstream adjacency map: predecessorId -> List of dependent bookingIds.
     */
    public Map<String, List<String>> buildDownstreamAdjacency(List<Booking> bookings) {
        Map<String, List<String>> downstreamAdj = new LinkedHashMap<>();
        for (Booking b : bookings) {
            downstreamAdj.putIfAbsent(b.getId(), new ArrayList<>());
            if (b.getDependsOn() != null) {
                for (String predId : b.getDependsOn()) {
                    downstreamAdj.computeIfAbsent(predId, k -> new ArrayList<>()).add(b.getId());
                }
            }
        }
        return downstreamAdj;
    }

    /**
     * Checks if the directed booking dependencies contain any cycles.
     */
    public boolean hasCycle(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return false;
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (Booking b : bookings) {
            adj.putIfAbsent(b.getId(), new ArrayList<>());
            if (b.getDependsOn() != null) {
                for (String dep : b.getDependsOn()) {
                    if (dep != null && !dep.isBlank()) {
                        adj.get(b.getId()).add(dep);
                    }
                }
            }
        }

        // 0 = unvisited, 1 = visiting (in recursion stack), 2 = visited
        Map<String, Integer> state = new HashMap<>();
        for (String id : adj.keySet()) {
            if (state.getOrDefault(id, 0) == 0) {
                if (dfsDetectCycle(id, adj, state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsDetectCycle(String node, Map<String, List<String>> adj, Map<String, Integer> state) {
        state.put(node, 1);
        List<String> neighbors = adj.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            int neighborState = state.getOrDefault(neighbor, 0);
            if (neighborState == 1) {
                return true; // Cycle detected
            }
            if (neighborState == 0) {
                if (dfsDetectCycle(neighbor, adj, state)) {
                    return true;
                }
            }
        }
        state.put(node, 2);
        return false;
    }

    /**
     * Executes BFS from the disrupted root booking to analyze all downstream impacts.
     */
    public TripImpactReport analyze(Trip trip, Booking rootDisrupted, Disruption disruption) {
        List<Booking> allBookings = trip.getBookings();
        Constraints constraints = trip.getConstraints();
        Map<String, List<String>> downstreamAdj = buildDownstreamAdjacency(allBookings);

        Map<String, Booking> bookingMap = new LinkedHashMap<>();
        for (Booking b : allBookings) {
            bookingMap.put(b.getId(), b);
        }

        Map<String, LocalDateTime> projectedStartTimes = new HashMap<>();
        Map<String, LocalDateTime> projectedEndTimes = new HashMap<>();
        Map<String, BookingStatus> impactStatuses = new HashMap<>();
        Set<String> brokenChains = new HashSet<>();

        // 1. Evaluate root disrupted booking
        int delay = disruption.getDelayMinutes() != null ? disruption.getDelayMinutes() : 0;
        if (disruption.getType() == DisruptionType.CANCELLATION) {
            impactStatuses.put(rootDisrupted.getId(), BookingStatus.CANCELLED);
            projectedStartTimes.put(rootDisrupted.getId(), rootDisrupted.getStartTime());
            projectedEndTimes.put(rootDisrupted.getId(), null);
            brokenChains.add(rootDisrupted.getId());
        } else if (disruption.getType() == DisruptionType.FLIGHT_DELAY) {
            impactStatuses.put(rootDisrupted.getId(), BookingStatus.DELAYED);
            projectedStartTimes.put(rootDisrupted.getId(), rootDisrupted.getStartTime());
            LocalDateTime newEnd = rootDisrupted.getEndTime() != null ? rootDisrupted.getEndTime().plusMinutes(delay) : null;
            projectedEndTimes.put(rootDisrupted.getId(), newEnd);
        } else {
            impactStatuses.put(rootDisrupted.getId(), BookingStatus.AT_RISK);
            projectedStartTimes.put(rootDisrupted.getId(), rootDisrupted.getStartTime());
            projectedEndTimes.put(rootDisrupted.getId(), rootDisrupted.getEndTime());
        }

        // 2. BFS traversal through downstream dependents
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        List<BookingImpact> impacts = new ArrayList<>();

        for (String childId : downstreamAdj.getOrDefault(rootDisrupted.getId(), Collections.emptyList())) {
            queue.add(childId);
            visited.add(childId);
        }

        List<String> summaryTokens = new ArrayList<>();
        List<String> hardCommitmentsBreached = new ArrayList<>();

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            Booking current = bookingMap.get(currentId);
            if (current == null) continue;

            BookingStatus computedStatus;
            String computedReason;
            LocalDateTime computedStart = current.getStartTime();
            LocalDateTime computedEnd = current.getEndTime();

            // Determine if any predecessor in the chain was cancelled
            boolean hasCancelledPredecessor = current.getDependsOn() != null && current.getDependsOn().stream()
                    .anyMatch(brokenChains::contains);

            if (hasCancelledPredecessor) {
                brokenChains.add(currentId);
                if (current.getType() == BookingType.CAB) {
                    computedStatus = BookingStatus.AT_RISK;
                    String cabDesc = (current.getProvider() != null && current.getProvider().toLowerCase().contains("airport")) ? "airport cab" : "cab";
                    computedReason = String.format("At risk; preceding transit was cancelled, so %s %s (%s) pickup time needs confirmation.",
                            cabDesc, current.getId(), current.getProvider());
                    summaryTokens.add(String.format("%s %s is at risk", cabDesc, current.getId()));
                } else if (current.getType() == BookingType.HOTEL) {
                    computedStatus = BookingStatus.AT_RISK;
                    computedReason = String.format("At risk; hotel check-in %s (%s) cannot be reached as scheduled due to preceding transit cancellation.",
                            current.getId(), current.getProvider());
                    summaryTokens.add(String.format("hotel check-in %s is at risk", current.getId()));
                } else if (current.getType() == BookingType.EVENT) {
                    // For a cancellation with no replacement, keep AT_RISK
                    computedStatus = BookingStatus.AT_RISK;
                    String destCity = "destination";
                    if (rootDisrupted != null && rootDisrupted.getTo() != null && !rootDisrupted.getTo().isBlank()) {
                        com.odyssey.model.CityInfo ci = InventoryService.findCity(rootDisrupted.getTo());
                        destCity = ci != null ? ci.getName() : rootDisrupted.getTo();
                    } else if (current.getTo() != null && !current.getTo().isBlank()) {
                        com.odyssey.model.CityInfo ci = InventoryService.findCity(current.getTo());
                        destCity = ci != null ? ci.getName() : current.getTo();
                    }
                    LocalDateTime targetTime = current.getStartTime() != null ? current.getStartTime().minusMinutes(30) : null;
                    computedReason = String.format("At risk unless a replacement journey reaches %s by %s.",
                            destCity, targetTime != null ? targetTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "17:30");
                    summaryTokens.add(String.format("the %s event %s is at risk", formatDisplayTime(current.getStartTime()), current.getId()));
                    hardCommitmentsBreached.add(current.getProvider() + " (" + formatDisplayTime(current.getStartTime()) + ")");
                } else {
                    computedStatus = BookingStatus.AT_RISK;
                    computedReason = String.format("At risk; preceding booking was cancelled, so %s (%s) cannot proceed.",
                            current.getType(), current.getId());
                    summaryTokens.add(String.format("%s %s is at risk", current.getType(), current.getId()));
                }
                projectedStartTimes.put(currentId, computedStart);
                projectedEndTimes.put(currentId, computedEnd);
            } else {
                // Predecessor is delayed: compute propagation handover time
                LocalDateTime maxPredHandover = null;
                if (current.getDependsOn() != null) {
                    for (String predId : current.getDependsOn()) {
                        Booking pred = bookingMap.get(predId);
                        if (pred != null) {
                            LocalDateTime handover;
                            if (pred.getType() == BookingType.HOTEL) {
                                // Guest check-in arrival time
                                handover = projectedStartTimes.getOrDefault(predId, pred.getStartTime());
                            } else {
                                handover = projectedEndTimes.getOrDefault(predId, pred.getEndTime());
                            }
                            if (handover != null && (maxPredHandover == null || handover.isAfter(maxPredHandover))) {
                                maxPredHandover = handover;
                            }
                        }
                    }
                }
                if (maxPredHandover == null) {
                    maxPredHandover = current.getStartTime();
                }

                if (current.getType() == BookingType.EVENT) {
                    // Projected arrival at venue: check-in arrival + 60 min freshen-up + 30 min transit = +90 mins
                    LocalDateTime venueArrival = maxPredHandover.plusMinutes(90);

                    // Buffer threshold: (event startTime - 30 minute buffer)
                    LocalDateTime bufferThreshold = current.getStartTime().minusMinutes(30);

                    // Set status AT_RISK only if (projected arrival at the venue) > (event startTime - 30 minute buffer)
                    if (venueArrival.isAfter(bufferThreshold)) {
                        computedStatus = BookingStatus.AT_RISK;
                        String destCity = "destination";
                        if (rootDisrupted != null && rootDisrupted.getTo() != null && !rootDisrupted.getTo().isBlank()) {
                            com.odyssey.model.CityInfo ci = InventoryService.findCity(rootDisrupted.getTo());
                            destCity = ci != null ? ci.getName() : rootDisrupted.getTo();
                        } else if (current.getTo() != null && !current.getTo().isBlank()) {
                            com.odyssey.model.CityInfo ci = InventoryService.findCity(current.getTo());
                            destCity = ci != null ? ci.getName() : current.getTo();
                        }
                        LocalDateTime targetTime = current.getStartTime() != null ? current.getStartTime().minusMinutes(30) : null;
                        computedReason = String.format("At risk unless a replacement journey reaches %s by %s.",
                                destCity, targetTime != null ? targetTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "17:30");
                        summaryTokens.add(String.format("the %s event %s is at risk", formatDisplayTime(current.getStartTime()), current.getId()));
                        hardCommitmentsBreached.add(current.getProvider() + " at " + formatDisplayTime(current.getStartTime()));
                    } else {
                        computedStatus = BookingStatus.SAFE;
                        computedReason = String.format("Still reachable: projected arrival %s, event starts %s",
                                venueArrival.format(DateTimeFormatter.ofPattern("HH:mm")),
                                current.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                    }
                    projectedStartTimes.put(currentId, current.getStartTime());
                    projectedEndTimes.put(currentId, current.getEndTime());
                } else if (current.getType() == BookingType.CAB) {
                    LocalDateTime earliestCabPickup = maxPredHandover.plusMinutes(30);
                    if (earliestCabPickup.isAfter(current.getStartTime())) {
                        computedStatus = BookingStatus.AT_RISK;
                        long shiftMinutes = Duration.between(current.getStartTime(), earliestCabPickup).toMinutes();
                        computedStart = earliestCabPickup;
                        computedEnd = current.getEndTime().plusMinutes(shiftMinutes);
                        computedReason = String.format("At risk; pickup delayed from %s to %s (late by %d minutes).",
                                formatDisplayTime(current.getStartTime()), formatDisplayTime(computedStart), shiftMinutes);
                        summaryTokens.add(String.format("airport cab %s is at risk (late by %d minutes)", current.getId(), shiftMinutes));
                    } else {
                        computedStatus = BookingStatus.SAFE;
                        computedReason = String.format("On schedule; cab pickup at %s is intact.", formatDisplayTime(current.getStartTime()));
                    }
                    projectedStartTimes.put(currentId, computedStart);
                    projectedEndTimes.put(currentId, computedEnd);
                } else if (current.getType() == BookingType.HOTEL) {
                    LocalDateTime cabArrival = maxPredHandover;
                    if (cabArrival.isAfter(current.getStartTime())) {
                        computedStatus = BookingStatus.AT_RISK;
                        long lateMinutes = Duration.between(current.getStartTime(), cabArrival).toMinutes();
                        computedStart = cabArrival;
                        computedReason = String.format("At risk; check-in delayed from %s to %s (late by %d minutes).",
                                formatDisplayTime(current.getStartTime()), formatDisplayTime(cabArrival), lateMinutes);
                        summaryTokens.add(String.format("hotel check-in %s is at risk (late by %d minutes)", current.getId(), lateMinutes));
                    } else {
                        computedStatus = BookingStatus.SAFE;
                        computedReason = String.format("On schedule; hotel check-in available at %s.", formatDisplayTime(current.getStartTime()));
                    }
                    projectedStartTimes.put(currentId, computedStart);
                    projectedEndTimes.put(currentId, computedEnd);
                } else {
                    LocalDateTime earliestStart = maxPredHandover.plusMinutes(30);
                    if (earliestStart.isAfter(current.getStartTime())) {
                        computedStatus = BookingStatus.AT_RISK;
                        long shiftMinutes = Duration.between(current.getStartTime(), earliestStart).toMinutes();
                        computedStart = earliestStart;
                        computedEnd = current.getEndTime().plusMinutes(shiftMinutes);
                        computedReason = String.format("At risk; departure delayed from %s to %s (late by %d minutes).",
                                formatDisplayTime(current.getStartTime()), formatDisplayTime(computedStart), shiftMinutes);
                        summaryTokens.add(String.format("%s %s is at risk (late by %d minutes)", current.getType(), current.getId(), shiftMinutes));
                    } else {
                        computedStatus = BookingStatus.SAFE;
                        computedReason = String.format("On schedule; %s on track.", current.getType());
                    }
                    projectedStartTimes.put(currentId, computedStart);
                    projectedEndTimes.put(currentId, computedEnd);
                }
            }

            impactStatuses.put(currentId, computedStatus);

            impacts.add(BookingImpact.builder()
                    .bookingId(current.getId())
                    .bookingType(current.getType())
                    .provider(current.getProvider())
                    .from(current.getFrom())
                    .to(current.getTo())
                    .originalStartTime(current.getStartTime())
                    .originalEndTime(current.getEndTime())
                    .projectedStartTime(computedStart)
                    .projectedEndTime(computedEnd)
                    .status(computedStatus)
                    .reason(computedReason)
                    .dependsOn(new ArrayList<>(current.getDependsOn()))
                    .build());

            // Enqueue subsequent dependents in BFS order
            for (String nextChild : downstreamAdj.getOrDefault(currentId, Collections.emptyList())) {
                if (!visited.contains(nextChild)) {
                    queue.add(nextChild);
                    visited.add(nextChild);
                }
            }
        }

        // 3. Assemble dynamic human-readable summary
        String rootPrefix = disruption.getType() == DisruptionType.CANCELLATION ?
                String.format("%s cancelled", rootDisrupted.getType().toString().toLowerCase()) :
                String.format("%s delayed by %d mins", rootDisrupted.getType().toString().toLowerCase(), delay);

        String summary = rootPrefix;
        if (!summaryTokens.isEmpty()) {
            if (summaryTokens.size() == 1) {
                summary += ", so " + summaryTokens.get(0);
            } else {
                String allExceptLast = String.join(", ", summaryTokens.subList(0, summaryTokens.size() - 1));
                summary += ", so " + allExceptLast + ", and " + summaryTokens.get(summaryTokens.size() - 1);
            }
        }
        summary += ".";

        return TripImpactReport.builder()
                .tripId(trip.getId())
                .disruptionId(disruption.getId())
                .disruptionType(disruption.getType())
                .rootBookingId(rootDisrupted.getId())
                .delayMinutes(disruption.getDelayMinutes())
                .disruptionReason(disruption.getReason())
                .summary(summary)
                .hardCommitmentsBreached(hardCommitmentsBreached)
                .mustArriveByBreached("PENDING_RECOVERY")
                .impactedBookings(impacts)
                .analyzedAt(LocalDateTime.now())
                .build();
    }
}
