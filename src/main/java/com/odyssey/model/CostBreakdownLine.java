package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdownLine {
    private String label;
    private BigDecimal amount;
    private boolean isDeduction;
    private String assumptionNote;
}
