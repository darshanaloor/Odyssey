package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for refund timings and disclosure notes.
 */
@Data
@Component
@ConfigurationProperties(prefix = "refund")
public class RefundProperties {
    private int typicalDaysMin = 5;
    private int typicalDaysMax = 7;
    private String note = "Refunds are not instant. They usually take a few working days (typically 5 to 7). This is an estimate, not a guarantee, and depends on the operator and the payment method. Odyssey cannot confirm when a refund is received.";
    private String demoNote = "Demo refund. No real money is moved. Demo marketplace. No real ticket is issued and no money was charged.";
}

