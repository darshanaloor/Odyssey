package com.odyssey.dto.market;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulateDepartureRequest {

    @NotBlank(message = "serviceDepartureId is required")
    private String serviceDepartureId;

    @NotBlank(message = "state is required")
    private String state; // SCHEDULED, ON_TIME, DELAYED, CANCELLED

    private Integer delayMinutes;
}
