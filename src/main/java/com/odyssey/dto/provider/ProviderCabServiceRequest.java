package com.odyssey.dto.provider;

import com.odyssey.model.enums.VehicleCategory;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCabServiceRequest {

    @NotBlank(message = "City is required")
    private String city;

    @NotNull(message = "Vehicle category is required (HATCHBACK, SEDAN, SUV, TEMPO_TRAVELLER)")
    private VehicleCategory vehicleCategory;

    @NotNull(message = "Base fare is required")
    @DecimalMin(value = "0.01", message = "Base fare must be greater than 0")
    private BigDecimal baseFare;

    @NotNull(message = "Per km rate is required")
    @DecimalMin(value = "0.01", message = "Per km rate must be greater than 0")
    private BigDecimal perKmRate;

    @NotNull(message = "Seats is required")
    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 20, message = "Seats cannot exceed 20")
    private Integer seats;

    @Size(max = 100, message = "Operating hours must not exceed 100 characters")
    private String operatingHours;

    @Pattern(regexp = "^$|^[A-Za-z0-9 -]{3,20}$", message = "Vehicle registration number must be 3-20 alphanumeric characters, hyphens or spaces")
    private String vehicleNumber;

    @Builder.Default
    private Boolean active = true;
}
