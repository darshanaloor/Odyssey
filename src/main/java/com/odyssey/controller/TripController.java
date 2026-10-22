package com.odyssey.controller;

import com.odyssey.dto.request.AddBookingRequest;
import com.odyssey.dto.request.CreateTripRequest;
import com.odyssey.dto.request.UpdateConstraintsRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.ParseResult;
import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.Trip;
import com.odyssey.dto.request.BookBookingRequest;
import com.odyssey.dto.response.HandoffLegDto;
import com.odyssey.service.HandoffService;
import com.odyssey.service.TripAccessService;
import com.odyssey.service.LlmService;
import com.odyssey.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final LlmService llmService;
    private final TripAccessService tripAccessService;
    private final HandoffService handoffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Trip>>> getAllTrips() {
        List<Trip> trips = tripService.getAllTrips();
        return ResponseEntity.ok(ApiResponse.ok("Retrieved " + trips.size() + " trips", trips));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Trip>> getTripById(@PathVariable String id) {
        Trip trip = tripAccessService.requireReadAccess(id);
        return ResponseEntity.ok(ApiResponse.ok(trip));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<ApiResponse<List<AuditEntry>>> getTripAudit(@PathVariable String id) {
        Trip trip = tripAccessService.requireReadAccess(id);
        return ResponseEntity.ok(ApiResponse.ok("Audit log retrieved for trip " + id, trip.getAuditLog()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Trip>> createTrip(@Valid @RequestBody CreateTripRequest request) {
        Trip created = tripService.createTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Trip created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Trip>> updateTrip(
            @PathVariable String id,
            @Valid @RequestBody CreateTripRequest request) {
        tripAccessService.requireOwner(id);
        Trip updated = tripService.updateTrip(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Trip updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(@PathVariable String id) {
        tripAccessService.requireOwner(id);
        tripService.deleteTrip(id);
        return ResponseEntity.ok(ApiResponse.ok("Trip " + id + " deleted successfully", null));
    }

    @PostMapping("/{id}/bookings")
    public ResponseEntity<ApiResponse<Trip>> addBooking(
            @PathVariable String id,
            @Valid @RequestBody AddBookingRequest request) {
        tripAccessService.requireOwner(id);
        Trip updated = tripService.addBooking(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Booking added to trip", updated));
    }

    @PutMapping("/{id}/bookings/{bookingId}")
    public ResponseEntity<ApiResponse<Trip>> updateBooking(
            @PathVariable String id,
            @PathVariable String bookingId,
            @Valid @RequestBody AddBookingRequest request) {
        tripAccessService.requireOwner(id);
        Trip updated = tripService.updateBooking(id, bookingId, request);
        return ResponseEntity.ok(ApiResponse.ok("Booking updated successfully", updated));
    }

    @DeleteMapping("/{id}/bookings/{bookingId}")
    public ResponseEntity<ApiResponse<Trip>> deleteBooking(
            @PathVariable String id,
            @PathVariable String bookingId) {
        tripAccessService.requireOwner(id);
        Trip updated = tripService.deleteBooking(id, bookingId);
        return ResponseEntity.ok(ApiResponse.ok("Booking removed from trip", updated));
    }

    @PutMapping("/{id}/constraints")
    public ResponseEntity<ApiResponse<Trip>> updateConstraints(
            @PathVariable String id,
            @Valid @RequestBody UpdateConstraintsRequest request) {
        tripAccessService.requireOwner(id);
        Trip updated = tripService.updateConstraints(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Constraints updated successfully", updated));
    }

    @PostMapping("/{id}/bookings/{bookingId}/book")
    public ResponseEntity<ApiResponse<Trip>> markBookingBooked(
            @PathVariable String id,
            @PathVariable String bookingId,
            @RequestBody(required = false) BookBookingRequest request) {
        tripAccessService.requireOwner(id);
        String ref = request != null ? request.getBookingReference() : null;
        Trip updated = handoffService.markBookingBooked(id, bookingId, ref);
        return ResponseEntity.ok(ApiResponse.ok("Booking marked as booked", updated));
    }

    @PostMapping("/{id}/bookings/{bookingId}/unbook")
    public ResponseEntity<ApiResponse<Trip>> unmarkBookingBooked(
            @PathVariable String id,
            @PathVariable String bookingId) {
        tripAccessService.requireOwner(id);
        Trip updated = handoffService.unmarkBookingBooked(id, bookingId);
        return ResponseEntity.ok(ApiResponse.ok("Booking unmarked as booked", updated));
    }

    @GetMapping("/{id}/handoff")
    public ResponseEntity<ApiResponse<List<HandoffLegDto>>> getHandoffLegs(@PathVariable String id) {
        Trip trip = tripAccessService.requireReadAccess(id);
        List<HandoffLegDto> legs = handoffService.getHandoffLegsForTrip(trip);
        return ResponseEntity.ok(ApiResponse.ok("Handoff legs retrieved", legs));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<ParseResult>> parseConfirmationTextGeneral(
            @RequestBody Map<String, String> body) {
        String text = body != null ? body.get("text") : "";
        ParseResult result = llmService.parseText(text);
        return ResponseEntity.ok(ApiResponse.ok("Parsed " + result.getBookings().size() + " bookings from text", result));
    }

    @PostMapping("/{id}/parse")
    public ResponseEntity<ApiResponse<ParseResult>> parseConfirmationText(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        tripAccessService.requireOwner(id);
        String text = body != null ? body.get("text") : "";
        ParseResult result = llmService.parseText(text);
        return ResponseEntity.ok(ApiResponse.ok("Parsed " + result.getBookings().size() + " bookings from text", result));
    }
}
