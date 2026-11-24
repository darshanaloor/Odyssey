package com.odyssey.dto.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScheduleRequest {

    @NotBlank(message = "Vehicle ID is required")
    private String vehicleId;

    @NotBlank(message = "Origin city is required")
    private String originCity;

    @NotBlank(message = "Destination city is required")
    private String destinationCity;

    @Size(max = 255, message = "Origin stop must not exceed 255 characters")
    private String originStop;

    @Size(max = 255, message = "Destination stop must not exceed 255 characters")
    private String destinationStop;

    @NotNull(message = "Departure time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime departureTime;

    @NotNull(message = "Arrival time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;

    @Builder.Default
    private Boolean arrivesNextDay = false;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @Min(value = 1, message = "Seats offered must be at least 1")
    @Max(value = 500, message = "Seats offered must not exceed 500")
    private Integer seatsOffered;

    @NotBlank(message = "Days of week is required (e.g. MON,TUE,WED,THU,FRI,SAT,SUN)")
    private String daysOfWeek;

    @JsonFormat(pattern = "[yyyy-MM-dd][dd-MM-yyyy][yyyy/MM/dd][dd/MM/yyyy]")
    private LocalDate validFrom;

    @JsonFormat(pattern = "[yyyy-MM-dd][dd-MM-yyyy][yyyy/MM/dd][dd/MM/yyyy]")
    private LocalDate validTo;

    @Builder.Default
    private Boolean active = true;
}
