package com.odyssey.dto.response;

import com.odyssey.model.enums.DeductionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Breakdown line for a specific deduction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDeductionDto {
    private DeductionCategory category;
    private BigDecimal amount;
    private String description;
}

