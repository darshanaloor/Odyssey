package com.odyssey.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulateStatusRequest {
    private String tripId;
    private String bookingId;
    private String state;
    private Integer delayMinutes;
}
