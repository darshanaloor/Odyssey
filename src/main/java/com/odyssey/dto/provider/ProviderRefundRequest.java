package com.odyssey.dto.provider;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRefundRequest {

    @NotBlank(message = "Booking ID is required")
    private String bookingId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    @Size(min = 10, max = 200, message = "Refund reason must be between 10 and 200 characters")
    private String reason;
}
