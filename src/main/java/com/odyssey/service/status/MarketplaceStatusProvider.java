package com.odyssey.service.status;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.entity.ServiceDepartureEntity;
import com.odyssey.entity.ServiceEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.StatusResult;
import com.odyssey.model.enums.StatusState;
import com.odyssey.repository.jpa.ServiceDepartureJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceStatusProvider implements StatusProvider {

    private final ServiceDepartureJpaRepository departureRepo;
    private final RecoveryProperties recoveryProperties;

    @Override
    public boolean supports(Booking booking) {
        return booking != null && (booking.getDepartureId() != null || "MARKETPLACE".equalsIgnoreCase(booking.getSource()));
    }

    @Override
    public StatusResult getStatus(Booking booking) {
        return getStatus(null, booking);
    }

    @Override
    public StatusResult getStatus(String tripId, Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        if (booking == null || booking.getDepartureId() == null) {
            return StatusResult.builder()
                    .bookingId(booking != null ? booking.getId() : null)
                    .state(StatusState.ON_TIME)
                    .delayMinutes(0)
                    .source("SIMULATED")
                    .checkedAt(now)
                    .build();
        }

        ServiceDepartureEntity dep = departureRepo.findById(booking.getDepartureId()).orElse(null);
        if (dep == null) {
            log.warn("Marketplace departure not found for ID: {}", booking.getDepartureId());
            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(StatusState.ON_TIME)
                    .delayMinutes(0)
                    .reportedStart(booking.getStartTime())
                    .reportedEnd(booking.getEndTime())
                    .source("SIMULATED")
                    .checkedAt(now)
                    .build();
        }

        String depStatus = dep.getStatus() != null ? dep.getStatus().trim().toUpperCase() : "SCHEDULED";
        int delay = dep.getDelayMinutes() != null ? dep.getDelayMinutes() : 0;
        int threshold = recoveryProperties != null ? recoveryProperties.getDelayThresholdMinutes() : 60;

        StatusState state;
        if ("CANCELLED".equals(depStatus)) {
            state = StatusState.CANCELLED;
        } else if ("DELAYED".equals(depStatus) || delay >= threshold) {
            state = StatusState.DELAYED;
        } else {
            state = StatusState.ON_TIME;
        }

        ServiceEntity s = dep.getService();
        LocalDate depDate = dep.getTravelDate();
        LocalDate arrDate = (s != null && Boolean.TRUE.equals(s.getArrivesNextDay())) ? depDate.plusDays(1) : depDate;
        LocalDateTime scheduledStart = s != null ? depDate.atTime(s.getDepartureTime()) : booking.getStartTime();
        LocalDateTime scheduledEnd = s != null ? arrDate.atTime(s.getArrivalTime()) : booking.getEndTime();

        LocalDateTime reportedStart = scheduledStart != null ? scheduledStart.plusMinutes(delay) : null;
        LocalDateTime reportedEnd = scheduledEnd != null ? scheduledEnd.plusMinutes(delay) : null;

        String statusSrc = "PROVIDER_REPORTED".equalsIgnoreCase(dep.getStatusSource()) ? "PROVIDER_REPORTED" : "SIMULATED";

        return StatusResult.builder()
                .bookingId(booking.getId())
                .state(state)
                .delayMinutes(delay)
                .reportedStart(reportedStart)
                .reportedEnd(reportedEnd)
                .source(statusSrc)
                .checkedAt(now)
                .build();
    }
}
