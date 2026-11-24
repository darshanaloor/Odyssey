package com.odyssey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of sharing a refund summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareRefundResponse {
    private String channel;
    private String recipientMasked;
    private String status;
    private String statusLabel;
    private String mailtoUrl;
    private String message;
    private String content;
    private String error;
    private LocalDateTime timestamp;
}

