package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityInfo {
    private String key;
    private String name;

    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    @Builder.Default
    private List<String> airportCodes = new ArrayList<>();

    @Builder.Default
    private List<String> stationCodes = new ArrayList<>();

    private double latitude;
    private double longitude;
}
