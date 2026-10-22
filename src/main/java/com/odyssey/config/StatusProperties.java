package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "odyssey.status")
public class StatusProperties {
    private int checkIntervalMinutes = 5;
    private int lookaheadHours = 24;
    private Live live = new Live();

    @Data
    public static class Live {
        private String baseUrl = "https://api.aviationstack.com/v1";
        private String endpointPath = "/flights";
    }
}
