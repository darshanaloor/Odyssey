package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "market.generated")
public class MarketGeneratedProperties {

    private double roadFactor = 1.25;

    private BusConfig bus = new BusConfig();
    private TrainConfig train = new TrainConfig();
    private FlightConfig flight = new FlightConfig();

    @Data
    public static class BusConfig {
        private double maxDistanceKm = 1200.0;
        private double speedKmh = 45.0;
        private double basePrice = 150.0;
        private double perKmPrice = 1.80;
        private double sleeperMinDistanceKm = 350.0;
        private double sleeperSurcharge = 250.0;
        private int seats = 40;
        private String operatorName = "State Transport Express";
    }

    @Data
    public static class TrainConfig {
        private double speedKmh = 55.0;
        private double basePrice = 120.0;
        private double perKmPrice = 1.20;
        private int seats = 72;
        private String operatorName = "Intercity Rail Service";
    }

    @Data
    public static class FlightConfig {
        private double minDistanceKm = 300.0;
        private double speedKmh = 750.0;
        private int groundAllowanceMinutes = 60;
        private double basePrice = 2500.0;
        private double perKmPrice = 4.50;
        private int seats = 180;
        private String operatorName = "Domestic Air Service";
    }
}
