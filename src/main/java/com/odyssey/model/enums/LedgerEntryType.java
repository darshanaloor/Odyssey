package com.odyssey.model.enums;

/**
 * Type of entry in the cost and refund ledger.
 */
public enum LedgerEntryType {
    ORIGINAL_COST,
    REPLACEMENT_COST,
    EXTRA_COST,
    REFUND_EXPECTED,
    REFUND_RECORDED,
    REFUND,
    REFUND_DEDUCTION
}
