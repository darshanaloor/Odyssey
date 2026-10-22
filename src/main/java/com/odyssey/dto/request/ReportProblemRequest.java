package com.odyssey.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportProblemRequest {
    private String type; // "CANCELLED" or "DELAYED"
    private Integer delayMinutes;
    private String note;
}
