package com.odyssey.model;

import com.odyssey.model.enums.BookingType;
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
public class Constraints {

    @NotNull(message = "maxBudget is required")
    @PositiveOrZero(message = "maxBudget must be zero or positive")
    private BigDecimal maxBudget;

    @NotNull(message = "mustArriveBy is required")
    private LocalDateTime mustArriveBy;

    @Builder.Default
    private List<BookingType> preferredModes = new ArrayList<>();

    private Boolean avoidOvernightTravel;

    @Builder.Default
    private List<String> hardCommitments = new ArrayList<>();

    @Builder.Default
    private List<HardCommitment> structuredCommitments = new ArrayList<>();
}
