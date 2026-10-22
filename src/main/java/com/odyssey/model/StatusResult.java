package com.odyssey.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odyssey.model.enums.StatusState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusResult {
    private String bookingId;
    private StatusState state;
    private Integer delayMinutes;
    private LocalDateTime reportedStart;
    private LocalDateTime reportedEnd;
    private String source;
    private LocalDateTime checkedAt;
    private String error;
}
