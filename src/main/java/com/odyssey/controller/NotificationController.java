package com.odyssey.controller;

import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.ChannelStatusResponse;
import com.odyssey.dto.response.NotificationHistoryResponse;
import com.odyssey.model.Trip;
import com.odyssey.service.NotificationService;
import com.odyssey.service.TripAccessService;
import com.odyssey.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final TripService tripService;
    private final TripAccessService tripAccessService;

    @GetMapping("/api/notifications/status")
    public ResponseEntity<ApiResponse<ChannelStatusResponse>> getStatus() {
        ChannelStatusResponse status = notificationService.getChannelStatus();
        return ResponseEntity.ok(ApiResponse.ok("Channel status retrieved", status));
    }

    @GetMapping("/api/trips/{id}/notifications")
    public ResponseEntity<ApiResponse<List<NotificationHistoryResponse>>> getHistory(@PathVariable String id) {
        tripAccessService.requireOwner(id);
        List<NotificationHistoryResponse> history = notificationService.getNotificationHistory(id);
        return ResponseEntity.ok(ApiResponse.ok("Notification history retrieved", history));
    }

    @PostMapping("/api/trips/{id}/notifications/test")
    public ResponseEntity<ApiResponse<List<NotificationHistoryResponse>>> sendTest(@PathVariable String id) {
        Trip trip = tripAccessService.requireOwner(id);
        List<NotificationHistoryResponse> result = notificationService.sendTestAlert(trip);
        return ResponseEntity.ok(ApiResponse.ok("Test alert sent successfully", result));
    }

    @PostMapping("/api/trips/{id}/plan/hotel-message/send")
    public ResponseEntity<ApiResponse<NotificationHistoryResponse>> sendHotelMessage(@PathVariable String id) {
        Trip trip = tripAccessService.requireOwner(id);
        NotificationHistoryResponse response = notificationService.sendHotelEmail(trip, trip.getLatestPlan());
        return ResponseEntity.ok(ApiResponse.ok("Hotel update email dispatched successfully", response));
    }
}
