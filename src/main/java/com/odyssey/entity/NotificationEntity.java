package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "disruption_id", length = 64)
    private String disruptionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient_masked", nullable = false, length = 100)
    private String recipientMasked;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "dedup_key", length = 150)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
