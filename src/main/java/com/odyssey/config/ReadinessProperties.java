package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "readiness")
public class ReadinessProperties {

    private Weights weights = new Weights();
    private Thresholds thresholds = new Thresholds();
    private Buffers buffers = new Buffers();
    private Slack slack = new Slack();
    private Alternatives alternatives = new Alternatives();
    private Monitoring monitoring = new Monitoring();

    @Data
    public static class Weights {
        private double connectionBuffers = 25.0;
        private double hardCommitmentSlack = 20.0;
        private double bookingCompleteness = 20.0;
        private double recoveryAlternatives = 20.0;
        private double monitoringCoverage = 15.0;
    }

    @Data
    public static class Thresholds {
        private double high = 80.0;
        private double medium = 50.0;
    }

    @Data
    public static class Buffers {
        private Map<String, Integer> minMinutes = new HashMap<>();
        private String aggregation = "MINIMUM";

        public Buffers() {
            minMinutes.put("bus-to-cab", 30);
            minMinutes.put("train-to-hotel", 45);
            minMinutes.put("flight-to-cab", 45);
            minMinutes.put("cab-to-event", 30);
            minMinutes.put("flight-to-flight", 60);
            minMinutes.put("train-to-train", 45);
            minMinutes.put("bus-to-bus", 30);
            minMinutes.put("default", 30);
        }

        public int getRequiredBuffer(String fromType, String toType) {
            if (fromType == null || toType == null) {
                return minMinutes.getOrDefault("default", 30);
            }
            String key = (fromType + "-to-" + toType).toLowerCase();
            return minMinutes.getOrDefault(key, minMinutes.getOrDefault("default", 30));
        }
    }

    @Data
    public static class Slack {
        private int minSlackMinutes = 60;
    }

    @Data
    public static class Alternatives {
        private int targetCount = 3;
        private double estimatedWeight = 0.5;
        private double generatedWeight = 0.7;
        private double realWeight = 1.0;
    }

    @Data
    public static class Monitoring {
        private double simulatedWeight = 0.5;
    }
}

