package com.odyssey.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    private String id;
    private BookingType type;
    private String provider;

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal cost;
    private BookingStatus status;

    @Builder.Default
    private String source = "INVENTORY";

    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    private String lastStatusState;
    private Integer lastDelayMinutes;
    private LocalDateTime lastCheckedAt;
    private String lastStatusSource;
    private String statusCheckError;
    private String lastDedupKey;
    private String hotelContactEmail;
    private String bookingReference;
    private String userId;
    private String serviceId;
    private String departureId;
    private String providerId;
    private String vehicleNumber;
    private String operator;
    private String seatNumbers;
    private String passengerNames;
    private String paymentStatus;
    private String paymentMethod;
    private Integer seatsAvailable;
    private String scheduleLabel;
    private String hotelRoomTypeId;
    private Boolean cannotHonour;
    private String cannotHonourReason;
    private LocalDateTime cannotHonourAt;
    private String providerStatus;
    private LocalDateTime confirmationExpiresAt;
    private String providerConfirmationStatus;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String assignedDriverName;
    private String cabServiceContact;
    private String providerBusinessName;
    private String providerContactPhone;
    private String alternativeDepartureId;
}
