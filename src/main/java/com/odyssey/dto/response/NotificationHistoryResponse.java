package com.odyssey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHistoryResponse {
    private String id;
    private String tripId;
    private String disruptionId;
    private String eventType;
    private String channel;
    private String recipientMasked;
    private String subject;
    private String body;
    private String status;
    private String error;
    private String providerMessageId;
    private LocalDateTime createdAt;
}
