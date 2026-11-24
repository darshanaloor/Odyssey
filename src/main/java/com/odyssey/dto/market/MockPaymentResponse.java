package com.odyssey.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPaymentResponse {
    private boolean success;
    private String transactionId;
    private String paymentMethod;
    private String cardLast4;
    private String message;
    private String disclaimer;
    private MarketplaceBookingDto confirmedBooking;
    private MarketplaceBookingDto pendingAddonBooking;
}
