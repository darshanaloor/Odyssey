package com.odyssey.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookBookingRequest {
    private String bookingReference;
    private String paymentMethod;
    private BigDecimal paidAmount;
}
