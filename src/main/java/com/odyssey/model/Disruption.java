package com.odyssey.model;

import com.odyssey.model.enums.DisruptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Disruption {
    private String id;
    private DisruptionType type;
    private String bookingId;
    private Integer delayMinutes;
    private String reason;
    private LocalDateTime disruptionTime;
    private LocalDateTime reportedAt;
    private String source;
    private String actor;
}
