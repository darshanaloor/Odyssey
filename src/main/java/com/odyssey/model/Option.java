package com.odyssey.model;

import com.odyssey.model.enums.OptionLabel;
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
public class Option {
    private String id;
    private OptionLabel label;

    @Builder.Default
    private List<Booking> legs = new ArrayList<>();

    private BigDecimal totalCost;
    private LocalDateTime arrivalTime;
    private Double score;

    @Builder.Default
    private List<String> constraintsMet = new ArrayList<>();

    @Builder.Default
    private List<String> constraintsViolated = new ArrayList<>();

    @Builder.Default
    private String source = "INVENTORY";

    private String explanation;
    private String bestAvailableNote;
}
