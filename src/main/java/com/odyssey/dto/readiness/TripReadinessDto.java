package com.odyssey.dto.readiness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripReadinessDto {
    private String tripId;
    private Double score;
    private ReadinessBand band;
    private boolean reliable;
    private String statusMessage;
    
    @Builder.Default
    private String disclaimer = "Readiness checks your trip data. It does not predict disruptions.";
    
    @Builder.Default
    private List<ReadinessFactorDto> factors = new ArrayList<>();
    
    @Builder.Default
    private List<ReadinessFactorDto> unknownFactors = new ArrayList<>();
    
    @Builder.Default
    private List<ReadinessRecommendationDto> recommendations = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> dataQuality = new HashMap<>();
}

