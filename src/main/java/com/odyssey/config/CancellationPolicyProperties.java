package com.odyssey.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Component
@ConfigurationProperties(prefix = "cancellation.policy")
public class CancellationPolicyProperties {

    private long tier10PlusHours = 240; // 10 days = 240 hours
    private int tier10PlusRefundPercent = 100;

    private long tier3To9MinHours = 72; // 3 days = 72 hours
    private long tier3To9MaxHours = 240; // up to 240 hours
    private int tier3To9RefundPercent = 75;

    private long tier1To2MinHours = 24; // 1 day = 24 hours
    private long tier1To2MaxHours = 72; // up to 72 hours
    private int tier1To2RefundPercent = 25;

    private int sameDayRefundPercent = 0; // less than 24 hours (or past departure)

    public RefundTierResult calculateRefundTier(LocalDateTime cancellationTime, LocalDateTime departureTime) {
        if (cancellationTime == null || departureTime == null) {
            return new RefundTierResult(0, 100,
                    "Cancelled 0 days before departure — 0% refund policy tier applies, 100% non-refundable per cancellation policy", 0);
        }

        Duration duration = Duration.between(cancellationTime, departureTime);
        long hoursBefore = duration.toHours();
        long daysBefore = hoursBefore / 24;

        if (hoursBefore >= tier10PlusHours) {
            return new RefundTierResult(tier10PlusRefundPercent, 100 - tier10PlusRefundPercent,
                    String.format("Cancelled %d days before departure — %d%% refund policy tier applies, %d%% non-refundable per cancellation policy",
                            daysBefore, tier10PlusRefundPercent, 100 - tier10PlusRefundPercent), daysBefore);
        } else if (hoursBefore >= tier3To9MinHours) {
            return new RefundTierResult(tier3To9RefundPercent, 100 - tier3To9RefundPercent,
                    String.format("Cancelled %d days before departure — %d%% refund policy tier applies, %d%% non-refundable per cancellation policy",
                            daysBefore, tier3To9RefundPercent, 100 - tier3To9RefundPercent), daysBefore);
        } else if (hoursBefore >= tier1To2MinHours) {
            return new RefundTierResult(tier1To2RefundPercent, 100 - tier1To2RefundPercent,
                    String.format("Cancelled %d days before departure — %d%% refund policy tier applies, %d%% non-refundable per cancellation policy",
                            daysBefore, tier1To2RefundPercent, 100 - tier1To2RefundPercent), daysBefore);
        } else {
            return new RefundTierResult(sameDayRefundPercent, 100 - sameDayRefundPercent,
                    String.format("Cancelled %d days before departure — %d%% refund policy tier applies, %d%% non-refundable per cancellation policy",
                            Math.max(0, daysBefore), sameDayRefundPercent, 100 - sameDayRefundPercent), Math.max(0, daysBefore));
        }
    }

    public record RefundTierResult(int refundPercent, int deductionPercent, String description, long daysBefore) {}
}
