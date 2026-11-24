package com.odyssey.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for completing, skipping, or reopening a tracked recovery action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryActionNoteRequest {
    private String note;
}

