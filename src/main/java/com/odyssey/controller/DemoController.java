package com.odyssey.controller;

import com.odyssey.dto.response.ApiResponse;
import com.odyssey.model.Trip;
import com.odyssey.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final TripRepository tripRepository;

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Trip>> resetDemo() {
        Trip resetTrip = tripRepository.resetDemoTrip();
        return ResponseEntity.ok(ApiResponse.ok("Demo trip TRIP-DEMO-001 has been restored to its initial clean state", resetTrip));
    }
}
