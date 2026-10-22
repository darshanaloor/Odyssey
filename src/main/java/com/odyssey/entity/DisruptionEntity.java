package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "disruptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisruptionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "disruption_time", nullable = false)
    private LocalDateTime disruptionTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "actor", length = 50)
    private String actor;
}
