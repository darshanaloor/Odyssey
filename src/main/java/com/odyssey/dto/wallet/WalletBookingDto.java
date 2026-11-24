package com.odyssey.dto.wallet;

import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Booking presentation inside the Trip Wallet.
 * Stored booking details are separated from the last known status block.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBookingDto {
    private String id;
    private BookingType type;
    private String provider;
    private String operator;
    private String vehicleNumber;
    private String from;
    private String to;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String seats;
    private String passengerNames;
    private String bookingReference;
    private String source; // MARKETPLACE, TYPED, PARSED
    private String status;
    private BigDecimal cost;
    private String scheduleLabel; // REAL, GENERATED, ESTIMATED
    private LastKnownStatusDto lastKnownStatus;
    private String providerStatus; // e.g. "Provider suspended"
    private String providerContactEmail;
    private String providerContactPhone;
}

