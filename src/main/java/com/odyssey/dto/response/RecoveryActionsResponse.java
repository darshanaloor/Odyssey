package com.odyssey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovery execution tracker summary and ordered actions for a trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryActionsResponse {
    private String tripId;
    private String planId;
    private boolean approved;
    private int doneCount;
    private int skippedCount;
    private int pendingCount;
    private int totalCount;
    private String progressText;
    @Builder.Default
    private List<RecoveryActionDto> actions = new ArrayList<>();
}
