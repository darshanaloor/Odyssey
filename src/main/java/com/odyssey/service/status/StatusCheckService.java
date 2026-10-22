package com.odyssey.service.status;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.config.StatusProperties;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.service.DependencyGraph;
import com.odyssey.service.NotificationService;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusCheckService {

    private final TripRepository tripRepository;
    private final StatusProviderSelector statusProviderSelector;
    private final DependencyGraph dependencyGraph;
    private final NotificationService notificationService;
    private final RecoveryProperties recoveryProperties;
    private final StatusProperties statusProperties;

    @Autowired(required = false)
    private SimulatedStatusProvider simulatedStatusProvider;

    private final AtomicBoolean isChecking = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "#{${odyssey.status.check-interval-minutes:5} * 60000}")
    public void runScheduledStatusChecks() {
        if (!isChecking.compareAndSet(false, true)) {
            log.info("Scheduled status check job is already in progress. Skipping this cycle.");
            return;
        }

        try {
            log.debug("Starting scheduled status check cycle...");
            List<Trip> trips = tripRepository.findAll();
            LocalDateTime now = LocalDateTime.now();

            for (Trip trip : trips) {
                if ("RESOLVED".equalsIgnoreCase(trip.getStatus())) {
                    continue;
                }
                try {
                    checkTrip(trip, now, false);
                } catch (Exception e) {
                    log.error("Error checking status for trip {}: {}", trip.getId(), e.getMessage(), e);
                }
            }
        } finally {
            isChecking.set(false);
        }
    }

    @Transactional
    public StatusCheckReport checkTripNow(String tripId) {
        return checkTripNow(tripId, true);
    }

    @Transactional
    public StatusCheckReport checkTripNow(String tripId, boolean dispatchAlert) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        LocalDateTime now = LocalDateTime.now();
        return checkTrip(trip, now, dispatchAlert);
    }

    public List<StatusResult> getLastKnownStatus(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        return trip.getBookings().stream()
                .map(b -> {
                    StatusState state = StatusState.ON_TIME;
                    if (b.getLastStatusState() != null) {
                        try {
                            state = StatusState.valueOf(b.getLastStatusState());
                        } catch (Exception ignored) {
                        }
                    }
                    return StatusResult.builder()
                            .bookingId(b.getId())
                            .state(state)
                            .delayMinutes(b.getLastDelayMinutes() != null ? b.getLastDelayMinutes() : 0)
                            .checkedAt(b.getLastCheckedAt())
                            .source(b.getLastStatusSource() != null ? b.getLastStatusSource() : "SIMULATED")
                            .error(b.getStatusCheckError())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private StatusCheckReport checkTrip(Trip trip, LocalDateTime now) {
        return checkTrip(trip, now, true);
    }

    private StatusCheckReport checkTrip(Trip trip, LocalDateTime now, boolean dispatchAlert) {
        if ("RESOLVED".equalsIgnoreCase(trip.getStatus())) {
            return StatusCheckReport.builder()
                    .tripId(trip.getId())
                    .results(Collections.emptyList())
                    .note("Trip is RESOLVED, status checks skipped")
                    .build();
        }

        List<Booking> candidates = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.FLIGHT || b.getType() == BookingType.TRAIN || b.getType() == BookingType.BUS)
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.REBOOKED && b.getStatus() != BookingStatus.COMPLETED)
                .collect(Collectors.toList());

        LocalDateTime windowEnd = now.plusHours(statusProperties.getLookaheadHours());
        List<Booking> inWindow = candidates.stream()
                .filter(b -> (b.getStartTime() != null
                                && !b.getStartTime().isBefore(now.minusMinutes(5))
                                && !b.getStartTime().isAfter(windowEnd))
                        || (simulatedStatusProvider != null && simulatedStatusProvider.hasScriptedStatus(trip.getId(), b.getId())))
                .collect(Collectors.toList());

        if (inWindow.isEmpty()) {
            return StatusCheckReport.builder()
                    .tripId(trip.getId())
                    .results(Collections.emptyList())
                    .note("No bookings start within 24 hours, nothing to check")
                    .build();
        }

        List<StatusResult> results = new ArrayList<>();
        boolean tripModified = false;

        for (Booking booking : inWindow) {
            StatusResult result;
            try {
                StatusProvider provider = statusProviderSelector.getProvider(booking);
                result = provider.getStatus(trip.getId(), booking);
            } catch (Exception e) {
                log.error("Exception invoking provider for booking {}: {}", booking.getId(), e.getMessage());
                result = StatusResult.builder()
                        .bookingId(booking.getId())
                        .state(StatusState.UNKNOWN)
                        .source("SIMULATED")
                        .checkedAt(now)
                        .error("Provider failure: " + e.getMessage())
                        .build();
            }

            results.add(result);

            // Persist status tracking fields
            booking.setLastStatusState(result.getState() != null ? result.getState().name() : StatusState.UNKNOWN.name());
            booking.setLastCheckedAt(result.getCheckedAt() != null ? result.getCheckedAt() : now);
            booking.setLastStatusSource(result.getSource() != null ? result.getSource() : "SIMULATED");
            booking.setStatusCheckError(result.getError());
            booking.setLastDelayMinutes(result.getDelayMinutes() != null ? result.getDelayMinutes() : 0);
            tripModified = true;

            // Audit entry for STATUS_CHECKED
            trip.getAuditLog().add(AuditEntry.builder()
                    .id(IdGenerator.generate("AUD"))
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.SYSTEM)
                    .action("STATUS_CHECKED")
                    .details(String.format("Status check completed for booking %s (%s): %s, delay %dm via %s",
                            booking.getId(), booking.getProvider(), result.getState(),
                            result.getDelayMinutes() != null ? result.getDelayMinutes() : 0,
                            result.getSource()))
                    .build());

            // Handle Fail-Safe: UNKNOWN, exception or error -> never create disruption
            if (result.getState() == StatusState.UNKNOWN || result.getState() == null) {
                log.info("Booking {} returned UNKNOWN status ({}) - recording error only",
                        booking.getId(), result.getError());
                continue;
            }

            // ON_TIME -> no disruption
            if (result.getState() == StatusState.ON_TIME) {
                continue;
            }

            // DELAYED or CANCELLED
            int delayMinutes = result.getDelayMinutes() != null ? result.getDelayMinutes() : 0;
            int delayThreshold = recoveryProperties.getDelayThresholdMinutes();

            if (result.getState() == StatusState.DELAYED && delayMinutes < delayThreshold) {
                log.debug("Booking {} delay {}m is below threshold {}m - no disruption",
                        booking.getId(), delayMinutes, delayThreshold);
                continue;
            }

            // Determine disruption type & delay band
            DisruptionType disruptionType = result.getState() == StatusState.CANCELLED
                    ? DisruptionType.CANCELLATION
                    : DisruptionType.FLIGHT_DELAY;

            int delayBand = (delayMinutes / 30) * 30;
            String dedupKey = booking.getId() + "_" + disruptionType.name() + "_" +
                    (disruptionType == DisruptionType.CANCELLATION ? "CANCELLED" : delayBand);

            // Check if already processed (dedup key in database column)
            if (isDuplicateOrSmaller(booking, disruptionType, delayBand)) {
                log.info("Booking {} already has processed disruption with dedup key {} (current: {}) - skipping duplicate",
                        booking.getId(), booking.getLastDedupKey(), dedupKey);
                continue;
            }

            // If options already generated for previous disruption, clear them and set note
            if (trip.getOptions() != null && !trip.getOptions().isEmpty()) {
                log.info("Clearing stale recovery options for trip {} due to updated disruption on booking {}",
                        trip.getId(), booking.getId());
                trip.getOptions().clear();
                trip.setRecoveryNote("Status changed, please search again");
            }

            booking.setLastDedupKey(dedupKey);

            // Create Disruption
            Disruption disruption = Disruption.builder()
                    .id(IdGenerator.generate("DIS"))
                    .type(disruptionType)
                    .bookingId(booking.getId())
                    .delayMinutes(delayMinutes)
                    .reason("Detected automatically from " + result.getSource() + " status")
                    .disruptionTime(now)
                    .reportedAt(now)
                    .source(result.getSource() != null ? result.getSource() : "SIMULATED")
                    .actor("SYSTEM")
                    .build();

            trip.getActiveDisruptions().add(disruption);

            // Run BFS impact analysis
            TripImpactReport impactReport = dependencyGraph.analyze(trip, booking, disruption);

            // Update root booking status
            if (disruptionType == DisruptionType.CANCELLATION) {
                booking.setStatus(BookingStatus.CANCELLED);
            } else {
                booking.setStatus(BookingStatus.DELAYED);
                if (booking.getEndTime() != null && delayMinutes > 0) {
                    booking.setEndTime(booking.getEndTime().plusMinutes(delayMinutes));
                }
            }

            // Update downstream bookings according to BFS
            Map<String, BookingImpact> impactMap = impactReport.getImpactedBookings().stream()
                    .collect(Collectors.toMap(BookingImpact::getBookingId, bi -> bi));
            for (Booking b : trip.getBookings()) {
                if (impactMap.containsKey(b.getId())) {
                    b.setStatus(impactMap.get(b.getId()).getStatus());
                }
            }

            // Generate notification alert
            NotificationMessage notification = notificationService.generateAlert(trip, booking, disruption, impactReport);
            impactReport.setNotification(notification);
            trip.setLatestImpact(impactReport);
            trip.setStatus("DISRUPTED");

            if (dispatchAlert) {
                try {
                    notificationService.sendDisruptionAlert(trip, booking, disruption, impactReport);
                } catch (Exception e) {
                    log.error("Failed to dispatch auto-detected disruption notification: {}", e.getMessage());
                }
            }

            // Write required audit entries
            trip.getAuditLog().add(AuditEntry.builder()
                    .id(IdGenerator.generate("AUD"))
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.SYSTEM)
                    .action("DISRUPTION_AUTO_DETECTED")
                    .details(String.format("Auto-detected %s on booking %s (%s) from %s status. Delay: %d mins (band: %d mins).",
                            disruptionType, booking.getId(), booking.getProvider(), result.getSource(),
                            delayMinutes, delayBand))
                    .build());

            trip.getAuditLog().add(AuditEntry.builder()
                    .id(IdGenerator.generate("AUD"))
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.AGENT)
                    .action("IMPACT_ANALYSIS_COMPLETED")
                    .details("BFS traversal analyzed " + impactReport.getImpactedBookings().size() +
                            " downstream bookings. Summary: " + impactReport.getSummary())
                    .build());

            if (dispatchAlert) {
                String previewMsg = notification.getMessage();
                if (previewMsg.length() > 80) {
                    previewMsg = previewMsg.substring(0, 80) + "...";
                }
                String maskedPhone = com.odyssey.util.NotificationMasker.maskPhone(notification.getRecipient());
                trip.getAuditLog().add(AuditEntry.builder()
                        .id(IdGenerator.generate("AUD"))
                        .timestamp(LocalDateTime.now())
                        .actor(AuditActor.AGENT)
                        .action("NOTIFICATION_DISPATCHED")
                        .details("Alert dispatched via " + notification.getChannel() +
                                " to " + maskedPhone + ": " + previewMsg)
                        .build());
            }
        }

        if (tripModified) {
            tripRepository.save(trip);
        }

        return StatusCheckReport.builder()
                .tripId(trip.getId())
                .results(results)
                .note(trip.getRecoveryNote())
                .build();
    }

    private boolean isDuplicateOrSmaller(Booking booking, DisruptionType type, int newBand) {
        String lastKey = booking.getLastDedupKey();
        if (lastKey == null || lastKey.isBlank()) {
            return false;
        }

        if (type == DisruptionType.CANCELLATION) {
            return lastKey.contains("_CANCELLATION_");
        }

        String prefix = booking.getId() + "_" + type.name() + "_";
        if (lastKey.startsWith(prefix)) {
            try {
                int lastBand = Integer.parseInt(lastKey.substring(prefix.length()));
                return newBand <= lastBand;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatusCheckReport {
        private String tripId;
        private List<StatusResult> results;
        private String note;
    }
}
