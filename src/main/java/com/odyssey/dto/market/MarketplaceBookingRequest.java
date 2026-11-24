package com.odyssey.dto.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketplaceBookingRequest {

    @NotNull(message = "Service departure ID is required")
    private String serviceDepartureId;

    @NotEmpty(message = "At least one passenger is required")
    @Size(max = 6, message = "Maximum 6 passengers allowed per booking")
    @Valid
    private List<PassengerDto> passengers;

    private String tripId; // optional
    private Boolean createTrip; // optional

    private CabBookingRequest cabAddon; // optional Phase 10A / 18C add-on
    private HotelBookingRequest hotelAddon; // optional Phase 10A / 18C add-on
    private String paymentMethod; // optional CARD | UPI | NETBANKING
}
