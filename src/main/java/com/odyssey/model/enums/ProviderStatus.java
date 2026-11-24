package com.odyssey.model.enums;

/**
 * Status lifecycle for a provider profile.
 * PENDING: Under review; can create draft listings, but listings hidden from traveller search.
 * APPROVED: Reviewed and active; listings visible in traveller search and recovery.
 * REJECTED: Declined by administrator with reason.
 * SUSPENDED: Suspended by administrator; listings hidden; existing bookings remain valid.
 */
public enum ProviderStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}
