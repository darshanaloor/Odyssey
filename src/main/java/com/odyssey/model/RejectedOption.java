package com.odyssey.model;

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
public class RejectedOption {
    private String id;
    private BookingType mode;
    private String provider;
    private BigDecimal cost;
    private LocalDateTime departure;
    private LocalDateTime arrival;
    private String reason;
    private String blockedBy;
}
