package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "provider_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScheduleEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private ProviderVehicleEntity vehicle;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Column(name = "origin_city", nullable = false, length = 50)
    private String originCity;

    @Column(name = "destination_city", nullable = false, length = 50)
    private String destinationCity;

    @Column(name = "origin_stop", nullable = false)
    private String originStop;

    @Column(name = "destination_stop", nullable = false)
    private String destinationStop;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @Column(name = "arrives_next_day", nullable = false)
    @Builder.Default
    private Boolean arrivesNextDay = false;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "seats_offered", nullable = false)
    private Integer seatsOffered;

    @Column(name = "days_of_week", nullable = false, length = 100)
    private String daysOfWeek; // e.g. "MON,TUE,WED,THU,FRI,SAT,SUN"

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "reliability", nullable = false)
    @Builder.Default
    private Integer reliability = 85;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (arrivesNextDay == null) {
            arrivesNextDay = false;
        }
        if (reliability == null) {
            reliability = 85;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
