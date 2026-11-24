package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id", nullable = false)
    private OperatorEntity operator;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode; // BUS, TRAIN, FLIGHT

    @Column(name = "vehicle_number", nullable = false, length = 100)
    private String vehicleNumber;

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
    private Boolean arrivesNextDay;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "seats_total", nullable = false)
    private Integer seatsTotal;

    @Column(name = "reliability_score", nullable = false)
    private Integer reliabilityScore;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "SEED";

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "schedule_id", length = 64)
    private String scheduleId;
}
