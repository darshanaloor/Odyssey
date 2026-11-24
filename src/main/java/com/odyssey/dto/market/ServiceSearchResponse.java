package com.odyssey.dto.market;

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
public class ServiceSearchResponse {
    private String id;
    private String departureId;
    private String operator;
    private String vehicleNumber;
    private String mode;
    private String originCity;
    private String destinationCity;
    private String originStop;
    private String destinationStop;
    private LocalDate departureDate;
    private LocalTime departureTime;
    private LocalDate arrivalDate;
    private LocalTime arrivalTime;
    private String duration;
    private BigDecimal price;
    private int seatsLeft;
    private int reliabilityScore;
    private String status;
    private boolean bookable;
    private String source; // SEED or GENERATED
    private String scheduleLabel; // Generated schedule, not an official timetable
    private String sourceLabel; // Seeded schedule vs Generated schedule
    private String providerBusinessName;
    private String disclaimer;
}
