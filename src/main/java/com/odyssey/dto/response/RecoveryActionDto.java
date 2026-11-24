package com.odyssey.dto.response;

import com.odyssey.model.enums.RecoveryActionStatus;
import com.odyssey.model.enums.RecoveryActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single persisted recovery execution action derived from an approved plan checklist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryActionDto {
    private String id;
    private RecoveryActionType type;
    private String text;
    private RecoveryActionStatus status;
    private String note;
    private LocalDateTime completedAt;
    private String relatedBookingId;
    private String bookingReference;
    private String contactInfo;
    private boolean canComplete;
    private String disabledReason;
    private int order;
}

