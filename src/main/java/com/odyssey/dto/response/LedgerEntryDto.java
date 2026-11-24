package com.odyssey.dto.response;

import com.odyssey.model.enums.LedgerBasis;
import com.odyssey.model.enums.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Individual entry in the trip cost and refund ledger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryDto {
    private String id;
    private String bookingId;
    private String bookingDescription;
    private LedgerEntryType type;
    private BigDecimal amount;
    private boolean deduction;
    private LedgerBasis basis;
    private String category;
    private String description;
    private String parentEntryId;
    private String refundStatus;
    private String statusLabel;
    private String note;
    private String reference;
    private LocalDate receivedOn;
    private LocalDateTime createdAt;
}
