package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recovery_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryPlanEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "option_id", length = 64)
    private String optionId;

    @Column(name = "new_leg_id", length = 64)
    private String newLegId;

    @Column(name = "replacement_cab_id", length = 64)
    private String replacementCabId;

    @Column(name = "trade_off_explanation", columnDefinition = "TEXT")
    private String tradeOffExplanation;

    @Column(name = "hotel_message", columnDefinition = "TEXT")
    private String hotelMessage;

    @Column(name = "net_trip_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal netTripCost;

    @Column(name = "max_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxBudget;

    @Column(name = "remaining_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingBudget;

    @Column(name = "cost_breakdown_json", columnDefinition = "TEXT")
    private String costBreakdownJson;

    @Column(name = "itinerary_diff_json", columnDefinition = "TEXT")
    private String itineraryDiffJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "recoveryPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PlanChecklistItemEntity> checklistItems = new ArrayList<>();
}
