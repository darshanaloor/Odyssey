package com.odyssey.dto.provider;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAvailabilityUpdateDto {

    @NotNull(message = "Stay date is required")
    private LocalDate stayDate;

    @NotNull(message = "Closed status is required")
    private Boolean isClosed;
}
