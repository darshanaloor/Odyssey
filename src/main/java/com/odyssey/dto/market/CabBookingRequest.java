package com.odyssey.dto.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CabBookingRequest {

    @NotBlank(message = "Cab service ID is required")
    private String cabServiceId;

    @NotBlank(message = "Pickup location is required")
    private String pickup;

    @NotBlank(message = "Dropoff location is required")
    private String dropoff;

    @NotNull(message = "Pickup time is required")
    private LocalDateTime pickupTime;

    private String tripId; // optional
    private Boolean createTrip; // optional
    private String paymentMethod; // optional CARD | UPI | NETBANKING
}
