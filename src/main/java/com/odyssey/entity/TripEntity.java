package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "traveller_name", nullable = false)
    private String travellerName;

    @Column(name = "traveller_phone", length = 50)
    private String travellerPhone;

    @Column(name = "traveller_email", length = 100)
    private String travellerEmail;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "user_id", length = 64)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;

    @Column(name = "needs_destination_cab", nullable = false)
    @Builder.Default
    private Boolean needsDestinationCab = false;

    @Column(name = "destination_cab_dropoff")
    private String destinationCabDropoff;

    @Column(name = "needs_hotel", nullable = false)
    @Builder.Default
    private Boolean needsHotel = false;

    @Column(name = "notify_email", nullable = false)
    @Builder.Default
    private Boolean notifyEmail = true;

    @Column(name = "notify_sms", nullable = false)
    @Builder.Default
    private Boolean notifySms = true;

    @Column(name = "notify_whatsapp", nullable = false)
    @Builder.Default
    private Boolean notifyWhatsapp = false;

    @Column(name = "has_mobility_assistance", nullable = false)
    @Builder.Default
    private Boolean hasMobilityAssistance = false;

    @Column(name = "has_elderly_traveller", nullable = false)
    @Builder.Default
    private Boolean hasElderlyTraveller = false;

    @Column(name = "has_travelling_with_children", nullable = false)
    @Builder.Default
    private Boolean hasTravellingWithChildren = false;

    @Column(name = "has_medical_needs", nullable = false)
    @Builder.Default
    private Boolean hasMedicalNeeds = false;

    @Column(name = "special_needs_note", length = 300)
    private String specialNeedsNote;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TripConstraintsEntity constraints;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HardCommitmentEntity> hardCommitments = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("startTime ASC")
    @Builder.Default
    private List<BookingEntity> bookings = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DisruptionEntity> disruptions = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecoveryOptionEntity> recoveryOptions = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecoveryPlanEntity> recoveryPlans = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AuditEntryEntity> auditEntries = new ArrayList<>();

    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationEntity> notifications = new ArrayList<>();
}
