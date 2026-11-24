package com.odyssey.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary figures for the trip cost and refund ledger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerSummaryDto {
    private BigDecimal originalCost;
    private BigDecimal replacementCost;
    private BigDecimal estimatedExtraCost;
    private BigDecimal expectedRefunds;
    private BigDecimal recordedRefunds;
    private BigDecimal outstandingRefunds;
    private BigDecimal ledgerTotal;
    private BigDecimal netTotal;
    @Builder.Default
    private String refundReceiptLabel = "User-reported, not verified";
    @Builder.Default
    private String disclaimer = "Estimates are not confirmed amounts";
    private String timingNote;
    private int typicalDaysMin;
    private int typicalDaysMax;
    @JsonProperty("isDemo")
    private boolean isDemo;
}
