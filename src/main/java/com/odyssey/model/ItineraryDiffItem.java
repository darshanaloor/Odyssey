package com.odyssey.model;

import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryDiffItem {
    private String bookingId;
    private BookingType type;
    private String provider;
    private LocalDateTime oldStart;
    private LocalDateTime oldEnd;
    private BookingStatus oldStatus;
    private LocalDateTime newStart;
    private LocalDateTime newEnd;
    private BookingStatus newStatus;
    private String changeDescription;
}
