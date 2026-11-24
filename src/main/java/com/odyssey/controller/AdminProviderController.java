package com.odyssey.controller;

import com.odyssey.dto.provider.AdminProviderActionRequest;
import com.odyssey.dto.provider.ProviderResponse;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.service.AdminProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
public class AdminProviderController {

    private final AdminProviderService adminProviderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderResponse>>> getProviders(
            @RequestParam(required = false) ProviderStatus status) {
        List<ProviderResponse> list = adminProviderService.getProviders(status);
        return ResponseEntity.ok(ApiResponse.ok("Providers retrieved successfully", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getProvider(@PathVariable String id) {
        ProviderResponse response = adminProviderService.getProvider(id);
        return ResponseEntity.ok(ApiResponse.ok("Provider retrieved successfully", response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ProviderResponse>> approve(@PathVariable String id) {
        ProviderResponse response = adminProviderService.approveProvider(id);
        return ResponseEntity.ok(ApiResponse.ok("Provider approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ProviderResponse>> reject(
            @PathVariable String id,
            @Valid @RequestBody AdminProviderActionRequest request) {
        ProviderResponse response = adminProviderService.rejectProvider(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Provider rejected successfully", response));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<ProviderResponse>> suspend(
            @PathVariable String id,
            @Valid @RequestBody AdminProviderActionRequest request) {
        ProviderResponse response = adminProviderService.suspendProvider(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Provider suspended successfully", response));
    }

    @PostMapping("/{id}/reinstate")
    public ResponseEntity<ApiResponse<ProviderResponse>> reinstate(@PathVariable String id) {
        ProviderResponse response = adminProviderService.reinstateProvider(id);
        return ResponseEntity.ok(ApiResponse.ok("Provider reinstated successfully", response));
    }
}
