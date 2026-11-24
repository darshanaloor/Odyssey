package com.odyssey.dto.readiness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessRecommendationDto {
    private String factorId;
    private String message;
}

