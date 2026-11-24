package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "option_legs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionLegEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private RecoveryOptionEntity recoveryOption;

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

    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private String source = "INVENTORY";

    @Column(name = "leg_order", nullable = false)
    @Builder.Default
    private Integer legOrder = 0;

    @Column(name = "service_departure_id", length = 64)
    private String serviceDepartureId;

    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "vehicle_number", length = 100)
    private String vehicleNumber;

    @Column(name = "seats_available")
    private Integer seatsAvailable;

    @Column(name = "schedule_label", length = 100)
    private String scheduleLabel;
}
