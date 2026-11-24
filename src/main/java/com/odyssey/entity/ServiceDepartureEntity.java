package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "service_departures", uniqueConstraints = {
    @UniqueConstraint(name = "uq_service_travel_date", columnNames = {"service_id", "travel_date"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDepartureEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "seats_available", nullable = false)
    private Integer seatsAvailable;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "SCHEDULED"; // SCHEDULED, DELAYED, CANCELLED

    @Column(name = "delay_minutes", nullable = false)
    @Builder.Default
    private Integer delayMinutes = 0;

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "SEED";

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "status_source", length = 30)
    private String statusSource;

    @Column(name = "schedule_id", length = 64)
    private String scheduleId;

    @Column(name = "alternative_departure_id", length = 64)
    private String alternativeDepartureId;
}
