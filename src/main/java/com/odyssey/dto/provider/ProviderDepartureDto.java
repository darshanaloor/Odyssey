package com.odyssey.dto.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDepartureDto {
    private String id;
    private String scheduleId;
    private LocalDate travelDate;
    private String vehicleNumber;
    private BookingType mode;
    private String originCity;
    private String destinationCity;
    private String originStop;
    private String destinationStop;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime departureTime;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;
    private Integer seatsAvailable;
    private Integer seatsSold;
    private Integer seatsTotal;
    private String status;
    private Integer delayMinutes;
    private String statusSource;
}
