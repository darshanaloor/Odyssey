package com.odyssey.controller;

import com.odyssey.dto.request.ChangePasswordRequest;
import com.odyssey.dto.request.LoginRequest;
import com.odyssey.dto.request.RegisterRequest;
import com.odyssey.dto.request.UpdateProfileRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.dto.response.UserResponse;
import com.odyssey.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User registered successfully", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        UserResponse user = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", user));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful", Map.of("status", "logged_out")));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        UserResponse user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<UserResponse>builder()
                            .success(false)
                            .message("Not authenticated")
                            .build());
        }
        return ResponseEntity.ok(ApiResponse.ok("Current user retrieved", user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserResponse user = authService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", user));
    }

    @RequestMapping(value = "/password", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", Map.of("status", "updated")));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCsrf() {
        return ResponseEntity.ok(ApiResponse.ok("CSRF token initialized", Map.of("csrf", "initialized")));
    }
}
