package com.odyssey.dto.request;

import com.odyssey.model.enums.DraftKind;
import com.odyssey.model.enums.RecipientType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftMessageRequest {

    @NotNull(message = "recipientType is required")
    private RecipientType recipientType;

    @NotNull(message = "draftKind is required")
    private DraftKind draftKind;

    private String bookingId;
}

