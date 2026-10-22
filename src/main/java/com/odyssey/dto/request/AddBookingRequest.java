package com.odyssey.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBookingRequest {

    @NotNull(message = "Booking type is required")
    private BookingType type;

    @NotBlank(message = "Provider cannot be blank")
    private String provider;

    @NotBlank(message = "'from' location is required")
    @JsonProperty("from")
    private String from;

    @NotBlank(message = "'to' location is required")
    @JsonProperty("to")
    private String to;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @NotNull(message = "Cost is required")
    @PositiveOrZero(message = "Cost must be zero or positive")
    private BigDecimal cost;

    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    private String hotelContactEmail;
}
