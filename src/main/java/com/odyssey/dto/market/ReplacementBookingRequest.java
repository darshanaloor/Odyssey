package com.odyssey.dto.market;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplacementBookingRequest {

    @NotBlank(message = "tripId is required")
    private String tripId;

    @NotBlank(message = "oldBookingId is required")
    private String oldBookingId;

    @NotBlank(message = "serviceDepartureId is required")
    private String serviceDepartureId;

    private List<PassengerDto> passengers;
    private List<String> seatNumbers;
}
