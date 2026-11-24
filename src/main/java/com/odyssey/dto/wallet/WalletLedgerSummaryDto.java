package com.odyssey.dto.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * High-level financial summary line for the Trip Wallet header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLedgerSummaryDto {
    private BigDecimal netCost;
    private BigDecimal originalCost;
    private BigDecimal replacementCost;
    private BigDecimal outstandingRefunds;
    private String refundStatus;
    private String timingNote;
    @JsonProperty("isDemo")
    private boolean isDemo;
}

