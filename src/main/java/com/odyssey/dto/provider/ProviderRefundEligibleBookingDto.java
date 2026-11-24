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
public class ProviderRefundEligibleBookingDto {
    private String bookingId;
    private String bookingReference;
    private String type;
    private String serviceDescription;
    private String guestName;
    private BigDecimal originalCost;
    private BigDecimal alreadyRefunded;
    private String status;
    private Boolean cannotHonour;
    private String cannotHonourReason;
    private LocalDateTime startTime;
    private boolean refundIssued;

    public String getId() {
        return bookingId;
    }
}
