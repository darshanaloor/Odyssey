package com.odyssey.dto.response;

import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffLegDto {
    private String bookingId;
    private BookingType type;
    private String provider;
    private String from;
    private String to;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status;
    private String bookingReference;
    private boolean booked;
    private List<HandoffLink> links;
    private String scheduleNote;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandoffLink {
        private String label;
        private String url;
        private boolean prefilled;
    }
}
