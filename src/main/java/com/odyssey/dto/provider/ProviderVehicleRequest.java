package com.odyssey.dto.provider;

import com.odyssey.model.enums.BookingType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVehicleRequest {

    @NotNull(message = "Mode is required (BUS, TRAIN, FLIGHT)")
    private BookingType mode;

    @NotBlank(message = "Vehicle number is required")
    @Pattern(regexp = "^[A-Za-z0-9 -]{3,20}$", message = "Vehicle number must be 3 to 20 characters (letters, digits, spaces, hyphens)")
    private String vehicleNumber;

    @Size(max = 100, message = "Vehicle type must not exceed 100 characters")
    private String vehicleType;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "Seats total must be at least 1")
    @Max(value = 500, message = "Seats total must not exceed 500")
    private Integer seatsTotal;

    @Size(max = 200, message = "Amenities must not exceed 200 characters")
    private String amenities;

    @Builder.Default
    private Boolean active = true;
}
