package com.odyssey.dto.wallet;

import com.odyssey.model.Constraints;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level response for GET /api/trips/{tripId}/wallet.
 * Excludes sensitive fields (passwords, sessions, cards, tokens, emails, phones).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripWalletDto {
    private String tripId;
    private String title;
    private String travellerName;
    private Constraints constraints;
    private WalletLedgerSummaryDto ledgerSummary;
    @Builder.Default
    private List<WalletBookingDto> bookings = new ArrayList<>();

    // Phase 17: Traveller Needs (stripped for non-owners)
    private Boolean hasMobilityAssistance;
    private Boolean hasElderlyTraveller;
    private Boolean hasTravellingWithChildren;
    private Boolean hasMedicalNeeds;
    private String specialNeedsNote;
    private String emergencyContactName;
    private String emergencyContactPhone;
}

