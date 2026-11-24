package com.odyssey.dto.request;

import com.odyssey.model.enums.DeductionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Deduction applied against a gross refund.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDeductionRequest {

    @NotNull(message = "Deduction category is required")
    private DeductionCategory category;

    @NotNull(message = "Deduction amount is required")
    @DecimalMin(value = "0.01", message = "Deduction amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Deduction description is required")
    @Size(min = 5, max = 200, message = "Deduction description must be between 5 and 200 characters")
    private String description;
}

