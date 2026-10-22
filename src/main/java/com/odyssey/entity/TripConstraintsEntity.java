package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_constraints")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripConstraintsEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private TripEntity trip;

    @Column(name = "max_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxBudget;

    @Column(name = "must_arrive_by")
    private LocalDateTime mustArriveBy;

    @Column(name = "avoid_overnight_travel")
    private Boolean avoidOvernightTravel;

    @Column(name = "preferred_modes", length = 255)
    private String preferredModes;
}
