package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = true)
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private UserEntity user;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "from_location", nullable = false)
    private String fromLocation;

    @Column(name = "to_location", nullable = false)
    private String toLocation;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_dependencies", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "depends_on_id", length = 64)
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    @Column(name = "last_status_state", length = 20)
    private String lastStatusState;

    @Column(name = "last_delay_minutes")
    private Integer lastDelayMinutes;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_status_source", length = 20)
    private String lastStatusSource;

    @Column(name = "status_check_error", length = 255)
    private String statusCheckError;

    @Column(name = "last_dedup_key", length = 100)
    private String lastDedupKey;

    @Column(name = "hotel_contact_email", length = 100)
    private String hotelContactEmail;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Column(name = "departure_id", length = 64)
    private String departureId;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "vehicle_number", length = 100)
    private String vehicleNumber;

    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "seat_numbers", length = 255)
    private String seatNumbers;

    @Column(name = "passenger_names", columnDefinition = "TEXT")
    private String passengerNames;

    @Column(name = "payment_status", length = 50)
    private String paymentStatus;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "hotel_room_type_id", length = 64)
    private String hotelRoomTypeId;

    @Column(name = "cannot_honour", nullable = false)
    @Builder.Default
    private Boolean cannotHonour = false;

    @Column(name = "cannot_honour_reason", length = 255)
    private String cannotHonourReason;

    @Column(name = "cannot_honour_at")
    private LocalDateTime cannotHonourAt;

    @Column(name = "confirmation_expires_at")
    private LocalDateTime confirmationExpiresAt;

    @Column(name = "provider_confirmation_status", length = 50)
    private String providerConfirmationStatus;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "assigned_driver_name", length = 100)
    private String assignedDriverName;

    @Column(name = "alternative_departure_id", length = 64)
    private String alternativeDepartureId;

    @Column(name = "verification_note", length = 500)
    private String verificationNote;
}
