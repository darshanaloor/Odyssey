package com.odyssey.dto.request;

import com.odyssey.model.enums.ShareChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotNull(message = "Channel is required")
    private ShareChannel channel;

    private String recipient;

    @NotNull(message = "Confirmation is required: confirm must be set to true to approve and send a message")
    private Boolean confirm;
}

