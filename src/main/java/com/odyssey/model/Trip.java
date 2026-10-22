package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {
    private String id;
    private String title;
    private String description;
    private String status;

    private String travellerName;
    private String travellerPhone;
    private String travellerEmail;
    private String userId;

    @Builder.Default
    private Boolean needsDestinationCab = false;

    private String destinationCabDropoff;

    @Builder.Default
    private Boolean needsHotel = false;

    @Builder.Default
    private Boolean notifyEmail = true;

    @Builder.Default
    private Boolean notifySms = true;

    @Builder.Default
    private Boolean notifyWhatsapp = false;

    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    private Constraints constraints;

    @Builder.Default
    private List<Disruption> activeDisruptions = new ArrayList<>();

    @Builder.Default
    private List<HardCommitment> hardCommitments = new ArrayList<>();

    @Builder.Default
    private List<Option> options = new ArrayList<>();

    @Builder.Default
    private List<AuditEntry> auditLog = new ArrayList<>();

    private TripImpactReport latestImpact;
    private RecoveryPlan latestPlan;
    private String recoveryNote;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
