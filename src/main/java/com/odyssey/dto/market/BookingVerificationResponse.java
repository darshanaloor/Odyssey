package com.odyssey.dto.market;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingVerificationResponse {

    private boolean success;
    private String bookingId;
    private String bookingReference;
    private String type;
    private String targetName;
    private String providerEmail;
    private int timeoutSeconds;
    private LocalDateTime expiresAt;
    private String status;
    private String message;
}
