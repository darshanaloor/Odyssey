package com.odyssey.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplacementPreviewResponse {
    private String departureId;
    private String operator;
    private String vehicleNumber;
    private String mode;
    private String origin;
    private String destination;
    private String originCity;
    private String destinationCity;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private int seatsAvailable;
    private int seatsLeft;
    private int passengerCount;
    private List<String> passengers;
    private List<String> assignedSeats;
    private BigDecimal unitPrice;
    private BigDecimal totalCost;
    private BigDecimal replacementCost;
    private BigDecimal refundAmount;
    private BigDecimal netCost;
    private String scheduleLabel;
    private String disclaimer;
}
