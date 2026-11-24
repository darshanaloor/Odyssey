package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "odyssey.market.provider")
public class ProviderPortalProperties {

    private int horizonDays = 14;
    private int defaultReliability = 85;
    private int maxVehicles = 20;
    private int maxSchedules = 60;
    private int maxHotels = 10;
    private int maxRoomTypesPerHotel = 10;
    private int maxCabServices = 20;
    private int rateLimitPerMinute = 60;

    private BigDecimal cabMinBaseFare = new BigDecimal("50.0");
    private BigDecimal cabMaxBaseFare = new BigDecimal("2000.0");
    private BigDecimal cabMinPerKmRate = new BigDecimal("8.0");
    private BigDecimal cabMaxPerKmRate = new BigDecimal("100.0");

    private Map<String, ModePricingRule> pricingRules = new HashMap<>();
    private Map<String, Integer> maxJourneyMinutes = new HashMap<>();

    public ProviderPortalProperties() {
        // Defaults
        maxJourneyMinutes.put("BUS", 2160); // 36 hours
        maxJourneyMinutes.put("TRAIN", 4320); // 72 hours
        maxJourneyMinutes.put("FLIGHT", 360); // 6 hours
        maxJourneyMinutes.put("CAB", 1440); // 24 hours

        ModePricingRule cabRule = new ModePricingRule();
        cabRule.setMinPerKm(new BigDecimal("1.0"));
        cabRule.setMaxPerKm(new BigDecimal("30.0"));
        cabRule.setMinPrice(new BigDecimal("50.0"));
        cabRule.setMaxPrice(new BigDecimal("25000.0"));
        pricingRules.put("CAB", cabRule);

        ModePricingRule busRule = new ModePricingRule();
        busRule.setMinPerKm(new BigDecimal("0.5"));
        busRule.setMaxPerKm(new BigDecimal("10.0"));
        busRule.setMinPrice(new BigDecimal("50.0"));
        busRule.setMaxPrice(new BigDecimal("15000.0"));
        pricingRules.put("BUS", busRule);

        ModePricingRule trainRule = new ModePricingRule();
        trainRule.setMinPerKm(new BigDecimal("0.2"));
        trainRule.setMaxPerKm(new BigDecimal("8.0"));
        trainRule.setMinPrice(new BigDecimal("30.0"));
        trainRule.setMaxPrice(new BigDecimal("15000.0"));
        pricingRules.put("TRAIN", trainRule);

        ModePricingRule flightRule = new ModePricingRule();
        flightRule.setMinPerKm(new BigDecimal("1.5"));
        flightRule.setMaxPerKm(new BigDecimal("35.0"));
        flightRule.setMinPrice(new BigDecimal("800.0"));
        flightRule.setMaxPrice(new BigDecimal("60000.0"));
        pricingRules.put("FLIGHT", flightRule);
    }

    @Data
    public static class ModePricingRule {
        private BigDecimal minPerKm = new BigDecimal("0.5");
        private BigDecimal maxPerKm = new BigDecimal("10.0");
        private BigDecimal minPrice = new BigDecimal("50.0");
        private BigDecimal maxPrice = new BigDecimal("20000.0");
    }
}
