package com.odyssey.controller;

import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.wallet.TripWalletDto;
import com.odyssey.model.Trip;
import com.odyssey.service.TripAccessService;
import com.odyssey.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Controller for the Trip Wallet feature (Phase 14).
 * Strictly read-only operations with ownership and read-access verification.
 */
@Slf4j
@RestController
@RequestMapping("/api/trips/{tripId}/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TripAccessService tripAccessService;

    /**
     * GET /api/trips/{tripId}/wallet
     * Returns structured JSON data for the trip wallet.
     * Contains stored booking details, last known status block, header, and ledger summary line.
     * Excludes sensitive fields (passwords, sessions, cards, tokens, emails, phone numbers).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<TripWalletDto>> getWallet(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        tripAccessService.filterSensitiveNeedsForNonOwners(trip);
        TripWalletDto dto = walletService.getWallet(trip);
        return ResponseEntity.ok(ApiResponse.ok("Trip wallet retrieved successfully", dto));
    }

    /**
     * GET /api/trips/{tripId}/wallet/print
     * Returns a server-rendered HTML page with print CSS.
     * Contains no <script> tags and no inline event handlers.
     */
    @GetMapping(value = "/print", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPrintableSummary(
            @PathVariable String tripId,
            @RequestParam(value = "includeEmergency", defaultValue = "false") boolean includeEmergency) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        tripAccessService.filterSensitiveNeedsForNonOwners(trip);
        boolean allowEmergency = includeEmergency && tripAccessService.isOwner(trip);
        String html = walletService.generatePrintHtml(trip, allowEmergency);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }

    /**
     * GET /api/trips/{tripId}/wallet/calendar
     * Returns an RFC 5545 iCalendar (.ics) file download.
     * Times in UTC with 'Z' suffix, stable UIDs, cancelled status handling, and line folding at 75 octets.
     */
    @GetMapping(value = "/calendar", produces = "text/calendar")
    public ResponseEntity<String> getCalendarFile(@PathVariable String tripId) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        String ics = walletService.generateCalendar(trip);
        String filename = walletService.sanitizeFilename(tripId, ".ics");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .body(ics);
    }

    /**
     * GET /api/trips/{tripId}/wallet/pack
     * Returns a self-contained offline HTML trip pack attachment.
     * Inline CSS only, no external resources, no <script> tags.
     */
    @GetMapping(value = "/pack", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getOfflineTripPack(
            @PathVariable String tripId,
            @RequestParam(value = "includeEmergency", defaultValue = "false") boolean includeEmergency) {
        Trip trip = tripAccessService.requireReadAccess(tripId);
        tripAccessService.filterSensitiveNeedsForNonOwners(trip);
        boolean allowEmergency = includeEmergency && tripAccessService.isOwner(trip);
        String packHtml = walletService.generatePackHtml(trip, allowEmergency);
        String filename = walletService.sanitizeFilename(tripId, "-pack.html");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(packHtml);
    }
}

