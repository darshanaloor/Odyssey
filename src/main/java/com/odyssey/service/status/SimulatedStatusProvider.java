package com.odyssey.service.status;

import com.odyssey.model.Booking;
import com.odyssey.model.StatusResult;
import com.odyssey.model.enums.StatusState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SimulatedStatusProvider implements StatusProvider {

    private final ConcurrentMap<String, StatusResult> scriptedStates = new ConcurrentHashMap<>();

    public static String buildKey(String tripId, String bookingId) {
        if (tripId != null && !tripId.isBlank()) {
            return tripId + ":" + bookingId;
        }
        return bookingId;
    }

    @Override
    public boolean supports(Booking booking) {
        return true;
    }

    @Override
    public StatusResult getStatus(Booking booking) {
        return getStatus(null, booking);
    }

    @Override
    public StatusResult getStatus(String tripId, Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        String scopedKey = buildKey(tripId, booking.getId());
        StatusResult scripted = scriptedStates.get(scopedKey);
        if (scripted == null && tripId != null) {
            // Fallback for tests or callers without tripId scope
            scripted = scriptedStates.get(booking.getId());
        }

        if (scripted != null) {
            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(scripted.getState())
                    .delayMinutes(scripted.getDelayMinutes() != null ? scripted.getDelayMinutes() : 0)
                    .reportedStart(booking.getStartTime())
                    .reportedEnd(booking.getEndTime() != null && scripted.getDelayMinutes() != null && scripted.getDelayMinutes() > 0
                            ? booking.getEndTime().plusMinutes(scripted.getDelayMinutes())
                            : booking.getEndTime())
                    .source("SIMULATED")
                    .checkedAt(now)
                    .error(scripted.getError())
                    .build();
        }

        // Default for every booking is ON_TIME
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

    public void setScriptedStatus(String tripId, String bookingId, StatusState state, int delayMinutes) {
        String key = buildKey(tripId, bookingId);
        scriptedStates.put(key, StatusResult.builder()
                .bookingId(bookingId)
                .state(state)
                .delayMinutes(delayMinutes)
                .source("SIMULATED")
                .checkedAt(LocalDateTime.now())
                .build());
    }

    public void setScriptedStatus(String bookingId, StatusState state, int delayMinutes) {
        setScriptedStatus(null, bookingId, state, delayMinutes);
    }

    public boolean hasScriptedStatus(String tripId, String bookingId) {
        String scopedKey = buildKey(tripId, bookingId);
        return scriptedStates.containsKey(scopedKey) || (tripId != null && scriptedStates.containsKey(bookingId));
    }

    public boolean hasScriptedStatusForBooking(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return false;
        }
        if (scriptedStates.containsKey(bookingId)) {
            return true;
        }
        String suffix = ":" + bookingId;
        return scriptedStates.keySet().stream().anyMatch(k -> k.endsWith(suffix));
    }

    public void clearScriptedStatus(String tripId, String bookingId) {
        scriptedStates.remove(buildKey(tripId, bookingId));
    }

    public void clearScriptedStatus(String bookingId) {
        scriptedStates.remove(bookingId);
    }

    public void clearAll() {
        scriptedStates.clear();
    }
}
