package com.odyssey.model;

import com.odyssey.model.enums.RecoveryActionStatus;
import com.odyssey.model.enums.RecoveryActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionChecklistItem {
    private String id;
    private RecoveryActionType type;
    private String text;
    @Builder.Default
    private RecoveryActionStatus status = RecoveryActionStatus.PENDING;
    @Builder.Default
    private boolean done = false;
    private String note;
    private LocalDateTime completedAt;
    private String relatedBookingId;
    private String bookingReference;
    private String contactInfo;
}
