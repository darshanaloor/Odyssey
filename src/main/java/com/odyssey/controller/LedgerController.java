package com.odyssey.controller;

import com.odyssey.dto.request.RecordRefundRequest;
import com.odyssey.dto.request.ShareRefundRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.ShareRefundResponse;
import com.odyssey.dto.response.TripLedgerResponse;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Trip;
import com.odyssey.service.LedgerService;
import com.odyssey.service.TripAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * REST controller for the cost and refund ledger:
 * summary figures, itemized entries, traveller refund recording, refund sharing, and CSV export.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final TripAccessService tripAccessService;

    @GetMapping
    public ResponseEntity<ApiResponse<TripLedgerResponse>> getLedger(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        return ResponseEntity.ok(ApiResponse.ok("Cost and refund ledger retrieved", ledgerService.getLedger(trip)));
    }

    @PostMapping("/refunds")
    public ResponseEntity<ApiResponse<TripLedgerResponse>> recordRefund(
            @PathVariable String tripId,
            @Valid @RequestBody RecordRefundRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        return ResponseEntity.ok(ApiResponse.ok(
                "Refund recorded successfully",
                ledgerService.recordRefund(trip, request)));
    }

    @PostMapping("/refunds/{entryId}/share")
    public ResponseEntity<ApiResponse<ShareRefundResponse>> shareRefund(
            @PathVariable String tripId,
            @PathVariable String entryId,
            @Valid @RequestBody ShareRefundRequest request) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        UserEntity currentUser = tripAccessService.getCurrentUserEntity();
        ShareRefundResponse response = ledgerService.shareRefund(trip, entryId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Refund summary shared successfully", response));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        String csvContent = ledgerService.exportCsv(trip);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ledger-" + tripId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}

