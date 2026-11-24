package com.odyssey.dto.response;

import com.odyssey.model.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-booking refund status and breakdown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRefundSummaryDto {
    private String bookingId;
    private String bookingDescription;
    private BigDecimal originalCost;
    private BigDecimal grossRefund;
    @Builder.Default
    private List<RefundDeductionDto> deductions = new ArrayList<>();
    private BigDecimal totalDeductions;
    private BigDecimal netRefund;
    private BigDecimal reduction;
    private RefundStatus status;
    private String statusLabel;
    private BigDecimal outstandingAmount;
    private String timingNote;
}

