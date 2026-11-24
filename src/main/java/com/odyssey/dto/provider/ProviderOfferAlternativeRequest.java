package com.odyssey.dto.provider;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderOfferAlternativeRequest {

    @NotBlank(message = "Alternative departure ID is required")
    private String alternativeDepartureId;

    private String reason;
}
