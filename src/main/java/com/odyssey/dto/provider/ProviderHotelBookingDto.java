package com.odyssey.dto.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sanitized booking details for provider manifest view.
 * Privacy rule: strictly NO traveller email, phone, card data, or trip details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderHotelBookingDto {
    private String bookingId;
    private String bookingReference;
    private String guestName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int rooms;
    private String roomTypeName;
    private String status;
    private Boolean cannotHonour;
    private String cannotHonourReason;
    private BigDecimal cost;
}
