package com.odyssey.service.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.StatusProperties;
import com.odyssey.model.Booking;
import com.odyssey.model.StatusResult;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.StatusState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LiveFlightStatusProvider implements StatusProvider {

    private static final Pattern FLIGHT_NUMBER_PATTERN = Pattern.compile("([A-Za-z0-9]{2})\\s*[-]?\\s*([0-9]{3,4})");

    private final StatusProperties statusProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LiveFlightStatusProvider(StatusProperties statusProperties, ObjectMapper objectMapper) {
        this.statusProperties = statusProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean supports(Booking booking) {
        return booking != null && booking.getType() == BookingType.FLIGHT;
    }

    @Override
    public StatusResult getStatus(Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        if (booking == null || booking.getType() != BookingType.FLIGHT) {
            return StatusResult.builder()
                    .bookingId(booking != null ? booking.getId() : null)
                    .state(StatusState.UNKNOWN)
                    .source("LIVE")
                    .checkedAt(now)
                    .error("Live status is only supported for FLIGHT bookings")
                    .build();
        }

        String flightNumber = parseFlightNumber(booking.getProvider());
        if (flightNumber == null) {
            log.warn("Could not parse flight number from provider text: {}", booking.getProvider());
            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(StatusState.UNKNOWN)
                    .source("LIVE")
                    .checkedAt(now)
                    .error("Could not parse flight number from: " + booking.getProvider())
                    .build();
        }

        String apiKey = System.getenv("FLIGHT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(StatusState.UNKNOWN)
                    .source("LIVE")
                    .checkedAt(now)
                    .error("FLIGHT_API_KEY environment variable is not configured")
                    .build();
        }

        try {
            String baseUrl = statusProperties.getLive().getBaseUrl();
            String endpointPath = statusProperties.getLive().getEndpointPath();
            String url = baseUrl + endpointPath + "?access_key=" + apiKey + "&flight_iata=" + flightNumber;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                log.warn("Rate limit exceeded (HTTP 429) from live flight status provider. Skipping cycle.");
                return StatusResult.builder()
                        .bookingId(booking.getId())
                        .state(StatusState.UNKNOWN)
                        .source("LIVE")
                        .checkedAt(now)
                        .error("Rate limit exceeded (HTTP 429)")
                        .build();
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Live status check returned HTTP {}: {}", response.statusCode(), response.body());
                return StatusResult.builder()
                        .bookingId(booking.getId())
                        .state(StatusState.UNKNOWN)
                        .source("LIVE")
                        .checkedAt(now)
                        .error("HTTP " + response.statusCode() + " from live provider")
                        .build();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataArray = root.path("data");
            if (dataArray.isArray() && !dataArray.isEmpty()) {
                JsonNode flightNode = dataArray.get(0);
                String flightStatus = flightNode.path("flight_status").asText("").toLowerCase();
                int delay = flightNode.path("departure").path("delay").asInt(0);

                StatusState state;
                if ("cancelled".equals(flightStatus)) {
                    state = StatusState.CANCELLED;
                } else if (delay > 0 || "delayed".equals(flightStatus)) {
                    state = StatusState.DELAYED;
                } else {
                    state = StatusState.ON_TIME;
                }

                return StatusResult.builder()
                        .bookingId(booking.getId())
                        .state(state)
                        .delayMinutes(Math.max(delay, 0))
                        .reportedStart(booking.getStartTime())
                        .reportedEnd(booking.getEndTime() != null && delay > 0 ? booking.getEndTime().plusMinutes(delay) : booking.getEndTime())
                        .source("LIVE")
                        .checkedAt(now)
                        .build();
            }

            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(StatusState.UNKNOWN)
                    .source("LIVE")
                    .checkedAt(now)
                    .error("No flight data found for " + flightNumber)
                    .build();

        } catch (Exception e) {
            log.error("Exception during live flight status check: {}", e.getMessage());
            return StatusResult.builder()
                    .bookingId(booking.getId())
                    .state(StatusState.UNKNOWN)
                    .source("LIVE")
                    .checkedAt(now)
                    .error("API error: " + e.getMessage())
                    .build();
        }
    }

    public static String parseFlightNumber(String providerText) {
        if (providerText == null) return null;
        Matcher matcher = FLIGHT_NUMBER_PATTERN.matcher(providerText);
        if (matcher.find()) {
            return (matcher.group(1) + matcher.group(2)).toUpperCase();
        }
        return null;
    }
}
