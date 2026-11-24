package com.odyssey.controller;

import com.odyssey.dto.request.DraftMessageRequest;
import com.odyssey.dto.request.EditMessageRequest;
import com.odyssey.dto.request.SendMessageRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.TravellerMessageDto;
import com.odyssey.service.TravellerMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Traveller Communication Assistant:
 * drafting, editing, approving, and sending communications to hotels, operators, family, etc.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/messages")
@RequiredArgsConstructor
public class TravellerMessageController {

    private final TravellerMessageService travellerMessageService;

    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<TravellerMessageDto>> draftMessage(
            @PathVariable String tripId,
            @Valid @RequestBody DraftMessageRequest request) {
        TravellerMessageDto dto = travellerMessageService.draftMessage(tripId, request);
        return ResponseEntity.ok(ApiResponse.ok("Message draft created successfully", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TravellerMessageDto>> editMessage(
            @PathVariable String tripId,
            @PathVariable String id,
            @Valid @RequestBody EditMessageRequest request) {
        TravellerMessageDto dto = travellerMessageService.editMessage(tripId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Message updated successfully", dto));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<TravellerMessageDto>> sendMessage(
            @PathVariable String tripId,
            @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request) {
        TravellerMessageDto dto = travellerMessageService.sendMessage(tripId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Message dispatched successfully", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TravellerMessageDto>>> getMessages(@PathVariable String tripId) {
        List<TravellerMessageDto> messages = travellerMessageService.getMessages(tripId);
        return ResponseEntity.ok(ApiResponse.ok("Traveller messages retrieved successfully", messages));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TravellerMessageDto>> getMessage(
            @PathVariable String tripId,
            @PathVariable String id) {
        TravellerMessageDto dto = travellerMessageService.getMessage(tripId, id);
        return ResponseEntity.ok(ApiResponse.ok("Traveller message retrieved successfully", dto));
    }
}

