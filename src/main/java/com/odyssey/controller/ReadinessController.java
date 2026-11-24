package com.odyssey.controller;

import com.odyssey.dto.readiness.TripReadinessDto;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.model.Trip;
import com.odyssey.service.ReadinessService;
import com.odyssey.service.TripAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Phase 15: Trip Readiness Score.
 * Strictly read-only endpoint with ownership verification.
 */
@Slf4j
@RestController
@RequestMapping("/api/trips/{tripId}/readiness")
@RequiredArgsConstructor
public class ReadinessController {

    private final ReadinessService readinessService;
    private final TripAccessService tripAccessService;

    /**
     * GET /api/trips/{tripId}/readiness
     * Computes deterministic readiness score across connection buffers, commitment slack,
     * completeness, recovery alternatives, and monitoring coverage.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<TripReadinessDto>> getReadiness(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        TripReadinessDto dto = readinessService.calculateReadiness(trip);
        return ResponseEntity.ok(ApiResponse.ok("Trip readiness calculated successfully", dto));
    }
}

