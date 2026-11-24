package com.odyssey.dto.response;

import com.odyssey.model.enums.DraftKind;
import com.odyssey.model.enums.GeneratedBy;
import com.odyssey.model.enums.MessageStatus;
import com.odyssey.model.enums.RecipientType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerMessageDto {

    private String id;
    private String tripId;
    private RecipientType recipientType;
    private DraftKind draftKind;
    private String bookingId;
    private String subject;
    private String body;
    private String channel;
    private MessageStatus status;
    private String statusLabel;
    private GeneratedBy generatedBy;
    private String generatedByLabel;
    private String recipientMasked;
    private String mailtoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime sentAt;
}

