package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String channel; // EMAIL, SMS, WHATSAPP
    private String recipient;
    private String recipientMasked;
    private String subject;
    private String alertLevel; // INFO, WARNING, CRITICAL
    private String message;
    private String replyTo;
    private String tripId;
    private String disruptionId;
    private String eventType;
    private String dedupKey;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
