package com.odyssey.model;

import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingImpact {
    private String bookingId;
    private BookingType bookingType;
    private String provider;
    private String from;
    private String to;
    private LocalDateTime originalStartTime;
    private LocalDateTime originalEndTime;
    private LocalDateTime projectedStartTime;
    private LocalDateTime projectedEndTime;
    private BookingStatus status;
    private String reason;

    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();
}
