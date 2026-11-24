package com.odyssey.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class CityInfo {
    private String key;
    private String name;
    private String state;
    private String tier; // METRO, LARGE, MEDIUM, SMALL

    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    private Boolean hasAirport;

    @Builder.Default
    private List<String> airportCodes = new ArrayList<>();

    private Boolean hasRailwayStation;

    @Builder.Default
    private List<String> stationCodes = new ArrayList<>();

    @Builder.Default
    private Boolean hasBusStand = true;

    private double latitude;
    private double longitude;

    public boolean getHasAirport() {
        if (hasAirport != null) return hasAirport;
        return airportCodes != null && !airportCodes.isEmpty();
    }

    public boolean getHasRailwayStation() {
        if (hasRailwayStation != null) return hasRailwayStation;
        return stationCodes != null && !stationCodes.isEmpty();
    }

    public boolean getHasBusStand() {
        return hasBusStand == null || hasBusStand;
    }
}
