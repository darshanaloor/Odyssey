package com.odyssey.dto.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRefundResponse {
    private String ledgerEntryId;
    private String bookingId;
    private String tripId;
    private BigDecimal amount;
    private String basis;
    private String reason;
    private String reference;
    private LocalDateTime createdAt;
    private String message;
}
