package com.odyssey.model;

import com.odyssey.model.enums.OptionLabel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryPlan {
    private String id;
    private String tripId;
    private String chosenOptionId;
    private OptionLabel chosenOptionLabel;
    private Booking newLeg;
    private Booking replacementCab;

    @Builder.Default
    private List<ItineraryDiffItem> itineraryDiff = new ArrayList<>();

    private CostBreakdown costBreakdown;

    @Builder.Default
    private List<ActionChecklistItem> checklist = new ArrayList<>();

    private String hotelMessage;
    private String tradeOffExplanation;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}
