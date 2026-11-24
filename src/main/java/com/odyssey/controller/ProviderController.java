package com.odyssey.controller;

import com.odyssey.dto.market.MarketplaceBookingDto;
import com.odyssey.dto.provider.*;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.service.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    // 1. Onboarding
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<ProviderResponse>> register(
            @Valid @RequestBody ProviderRegisterRequest request,
            HttpServletRequest httpRequest) {
        ProviderResponse response = providerService.registerProvider(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Provider registered successfully. Status is PENDING review.", response));
    }

    @GetMapping({"/me", "/profile", "/dashboard"})
    public ResponseEntity<ApiResponse<ProviderResponse>> getProfile() {
        ProviderResponse response = providerService.getCurrentProviderProfile();
        return ResponseEntity.ok(ApiResponse.ok("Provider profile retrieved successfully", response));
    }

    // 2. Vehicles
    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<List<ProviderVehicleDto>>> getVehicles() {
        List<ProviderVehicleDto> list = providerService.getVehicles();
        return ResponseEntity.ok(ApiResponse.ok("Vehicles retrieved successfully", list));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponse<ProviderVehicleDto>> createVehicle(
            @Valid @RequestBody ProviderVehicleRequest request) {
        ProviderVehicleDto dto = providerService.createVehicle(request);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle created successfully", dto));
    }

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<ApiResponse<ProviderVehicleDto>> updateVehicle(
            @PathVariable String id,
            @Valid @RequestBody ProviderVehicleRequest request) {
        ProviderVehicleDto dto = providerService.updateVehicle(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle updated successfully", dto));
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable String id) {
        providerService.deleteVehicle(id);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }

    // 3. Schedules
    @GetMapping("/schedules")
    public ResponseEntity<ApiResponse<List<ProviderScheduleDto>>> getSchedules() {
        List<ProviderScheduleDto> list = providerService.getSchedules();
        return ResponseEntity.ok(ApiResponse.ok("Schedules retrieved successfully", list));
    }

    @PostMapping("/schedules")
    public ResponseEntity<ApiResponse<ProviderScheduleDto>> createSchedule(
            @Valid @RequestBody ProviderScheduleRequest request) {
        ProviderScheduleDto dto = providerService.createSchedule(request);
        return ResponseEntity.ok(ApiResponse.ok("Schedule created successfully", dto));
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<ProviderScheduleDto>> updateSchedule(
            @PathVariable String id,
            @Valid @RequestBody ProviderScheduleRequest request) {
        ProviderScheduleDto dto = providerService.updateSchedule(id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto.getMessage() != null ? dto.getMessage() : "Schedule updated successfully", dto));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable String id) {
        providerService.deleteSchedule(id);
        return ResponseEntity.ok(ApiResponse.ok("Schedule deactivated successfully", null));
    }

    // 4. Departures & Bookings
    @GetMapping("/departures")
    public ResponseEntity<ApiResponse<List<ProviderDepartureDto>>> getUpcomingDepartures() {
        List<ProviderDepartureDto> list = providerService.getUpcomingDepartures();
        return ResponseEntity.ok(ApiResponse.ok("Upcoming departures retrieved successfully", list));
    }

    @GetMapping("/departures/{id}/bookings")
    public ResponseEntity<ApiResponse<List<ProviderBookingDto>>> getDepartureBookings(@PathVariable String id) {
        List<ProviderBookingDto> list = providerService.getDepartureBookings(id);
        return ResponseEntity.ok(ApiResponse.ok("Departure bookings retrieved successfully", list));
    }

    @PostMapping("/departures/{id}/status")
    public ResponseEntity<ApiResponse<ProviderDepartureDto>> reportStatus(
            @PathVariable String id,
            @Valid @RequestBody ProviderReportStatusRequest request) {
        ProviderDepartureDto dto = providerService.reportDepartureStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Status reported successfully", dto));
    }

    @PostMapping("/departures/{id}/offer-alternative")
    public ResponseEntity<ApiResponse<ProviderDepartureDto>> offerAlternative(
            @PathVariable String id,
            @Valid @RequestBody com.odyssey.dto.provider.ProviderOfferAlternativeRequest request) {
        ProviderDepartureDto dto = providerService.offerAlternativeVehicle(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Alternative vehicle offered successfully", dto));
    }

    // 5. Hotels
    @GetMapping("/hotels")
    public ResponseEntity<ApiResponse<List<ProviderHotelResponse>>> getHotels() {
        List<ProviderHotelResponse> list = providerService.getHotels();
        return ResponseEntity.ok(ApiResponse.ok("Hotels retrieved successfully", list));
    }

    @PostMapping("/hotels")
    public ResponseEntity<ApiResponse<ProviderHotelResponse>> createHotel(
            @Valid @RequestBody ProviderHotelRequest request) {
        ProviderHotelResponse response = providerService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Hotel created successfully", response));
    }

    @GetMapping("/hotels/{id}")
    public ResponseEntity<ApiResponse<ProviderHotelResponse>> getHotel(@PathVariable String id) {
        ProviderHotelResponse response = providerService.getHotel(id);
        return ResponseEntity.ok(ApiResponse.ok("Hotel retrieved successfully", response));
    }

    @PutMapping("/hotels/{id}")
    public ResponseEntity<ApiResponse<ProviderHotelResponse>> updateHotel(
            @PathVariable String id,
            @Valid @RequestBody ProviderHotelRequest request) {
        ProviderHotelResponse response = providerService.updateHotel(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Hotel updated successfully", response));
    }

    @DeleteMapping("/hotels/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHotel(@PathVariable String id) {
        providerService.deleteHotel(id);
        return ResponseEntity.ok(ApiResponse.ok("Hotel deleted successfully", null));
    }

    @PatchMapping("/hotels/{id}/status")
    public ResponseEntity<ApiResponse<Void>> toggleHotelStatus(
            @PathVariable String id,
            @RequestParam boolean active) {
        providerService.toggleHotelStatus(id, active);
        return ResponseEntity.ok(ApiResponse.ok("Hotel status updated successfully", null));
    }

    // 6. Hotel Room Types & Availability
    @GetMapping("/hotels/{hotelId}/rooms")
    public ResponseEntity<ApiResponse<List<ProviderRoomTypeResponse>>> getRoomTypes(@PathVariable String hotelId) {
        List<ProviderRoomTypeResponse> list = providerService.getRoomTypes(hotelId);
        return ResponseEntity.ok(ApiResponse.ok("Room types retrieved successfully", list));
    }

    @PostMapping("/hotels/{hotelId}/rooms")
    public ResponseEntity<ApiResponse<ProviderRoomTypeResponse>> createRoomType(
            @PathVariable String hotelId,
            @Valid @RequestBody ProviderRoomTypeRequest request) {
        ProviderRoomTypeResponse response = providerService.createRoomType(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Room type created and availability materialised", response));
    }

    @PutMapping("/hotels/{hotelId}/rooms/{roomId}")
    public ResponseEntity<ApiResponse<ProviderRoomTypeResponse>> updateRoomType(
            @PathVariable String hotelId,
            @PathVariable String roomId,
            @Valid @RequestBody ProviderRoomTypeRequest request) {
        ProviderRoomTypeResponse response = providerService.updateRoomType(hotelId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok("Room type updated successfully", response));
    }

    @DeleteMapping("/hotels/{hotelId}/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoomType(
            @PathVariable String hotelId,
            @PathVariable String roomId) {
        providerService.deleteRoomType(hotelId, roomId);
        return ResponseEntity.ok(ApiResponse.ok("Room type deleted successfully", null));
    }

    @GetMapping("/hotels/{hotelId}/rooms/{roomId}/availability")
    public ResponseEntity<ApiResponse<List<com.odyssey.entity.HotelRoomAvailabilityEntity>>> getRoomAvailability(
            @PathVariable String hotelId,
            @PathVariable String roomId) {
        List<com.odyssey.entity.HotelRoomAvailabilityEntity> list = providerService.getRoomAvailability(hotelId, roomId);
        return ResponseEntity.ok(ApiResponse.ok("Availability retrieved successfully", list));
    }

    @PutMapping("/hotels/{hotelId}/rooms/{roomId}/availability")
    public ResponseEntity<ApiResponse<Void>> updateRoomAvailability(
            @PathVariable String hotelId,
            @PathVariable String roomId,
            @Valid @RequestBody ProviderAvailabilityUpdateDto request) {
        providerService.updateRoomAvailability(hotelId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok("Availability updated successfully", null));
    }

    // 7. Hotel Bookings Manifest & Cannot Honour
    @GetMapping("/hotels/{hotelId}/bookings")
    public ResponseEntity<ApiResponse<List<ProviderHotelBookingDto>>> getHotelBookings(@PathVariable String hotelId) {
        List<ProviderHotelBookingDto> list = providerService.getHotelBookings(hotelId);
        return ResponseEntity.ok(ApiResponse.ok("Hotel bookings retrieved successfully", list));
    }

    @PostMapping({"/hotels/bookings/{bookingId}/cannot-honour", "/bookings/{bookingId}/cannot-honour"})
    public ResponseEntity<ApiResponse<Void>> markBookingCannotHonour(
            @PathVariable String bookingId,
            @Valid @RequestBody CannotHonourRequest request) {
        providerService.markBookingCannotHonour(bookingId, request);
        return ResponseEntity.ok(ApiResponse.ok("Booking marked cannot honour and traveller alerted", null));
    }

    // 8. Cab Services
    @GetMapping("/cabs")
    public ResponseEntity<ApiResponse<List<ProviderCabServiceResponse>>> getCabs() {
        List<ProviderCabServiceResponse> list = providerService.getCabs();
        return ResponseEntity.ok(ApiResponse.ok("Cab services retrieved successfully", list));
    }

    @PostMapping("/cabs")
    public ResponseEntity<ApiResponse<ProviderCabServiceResponse>> createCab(
            @Valid @RequestBody ProviderCabServiceRequest request) {
        ProviderCabServiceResponse response = providerService.createCab(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cab service created successfully", response));
    }

    @PutMapping("/cabs/{id}")
    public ResponseEntity<ApiResponse<ProviderCabServiceResponse>> updateCab(
            @PathVariable String id,
            @Valid @RequestBody ProviderCabServiceRequest request) {
        ProviderCabServiceResponse response = providerService.updateCab(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cab service updated successfully", response));
    }

    @DeleteMapping("/cabs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCab(@PathVariable String id) {
        providerService.deleteCab(id);
        return ResponseEntity.ok(ApiResponse.ok("Cab service deleted successfully", null));
    }

    @PatchMapping("/cabs/{id}/status")
    public ResponseEntity<ApiResponse<Void>> toggleCabStatus(
            @PathVariable String id,
            @RequestParam boolean active) {
        providerService.toggleCabStatus(id, active);
        return ResponseEntity.ok(ApiResponse.ok("Cab service status updated successfully", null));
    }

    // 9. Provider-Issued Refunds (Phase 18C)
    @PostMapping("/refunds")
    public ResponseEntity<ApiResponse<ProviderRefundResponse>> issueRefund(
            @Valid @RequestBody ProviderRefundRequest request) {
        ProviderRefundResponse response = providerService.issueProviderRefund(request);
        return ResponseEntity.ok(ApiResponse.ok("Provider refund issued successfully", response));
    }

    @GetMapping("/refunds/eligible")
    public ResponseEntity<ApiResponse<List<ProviderRefundEligibleBookingDto>>> getEligibleRefunds() {
        List<ProviderRefundEligibleBookingDto> list = providerService.getRefundEligibleBookings();
        return ResponseEntity.ok(ApiResponse.ok("Eligible bookings for refund retrieved successfully", list));
    }

    // 10. Provider Confirmation Workflow (Phase 18E)
    @GetMapping("/pending-bookings")
    public ResponseEntity<ApiResponse<List<MarketplaceBookingDto>>> getPendingBookings() {
        List<MarketplaceBookingDto> list = providerService.getPendingBookings();
        return ResponseEntity.ok(ApiResponse.ok("Pending bookings retrieved", list));
    }

    @PostMapping("/bookings/{id}/accept")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> acceptBooking(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        String driverName = (body != null && body.containsKey("assignedDriverName")) ? body.get("assignedDriverName") : null;
        MarketplaceBookingDto result = providerService.acceptBooking(id, driverName);
        return ResponseEntity.ok(ApiResponse.ok("Booking accepted successfully", result));
    }

    @PostMapping("/bookings/{id}/reject")
    public ResponseEntity<ApiResponse<MarketplaceBookingDto>> rejectBooking(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Declined by provider";
        MarketplaceBookingDto result = providerService.rejectBooking(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("Booking rejected", result));
    }
}
