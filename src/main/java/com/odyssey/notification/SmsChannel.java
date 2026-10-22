package com.odyssey.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.model.NotificationMessage;
import com.odyssey.util.NotificationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Component
public class SmsChannel implements NotificationChannel {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String whatsappFrom;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;

    public SmsChannel(
            @Value("${TWILIO_ACCOUNT_SID:#{null}}") String accountSid,
            @Value("${TWILIO_AUTH_TOKEN:#{null}}") String authToken,
            @Value("${TWILIO_FROM_NUMBER:#{null}}") String fromNumber,
            @Value("${TWILIO_WHATSAPP_FROM:#{null}}") String whatsappFrom) {
        this.accountSid = accountSid != null && !accountSid.isBlank() ? accountSid.trim() : null;
        this.authToken = authToken != null && !authToken.isBlank() ? authToken.trim() : null;
        this.fromNumber = fromNumber != null && !fromNumber.isBlank() ? fromNumber.trim() : null;
        this.whatsappFrom = whatsappFrom != null && !whatsappFrom.isBlank() ? whatsappFrom.trim() : null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void setHttpClientForTesting(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getChannelType() {
        return "SMS";
    }

    @Override
    public boolean isActive() {
        return accountSid != null && authToken != null && fromNumber != null;
    }

    public boolean isWhatsAppActive() {
        return isActive() && whatsappFrom != null && !whatsappFrom.isBlank();
    }

    @Override
    public SendResult send(NotificationMessage message) {
        boolean isWhatsApp = "WHATSAPP".equalsIgnoreCase(message.getChannel());
        String channelName = isWhatsApp ? "WhatsApp" : "SMS";

        String rawRecipient = message.getRecipient();
        String normalizedPhone = NotificationMasker.normalizePhone(rawRecipient);
        String maskedPhone = NotificationMasker.maskPhone(rawRecipient);

        if (normalizedPhone == null) {
            log.info("{}Channel: Phone number {} is missing or invalid - skipping", channelName, maskedPhone);
            return SendResult.builder()
                    .status(SendStatus.SKIPPED)
                    .error("Missing or invalid phone number: " + maskedPhone)
                    .sentAt(LocalDateTime.now())
                    .build();
        }

        if (isWhatsApp) {
            if (!isWhatsAppActive()) {
                log.debug("WhatsAppChannel: Inactive (TWILIO_WHATSAPP_FROM not set). Alert recorded as SIMULATED for {}", maskedPhone);
                return SendResult.builder()
                        .status(SendStatus.SIMULATED)
                        .error("Twilio WhatsApp not configured - simulated alert")
                        .sentAt(LocalDateTime.now())
                        .build();
            }
        } else {
            if (!isActive()) {
                log.debug("SmsChannel: Inactive (Twilio credentials not set). Alert recorded as SIMULATED for {}", maskedPhone);
                return SendResult.builder()
                        .status(SendStatus.SIMULATED)
                        .error("Twilio not configured - simulated alert")
                        .sentAt(LocalDateTime.now())
                        .build();
            }
        }

        String toParam = isWhatsApp ? "whatsapp:" + normalizedPhone : normalizedPhone;
        String fromParam = isWhatsApp
                ? (whatsappFrom.startsWith("whatsapp:") ? whatsappFrom : "whatsapp:" + whatsappFrom)
                : fromNumber;

        String body = message.getMessage() != null ? message.getMessage() : "";
        String formBody = "To=" + URLEncoder.encode(toParam, StandardCharsets.UTF_8)
                + "&From=" + URLEncoder.encode(fromParam, StandardCharsets.UTF_8)
                + "&Body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);

        String basicAuth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        String endpoint = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", accountSid);

        Exception lastException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(5))
                        .header("Authorization", "Basic " + basicAuth)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    String sid = extractSid(response.body());
                    log.info("{}Channel: Successfully sent to recipient: {}", channelName, maskedPhone);
                    return SendResult.builder()
                            .status(SendStatus.SENT)
                            .providerMessageId(sid)
                            .sentAt(LocalDateTime.now())
                            .build();
                } else {
                    String errorDetail = extractErrorMessage(response.body(), statusCode);
                    log.warn("{}Channel: Non-2xx response ({}) for {}: {}", channelName, statusCode, maskedPhone, errorDetail);
                    return SendResult.builder()
                            .status(SendStatus.FAILED)
                            .error("Twilio API error HTTP " + statusCode + ": " + errorDetail)
                            .sentAt(LocalDateTime.now())
                            .build();
                }
            } catch (IOException | InterruptedException e) {
                lastException = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.warn("{}Channel: Attempt {} failed for {}: {}", channelName, attempt, maskedPhone, e.getMessage());
                if (attempt == 1) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("{}Channel: Failed to deliver to {} after retries: {}", channelName, maskedPhone,
                lastException != null ? lastException.getMessage() : "Unknown error");

        return SendResult.builder()
                .status(SendStatus.FAILED)
                .error(lastException != null ? lastException.getMessage() : "Failed to send " + channelName)
                .sentAt(LocalDateTime.now())
                .build();
    }

    private String extractSid(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("sid")) {
                return root.get("sid").asText();
            }
        } catch (Exception ignored) {}
        return "TWILIO-" + System.currentTimeMillis();
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {}
        return "HTTP " + statusCode;
    }
}
