package com.odyssey.dto.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderOfferAlternativeRequest {

    private String alternativeDepartureId;

    private String alternativeVehicleId;

    private String replacementVehicleNumber;

    private String replacementVehicleType;

    private Integer replacementSeatsTotal;

    private String reason;
}
