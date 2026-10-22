package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {
    @Builder.Default
    private List<CostBreakdownLine> lines = new ArrayList<>();
    private BigDecimal netTripCost;
    private BigDecimal maxBudget;
    private BigDecimal remainingBudget;
}
