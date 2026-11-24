package com.odyssey.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplacementBookingResponse {
    private String tripId;
    private String oldBookingId;
    private String oldBookingStatus;
    private String oldPaymentStatus;
    private MarketplaceBookingDto newBooking;
    private BigDecimal refundAmount;
    private BigDecimal replacementCost;
    private BigDecimal netCost;
    private List<String> assignedSeats;
    private String disclaimer;
    private String note;
}
