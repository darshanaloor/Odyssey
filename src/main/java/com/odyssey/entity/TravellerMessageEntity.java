package com.odyssey.entity;

import com.odyssey.model.enums.DraftKind;
import com.odyssey.model.enums.GeneratedBy;
import com.odyssey.model.enums.MessageStatus;
import com.odyssey.model.enums.RecipientType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "traveller_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravellerMessageEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 30, nullable = false)
    private RecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_kind", length = 50, nullable = false)
    private DraftKind draftKind;

    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @Column(name = "subject", length = 255, nullable = false)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "channel", length = 20)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private MessageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", length = 20, nullable = false)
    private GeneratedBy generatedBy;

    @Column(name = "recipient_masked", length = 100)
    private String recipientMasked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}

