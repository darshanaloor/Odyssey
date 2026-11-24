package com.odyssey.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingVerificationRequest {

    @NotBlank(message = "Type must be HOTEL or CAB")
    private String type; // "HOTEL" or "CAB"

    @NotBlank(message = "Target ID is required")
    private String targetId; // hotel ID or cab service ID

    private String roomTypeId;
    private String targetName;
    private String city;
    private String pickup;
    private String dropoff;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer rooms;
    private Integer nights;
    private String guestName;
    private String tripId;
}
