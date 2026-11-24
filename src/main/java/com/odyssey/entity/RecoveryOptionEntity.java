package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recovery_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryOptionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "best_available_note", length = 255)
    private String bestAvailableNote;

    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private String source = "INVENTORY";

    @Column(name = "constraints_met", columnDefinition = "TEXT")
    private String constraintsMet;

    @Column(name = "constraints_violated", columnDefinition = "TEXT")
    private String constraintsViolated;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "recoveryOption", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OptionLegEntity> legs = new ArrayList<>();
}
