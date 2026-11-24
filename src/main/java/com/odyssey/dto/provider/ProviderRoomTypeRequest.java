package com.odyssey.dto.provider;

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
public class ProviderRoomTypeRequest {

    @NotBlank(message = "Room type name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Max guests is required")
    @Min(value = 1, message = "Max guests must be at least 1")
    @Max(value = 10, message = "Max guests cannot exceed 10")
    private Integer maxGuests;

    @NotNull(message = "Price per night is required")
    @DecimalMin(value = "0.01", message = "Price per night must be greater than 0")
    private BigDecimal pricePerNight;

    @NotNull(message = "Rooms total is required")
    @Min(value = 1, message = "Rooms total must be at least 1")
    @Max(value = 200, message = "Rooms total cannot exceed 200")
    private Integer roomsTotal;

    @Size(max = 200, message = "Amenities must not exceed 200 characters")
    private String amenities;

    @Builder.Default
    private Boolean active = true;
}
