package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "odyssey.recovery")
public class RecoveryProperties {
    private BigDecimal replacementCabCost = new BigDecimal("400.00");
    private int replacementCabBufferMinutes = 30;
    private boolean assumeFullRefundFlight = true;
    private boolean assumeFullRefundCab = true;
    private java.time.LocalDateTime demoDisruptionTime = java.time.LocalDateTime.of(2026, 10, 21, 9, 0, 0);
    private int stationBufferMinutes = 45;
    private double syntheticReliabilityScore = 60.0;
    private java.time.LocalTime dayTravelStart = java.time.LocalTime.of(6, 0);
    private java.time.LocalTime dayTravelEnd = java.time.LocalTime.of(21, 0);
    private int searchWindowDays = 2;
    private int delayThresholdMinutes = 30;
    private java.time.LocalTime quietHoursStart;
    private java.time.LocalTime quietHoursEnd;

    public void setQuietHoursStart(String start) {
        if (start != null && !start.isBlank() && !"null".equalsIgnoreCase(start.trim()) && !"#{null}".equalsIgnoreCase(start.trim())) {
            this.quietHoursStart = java.time.LocalTime.parse(start.trim());
        } else {
            this.quietHoursStart = null;
        }
    }

    public void setQuietHoursStart(java.time.LocalTime start) {
        this.quietHoursStart = start;
    }

    public void setQuietHoursEnd(String end) {
        if (end != null && !end.isBlank() && !"null".equalsIgnoreCase(end.trim()) && !"#{null}".equalsIgnoreCase(end.trim())) {
            this.quietHoursEnd = java.time.LocalTime.parse(end.trim());
        } else {
            this.quietHoursEnd = null;
        }
    }

    public void setQuietHoursEnd(java.time.LocalTime end) {
        this.quietHoursEnd = end;
    }
}
