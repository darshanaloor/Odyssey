package com.odyssey.dto.market;

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
public class MarketplaceBookingDto {
    private String id;
    private String bookingReference;
    private String type;
    private String provider;
    private String operator;
    private String vehicleNumber;
    private String from;
    private String to;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal cost;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String source;
    private String seatNumbers;
    private String passengerNames;
    private String tripId;
    private String tripTitle;
    private String departureId;
    private String last4Card;
    private String scheduleLabel; // Generated schedule, not an official timetable
    private String disclaimer;
    private String hotelContactEmail;
    private String providerStatus;
    private Boolean cannotHonour;
    private String cannotHonourReason;
    private LocalDateTime confirmationExpiresAt;
    private String providerConfirmationStatus;
    private MarketplaceBookingDto pendingAddon;
    private String providerBusinessName;
    private String providerContactPhone;
    private String assignedDriverName;
    private String cabServiceContact;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String refundTierApplied;
    private BigDecimal refundAmount;
    private BigDecimal deductionAmount;
    private String verificationNote;
}
