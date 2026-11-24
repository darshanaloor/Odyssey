package com.odyssey.entity;

import com.odyssey.model.enums.RecoveryActionStatus;
import com.odyssey.model.enums.RecoveryActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "trip_id", length = 64)
    private String tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50)
    private RecoveryActionType type;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RecoveryActionStatus status = RecoveryActionStatus.PENDING;

    @Column(name = "done", nullable = false)
    @Builder.Default
    private Boolean done = false;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "related_booking_id", length = 64)
    private String relatedBookingId;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;

    @Column(name = "contact_info", length = 255)
    private String contactInfo;

    @Column(name = "item_order", nullable = false)
    @Builder.Default
    private Integer itemOrder = 0;
}
