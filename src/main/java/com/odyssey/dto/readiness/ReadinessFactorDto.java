package com.odyssey.dto.readiness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessFactorDto {
    private String id;
    private String name;
    private Double pointsEarned;
    private Double pointsPossible;
    private String status; // KNOWN or UNKNOWN
    private String explanation;
    private Map<String, Object> dataUsed;
}

