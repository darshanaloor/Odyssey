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
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

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
}
