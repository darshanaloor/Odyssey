package com.odyssey.dto.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScheduleDto {
    private String id;
    private String providerId;
    private String vehicleId;
    private String vehicleNumber;
    private BookingType mode;
    private String serviceId;
    private String originCity;
    private String destinationCity;
    private String originStop;
    private String destinationStop;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime departureTime;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;
    private Boolean arrivesNextDay;
    private BigDecimal price;
    private Integer seatsOffered;
    private String daysOfWeek;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Integer reliability;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
