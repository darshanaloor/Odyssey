package com.odyssey.dto.market;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelBookingRequest {

    @NotBlank(message = "Hotel ID is required")
    private String hotelId;

    private String roomTypeId;

    @NotNull(message = "Check-in date is required")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    private LocalDate checkOut;

    @Min(value = 1, message = "At least 1 room is required")
    private int rooms;

    @NotBlank(message = "Guest name is required")
    private String guestName;

    private String tripId; // optional
    private Boolean createTrip; // optional
    private String paymentMethod; // optional CARD | UPI | NETBANKING
}
