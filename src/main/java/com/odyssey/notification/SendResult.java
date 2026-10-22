package com.odyssey.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {
    private SendStatus status;
    private String providerMessageId;
    private String error;

    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
