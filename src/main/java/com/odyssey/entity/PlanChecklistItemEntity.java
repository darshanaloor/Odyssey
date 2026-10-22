package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_checklist_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanChecklistItemEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private RecoveryPlanEntity recoveryPlan;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "done", nullable = false)
    @Builder.Default
    private Boolean done = false;

    @Column(name = "item_order", nullable = false)
    @Builder.Default
    private Integer itemOrder = 0;
}
