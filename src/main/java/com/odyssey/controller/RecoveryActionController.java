package com.odyssey.controller;

import com.odyssey.dto.request.RecoveryActionNoteRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.RecoveryActionsResponse;
import com.odyssey.model.Trip;
import com.odyssey.service.RecoveryActionService;
import com.odyssey.service.TripAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for tracking execution progress of approved recovery plan actions.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/actions")
@RequiredArgsConstructor
public class RecoveryActionController {

    private final RecoveryActionService recoveryActionService;
    private final TripAccessService tripAccessService;

    @GetMapping
    public ResponseEntity<ApiResponse<RecoveryActionsResponse>> getActions(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        return ResponseEntity.ok(ApiResponse.ok("Recovery actions retrieved", recoveryActionService.getActions(trip)));
    }

    @PostMapping("/{actionId}/complete")
    public ResponseEntity<ApiResponse<RecoveryActionsResponse>> completeAction(
            @PathVariable String tripId,
            @PathVariable String actionId,
            @RequestBody(required = false) RecoveryActionNoteRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                "Recovery action marked as done",
                recoveryActionService.completeAction(trip, actionId, note)));
    }

    @PostMapping("/{actionId}/skip")
    public ResponseEntity<ApiResponse<RecoveryActionsResponse>> skipAction(
            @PathVariable String tripId,
            @PathVariable String actionId,
            @RequestBody(required = false) RecoveryActionNoteRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                "Recovery action skipped",
                recoveryActionService.skipAction(trip, actionId, note)));
    }

    @PostMapping("/{actionId}/reopen")
    public ResponseEntity<ApiResponse<RecoveryActionsResponse>> reopenAction(
            @PathVariable String tripId,
            @PathVariable String actionId,
            @RequestBody(required = false) RecoveryActionNoteRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                "Recovery action reopened",
                recoveryActionService.reopenAction(trip, actionId, note)));
    }
}

