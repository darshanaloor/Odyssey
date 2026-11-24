package com.odyssey.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
    private String id;
    private BookingType mode;
    private String provider;

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    private LocalDateTime departure;
    private LocalDateTime arrival;
    private BigDecimal cost;
    private Integer transfers;
    private Integer seatsAvailable;
    private Integer reliabilityScore;

    @Builder.Default
    private String source = "INVENTORY";

    private String operator;
    private String vehicleNumber;
    private String serviceDepartureId;
    private String scheduleLabel;
}
