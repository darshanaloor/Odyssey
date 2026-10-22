package com.odyssey.controller;

import com.odyssey.dto.request.SimulateStatusRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.entity.BookingEntity;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.StatusResult;
import com.odyssey.model.enums.StatusState;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.service.status.SimulatedStatusProvider;
import com.odyssey.service.status.StatusCheckService;
import com.odyssey.service.status.StatusProviderSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StatusController {

    private final StatusProviderSelector statusProviderSelector;
    private final StatusCheckService statusCheckService;
    private final SimulatedStatusProvider simulatedStatusProvider;
    private final BookingJpaRepository bookingJpaRepository;
    private final TripRepository tripRepository;
    private final com.odyssey.service.TripAccessService tripAccessService;

    @GetMapping("/api/status/mode")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStatusMode() {
        String mode = statusProviderSelector.getActiveMode();
        return ResponseEntity.ok(ApiResponse.ok("Current status mode", Map.of("mode", mode)));
    }

    @PostMapping("/api/trips/{id}/status/check")
    public ResponseEntity<ApiResponse<List<StatusResult>>> checkTripStatus(@PathVariable String id) {
        tripAccessService.requireOwner(id);

        // Check transit status without dispatching alert message to email and phone
        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(id, false);
        String message = report.getNote() != null ? report.getNote() : "Status check completed";
        return ResponseEntity.ok(ApiResponse.ok(message, report.getResults()));
    }

    @GetMapping("/api/trips/{id}/status")
    public ResponseEntity<ApiResponse<List<StatusResult>>> getTripStatus(@PathVariable String id) {
        tripAccessService.requireReadAccess(id);

        List<StatusResult> results = statusCheckService.getLastKnownStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Last known status per booking", results));
    }

    @PostMapping("/api/simulate/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateStatus(@RequestBody SimulateStatusRequest request) {
        if (statusProviderSelector.isLiveMode()) {
            throw new ConflictException("Simulation is disabled in live mode");
        }

        if (request == null || request.getBookingId() == null || request.getBookingId().isBlank()) {
            throw new ResourceNotFoundException("Booking ID is required");
        }

        BookingEntity booking = bookingJpaRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.getBookingId()));

        if (booking.getTrip() == null || !tripRepository.existsById(booking.getTrip().getId())) {
            throw new ResourceNotFoundException("Trip not found for booking: " + request.getBookingId());
        }

        String tripId = booking.getTrip().getId();
        tripAccessService.requireOwner(tripId);

        if (request.getState() == null || request.getState().isBlank()) {
            throw new IllegalArgumentException("Invalid state: state is required");
        }

        StatusState statusState;
        try {
            statusState = StatusState.valueOf(request.getState().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid state: " + request.getState());
        }

        if (request.getDelayMinutes() != null && request.getDelayMinutes() < 0) {
            throw new IllegalArgumentException("delayMinutes cannot be negative");
        }

        int delay = request.getDelayMinutes() != null ? request.getDelayMinutes() : 0;
        simulatedStatusProvider.setScriptedStatus(tripId, booking.getId(), statusState, delay);

        // When Apply Simulation is clicked, evaluate status check immediately and dispatch alert to email and mobile
        try {
            booking.setLastDedupKey(null);
            bookingJpaRepository.save(booking);
            statusCheckService.checkTripNow(tripId, true);
        } catch (Exception e) {
            log.warn("Could not immediately trigger status check on simulation: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok("Simulated status updated for booking " + booking.getId(),
                Map.of(
                        "tripId", tripId,
                        "bookingId", booking.getId(),
                        "state", statusState.name(),
                        "delayMinutes", delay
                )));
    }
}
