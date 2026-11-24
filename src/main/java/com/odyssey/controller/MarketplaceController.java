package com.odyssey.controller;

import com.odyssey.dto.market.*;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.entity.UserEntity;
import com.odyssey.service.AuthService;
import com.odyssey.service.InventoryService;
import com.odyssey.service.MarketRateLimiter;
import com.odyssey.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final AuthService authService;
    private final MarketRateLimiter marketRateLimiter;
    private final com.odyssey.service.InventoryService inventoryService;
    private final com.odyssey.service.status.StatusProviderSelector statusProviderSelector;

    private UserEntity requireUser() {
        UserEntity user = authService.getCurrentUserEntity();
        if (user == null) {
            throw new AccessDeniedException("Authentication required to access marketplace");
        }
        return user;
    }

    /**
     * City autocomplete across all Indian cities with alias support and fuzzy suggestions.
     */
    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<com.odyssey.model.CityInfo>>> searchCities(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "all", required = false, defaultValue = "false") boolean all) {
        UserEntity user = authService.getCurrentUserEntity();
        String limitKey = user != null ? user.getId().toString() : "ANONYMOUS";
        marketRateLimiter.checkLimit(limitKey);
        if (all) {
            List<com.odyssey.model.CityInfo> allCities = InventoryService.getAllCities();
            return ResponseEntity.ok(ApiResponse.ok("Retrieved " + allCities.size() + " cities", allCities));
        }
        List<com.odyssey.model.CityInfo> matches = inventoryService.searchCities(q);
        if (matches.isEmpty() && q != null && !q.isBlank()) {
            List<com.odyssey.model.CityInfo> closest = inventoryService.findClosestCities(q);
            return ResponseEntity.ok(ApiResponse.ok("City not found", closest));
        }
        return ResponseEntity.ok(ApiResponse.ok("Retrieved " + matches.size() + " cities", matches));
    }

    /**
     * Search transport services with canonical city alias normalization.
     */
    @GetMapping({"/services", "/departures/search"})
    public ResponseEntity<ApiResponse<List<ServiceSearchResponse>>> searchServices(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String mode) {
        UserEntity user = requireUser();
        marketRateLimiter.checkLimit(user.getId().toString());
        List<ServiceSearchResponse> results = marketplaceService.searchServices(origin, destination, date, mode);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved " + results.size() + " transport services", results));
    }

    /**
     * Search hotels in a city.
     */
    @GetMapping("/hotels")
    public ResponseEntity<ApiResponse<List<HotelSearchResponse>>> searchHotels(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") int rooms) {
        requireUser();
        List<HotelSearchResponse> results = marketplaceService.searchHotels(city, checkIn, checkOut, rooms);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved " + results.size() + " hotel accommodations", results));
    }

    /**
     * Get estimated cab quotes for a city route.
     */
    @GetMapping("/cabs/quote")
    public ResponseEntity<ApiResponse<List<CabQuoteResponse>>> getCabQuotes(
            @RequestParam String city,
            @RequestParam String pickup,
            @RequestParam String dropoff) {
        requireUser();
        List<CabQuoteResponse> quotes = marketplaceService.getCabQuotes(city, pickup, dropoff);
        return ResponseEntity.ok(ApiResponse.ok("Generated " + quotes.size() + " cab quotes", quotes));
    }

    /**
     * Book transport seats in one pessimistic transaction.
     */
    @PostMapping("/bookings")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> bookTransport(
            @Valid @RequestBody MarketplaceBookingRequest request) {
        UserEntity user = requireUser();
        MarketplaceBookingDto result = marketplaceService.bookTransport(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Booking confirmed successfully", result));
    }

    /**
     * Book hotel accommodation.
     */
    @PostMapping("/hotel-bookings")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> bookHotel(
            @Valid @RequestBody HotelBookingRequest request) {
        UserEntity user = requireUser();
        MarketplaceBookingDto result = marketplaceService.bookHotel(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Hotel booking confirmed successfully", result));
    }

    /**
     * Book cab transfer.
     */
    @PostMapping("/cab-bookings")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> bookCab(
            @Valid @RequestBody CabBookingRequest request) {
        UserEntity user = requireUser();
        MarketplaceBookingDto result = marketplaceService.bookCab(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cab booking confirmed successfully", result));
    }

    /**
     * Mock payment: accepts card details (declines test card 4000000000000002).
     * Never stores full card number or CVV.
     */
    @PostMapping("/payments/mock")
    public ResponseEntity<ApiResponse<MockPaymentResponse>> processMockPayment(
            @Valid @RequestBody MockPaymentRequest request) {
        UserEntity user = requireUser();
        MockPaymentResponse result = marketplaceService.processMockPayment(request, user);
        return ResponseEntity.ok(ApiResponse.ok("Demo payment processed successfully", result));
    }

    /**
     * Send verification request to provider with 30-second window upon selecting hotel/cab.
     */
    @PostMapping("/verification/request")
    public ResponseEntity<ApiResponse<BookingVerificationResponse>> requestBookingVerification(
            @Valid @RequestBody BookingVerificationRequest request) {
        UserEntity user = requireUser();
        BookingVerificationResponse result = marketplaceService.requestBookingVerification(request, user);
        return ResponseEntity.ok(ApiResponse.ok("Verification email sent to provider", result));
    }

    /**
     * Retrieve current user's bookings.
     */
    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<MarketplaceBookingDto>>> getMyBookings() {
        UserEntity user = requireUser();
        List<MarketplaceBookingDto> bookings = marketplaceService.getMyBookings(user);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved " + bookings.size() + " bookings", bookings));
    }

    /**
     * Get booking details by ID (user ownership enforced).
     */
    @GetMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> getBookingById(@PathVariable String id) {
        UserEntity user = requireUser();
        MarketplaceBookingDto booking = marketplaceService.getBookingById(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Booking details retrieved", booking));
    }

    @GetMapping("/bookings/{id}/status")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> getBookingStatus(@PathVariable String id) {
        UserEntity user = requireUser();
        MarketplaceBookingDto booking = marketplaceService.getBookingStatus(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Booking status retrieved", booking));
    }

    /**
     * Cancel booking: releases seat holds, refunds payment.
     */
    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> cancelBooking(@PathVariable String id) {
        UserEntity user = requireUser();
        MarketplaceBookingDto cancelled = marketplaceService.cancelBooking(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Booking cancelled and seats released successfully", cancelled));
    }

    /**
     * Printable HTML summary with required demo disclaimer.
     */
    @GetMapping(value = "/bookings/{id}/summary", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getBookingSummaryHtml(@PathVariable String id) {
        UserEntity user = requireUser();
        String html = marketplaceService.generatePrintableSummary(id, user);
        return ResponseEntity.ok(html);
    }

    /**
     * Create checkout draft for /checkout/{bookingDraftId} route.
     */
    @PostMapping("/checkout/draft")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> createDraft(
            @RequestBody java.util.Map<String, Object> draftData) {
        UserEntity user = requireUser();
        java.util.Map<String, Object> result = marketplaceService.createBookingDraft(draftData);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Booking draft created", result));
    }

    /**
     * Retrieve checkout draft by ID.
     */
    @GetMapping("/checkout/draft/{id}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getDraft(
            @PathVariable String id) {
        requireUser();
        java.util.Map<String, Object> result = marketplaceService.getBookingDraft(id);
        return ResponseEntity.ok(ApiResponse.ok("Booking draft retrieved", result));
    }

    /**
     * In SIMULATED mode only: POST /api/market/simulate/departure
     * {serviceDepartureId, state, delayMinutes}
     * (409 in live mode, 400 for invalid input, requires login, caller must hold booking on departure or 403)
     */
    @PostMapping("/simulate/departure")
    public ResponseEntity<ApiResponse<String>> simulateDeparture(
            @Valid @RequestBody SimulateDepartureRequest request) {
        if (statusProviderSelector.isLiveMode()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Simulation is disabled in live mode. Active mode is LIVE."));
        }
        UserEntity user = requireUser();
        marketplaceService.simulateDeparture(request, user);
        return ResponseEntity.ok(ApiResponse.ok("Departure simulated as " + request.getState() + " across all affected trips", "OK"));
    }

    /**
     * Preview replacement booking details before confirming.
     */
    @GetMapping("/replacement/preview")
    public ResponseEntity<ApiResponse<ReplacementPreviewResponse>> previewReplacement(
            @RequestParam("tripId") String tripId,
            @RequestParam("oldBookingId") String oldBookingId,
            @RequestParam("serviceDepartureId") String serviceDepartureId) {
        UserEntity user = requireUser();
        ReplacementPreviewResponse preview = marketplaceService.previewReplacement(tripId, oldBookingId, serviceDepartureId, user);
        return ResponseEntity.ok(ApiResponse.ok("Replacement preview retrieved", preview));
    }

    /**
     * Book replacement leg in the marketplace in one transaction:
     * cancels old booking with DEMO_REFUNDED, books replacement, updates trip and plan.
     */
    @PostMapping("/replacement/book")
    public ResponseEntity<ApiResponse<ReplacementBookingResponse>> bookReplacement(
            @Valid @RequestBody ReplacementBookingRequest request) {
        UserEntity user = requireUser();
        ReplacementBookingResponse result = marketplaceService.bookReplacement(request, user);
        return ResponseEntity.ok(ApiResponse.ok("Replacement successfully booked in demo marketplace", result));
    }
}
