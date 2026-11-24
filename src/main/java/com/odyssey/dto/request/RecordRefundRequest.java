package com.odyssey.dto.request;

import com.odyssey.model.enums.RefundStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for recording a traveller-reported refund with optional deductions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordRefundRequest {

    @NotBlank(message = "bookingId is required")
    private String bookingId;

    @DecimalMin(value = "0.01", message = "Gross refund amount must be greater than zero")
    private BigDecimal grossAmount;

    /**
     * Backward-compatible alias for grossAmount.
     */
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    private BigDecimal amount;

    @Valid
    @Builder.Default
    private List<RefundDeductionRequest> deductions = new ArrayList<>();

    @Size(max = 60, message = "Reference must not exceed 60 characters")
    private String reference;

    private LocalDate receivedOn;

    private RefundStatus status;

    private String note;

    public BigDecimal resolveGrossAmount() {
        if (grossAmount != null) {
            return grossAmount;
        }
        return amount;
    }
}
