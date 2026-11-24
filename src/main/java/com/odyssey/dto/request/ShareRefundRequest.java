package com.odyssey.dto.request;

import com.odyssey.model.enums.ShareChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for sharing a refund summary via EMAIL, SMS, MAILTO, or COPY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareRefundRequest {

    @NotNull(message = "Channel is required (EMAIL, SMS, MAILTO, COPY)")
    private ShareChannel channel;

    private String recipient;

    @NotNull(message = "confirm must be true")
    private Boolean confirm;
}

