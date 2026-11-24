package com.odyssey.entity;

import com.odyssey.model.enums.LedgerBasis;
import com.odyssey.model.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_ledger_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripLedgerEntryEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private LedgerEntryType type;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 50)
    private LedgerBasis basis;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "parent_entry_id", length = 64)
    private String parentEntryId;

    @Column(name = "refund_status", length = 50)
    private String refundStatus;

    @Column(name = "received_on")
    private LocalDate receivedOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

