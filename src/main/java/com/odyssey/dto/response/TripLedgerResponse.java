package com.odyssey.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Full cost and refund ledger response for a trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripLedgerResponse {
    private String tripId;
    private String planId;
    private LedgerSummaryDto summary;
    private BigDecimal ledgerTotal;
    private BigDecimal netTotal;
    @Builder.Default
    private List<LedgerEntryDto> entries = new ArrayList<>();
    @Builder.Default
    private List<BookingRefundSummaryDto> bookingRefunds = new ArrayList<>();
    private String timingNote;
    private int typicalDaysMin;
    private int typicalDaysMax;
    @JsonProperty("isDemo")
    private boolean isDemo;
}
