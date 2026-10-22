package com.odyssey.controller;

import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.DisruptionResponse;
import com.odyssey.model.TripImpactReport;
import com.odyssey.service.DisruptionService;
import com.odyssey.service.LlmService;
import com.odyssey.service.RecoveryService;
import com.odyssey.service.TripAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class DisruptionController {

    private final DisruptionService disruptionService;
    private final RecoveryService recoveryService;
    private final LlmService llmService;
    private final com.odyssey.repository.TripRepository tripRepository;
    private final TripAccessService tripAccessService;

    @PostMapping("/disruptions")
    public ResponseEntity<ApiResponse<DisruptionResponse>> reportDisruption(
            @PathVariable String tripId,
            @Valid @RequestBody ReportDisruptionRequest request) {
        tripAccessService.requireOwner(tripId);
        DisruptionResponse response = disruptionService.reportDisruption(tripId, request);
        return ResponseEntity.ok(ApiResponse.ok("Disruption evaluated and impact analyzed", response));
    }

    @PostMapping("/bookings/{bookingId}/report-problem")
    public ResponseEntity<ApiResponse<DisruptionResponse>> reportProblem(
            @PathVariable String tripId,
            @PathVariable String bookingId,
            @RequestBody com.odyssey.dto.request.ReportProblemRequest request) {
        tripAccessService.requireOwner(tripId);
        com.odyssey.model.enums.DisruptionType disruptionType =
                (request != null && "CANCELLED".equalsIgnoreCase(request.getType()))
                        ? com.odyssey.model.enums.DisruptionType.CANCELLATION
                        : com.odyssey.model.enums.DisruptionType.FLIGHT_DELAY;

        int delayMinutes = (request != null && request.getDelayMinutes() != null) ? request.getDelayMinutes() : 0;
        String reason = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote()
                : ("Reported manually by traveller (" + disruptionType.name() + ")");

        ReportDisruptionRequest req = ReportDisruptionRequest.builder()
                .bookingId(bookingId)
                .type(disruptionType)
                .delayMinutes(delayMinutes)
                .reason(reason)
                .actor(com.odyssey.model.enums.AuditActor.USER)
                .source("MANUAL")
                .build();

        DisruptionResponse response = disruptionService.reportDisruption(tripId, req);
        return ResponseEntity.ok(ApiResponse.ok("Problem reported manually by user", response));
    }

    @GetMapping("/impact")
    public ResponseEntity<ApiResponse<TripImpactReport>> getTripImpact(@PathVariable String tripId) {
        tripAccessService.requireOwner(tripId);
        TripImpactReport impact = disruptionService.getImpactReport(tripId);
        return ResponseEntity.ok(ApiResponse.ok("Trip impact analysis retrieved", impact));
    }

    @PostMapping({"/recover", "/recovery"})
    public ResponseEntity<ApiResponse<com.odyssey.dto.response.RecoveryResponse>> generateRecoveryOptions(
            @PathVariable String tripId,
            @RequestParam(required = false) Boolean allowOvernight,
            @RequestParam(required = false) Integer extendDeadlineHours,
            @RequestParam(required = false) Integer raiseBudgetPercent,
            @RequestBody(required = false) com.odyssey.dto.request.RelaxConstraintsRequest request) {
        tripAccessService.requireOwner(tripId);
        com.odyssey.dto.request.RelaxConstraintsRequest effective = request != null ? request : new com.odyssey.dto.request.RelaxConstraintsRequest();
        if (allowOvernight != null) {
            effective.setAllowOvernight(allowOvernight);
        }
        if (extendDeadlineHours != null) {
            effective.setExtendDeadlineHours(extendDeadlineHours);
        }
        if (raiseBudgetPercent != null) {
            effective.setRaiseBudgetPercent(raiseBudgetPercent);
        }
        com.odyssey.dto.response.RecoveryResponse response = disruptionService.generateRecoveryOptions(tripId, effective);
        return ResponseEntity.ok(ApiResponse.ok("Recovery options generated and ranked", response));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<java.util.List<com.odyssey.model.Booking>>> parseBookings(
            @PathVariable String tripId,
            @Valid @RequestBody com.odyssey.dto.request.ParseBookingRequest request) {
        tripAccessService.requireOwner(tripId);
        tripRepository.findById(tripId)
                .orElseThrow(() -> new com.odyssey.exception.ResourceNotFoundException("Trip not found with ID: " + tripId));
        java.util.List<com.odyssey.model.Booking> extracted = llmService.extractBookings(request.getText());
        return ResponseEntity.ok(ApiResponse.ok("Bookings extracted successfully", extracted));
    }

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<com.odyssey.model.RecoveryPlan>> buildPlan(
            @PathVariable String tripId,
            @RequestBody(required = false) com.odyssey.dto.request.SelectOptionRequest request,
            @RequestParam(required = false, name = "optionId") String optionId) {
        tripAccessService.requireOwner(tripId);
        String resolvedOptionId = resolveOptionId(request, optionId);
        com.odyssey.model.RecoveryPlan plan = recoveryService.buildPlan(tripId, resolvedOptionId);
        return ResponseEntity.ok(ApiResponse.ok("Recovery plan generated", plan));
    }

    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<com.odyssey.model.RecoveryPlan>> approvePlan(
            @PathVariable String tripId,
            @RequestBody(required = false) com.odyssey.dto.request.SelectOptionRequest request,
            @RequestParam(required = false, name = "optionId") String optionId) {
        tripAccessService.requireOwner(tripId);
        String resolvedOptionId = resolveOptionId(request, optionId);
        com.odyssey.model.RecoveryPlan plan = recoveryService.approvePlan(tripId, resolvedOptionId);
        return ResponseEntity.ok(ApiResponse.ok("Recovery plan approved and committed", plan));
    }

    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<com.odyssey.model.Trip>> rejectPlan(
            @PathVariable String tripId,
            @RequestBody(required = false) com.odyssey.dto.request.SelectOptionRequest request,
            @RequestParam(required = false, name = "optionId") String optionId) {
        tripAccessService.requireOwner(tripId);
        String resolvedOptionId = resolveOptionId(request, optionId);
        com.odyssey.model.Trip trip = recoveryService.rejectPlan(tripId, resolvedOptionId);
        return ResponseEntity.ok(ApiResponse.ok("Recovery plan rejected by user", trip));
    }

    private String resolveOptionId(com.odyssey.dto.request.SelectOptionRequest request, String queryOptionId) {
        if (request != null && request.getOptionId() != null && !request.getOptionId().trim().isEmpty()) {
            return request.getOptionId().trim();
        }
        if (queryOptionId != null && !queryOptionId.trim().isEmpty()) {
            return queryOptionId.trim();
        }
        throw new IllegalArgumentException("optionId is required");
    }

    @GetMapping("/plan/latest")
    public ResponseEntity<ApiResponse<com.odyssey.model.RecoveryPlan>> getLatestPlan(
            @PathVariable String tripId) {
        tripAccessService.requireOwner(tripId);
        com.odyssey.model.RecoveryPlan plan = recoveryService.getLatestPlan(tripId);
        return ResponseEntity.ok(ApiResponse.ok("Latest recovery plan retrieved", plan));
    }
}
