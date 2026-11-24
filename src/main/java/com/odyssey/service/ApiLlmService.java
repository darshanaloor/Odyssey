package com.odyssey.service;

import com.odyssey.dto.response.ParseResult;
import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.Option;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Primary
@Service("apiLlmService")
public class ApiLlmService implements LlmService {

    private final RuleBasedLlmService ruleBasedLlmService;
    private final String apiKey;

    public ApiLlmService(
            @Qualifier("ruleBasedLlmService") RuleBasedLlmService ruleBasedLlmService,
            @Value("${LLM_API_KEY:${odyssey.llm.api-key:}}") String apiKey) {
        this.ruleBasedLlmService = ruleBasedLlmService;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    private boolean isApiKeyConfigured() {
        return !apiKey.isBlank();
    }

    private void recordFallback(Trip trip, String operation, Exception ex) {
        log.warn("LLM API call failed or timed out during '{}': {}. Falling back to RuleBasedLlmService.",
                operation, ex.getMessage());
        if (trip != null) {
            trip.getAuditLog().add(AuditEntry.builder()
                    .id(IdGenerator.generate("AUD"))
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.SYSTEM)
                    .action("LLM_API_FALLBACK")
                    .details(String.format("LLM API call failed for %s (%s); fell back to RuleBasedLlmService.",
                            operation, ex.getMessage()))
                    .build());
        }
    }

    @Override
    public ParseResult parseText(String confirmationText) {
        if (!isApiKeyConfigured()) {
            return ruleBasedLlmService.parseText(confirmationText);
        }
        try {
            log.info("Invoking external LLM API for booking extraction with configured key");
            throw new UnsupportedOperationException("External LLM API endpoint unavailable in local environment");
        } catch (Exception ex) {
            log.warn("LLM API extraction failed: {}. Falling back to RuleBasedLlmService.", ex.getMessage());
            return ruleBasedLlmService.parseText(confirmationText);
        }
    }

    @Override
    public List<Booking> extractBookings(String confirmationText) {
        return parseText(confirmationText).getBookings();
    }

    @Override
    public String draftHotelMessage(Trip trip, Booking hotelBooking, LocalDateTime newCheckInTime, Option chosenOption) {
        if (!isApiKeyConfigured()) {
            return ruleBasedLlmService.draftHotelMessage(trip, hotelBooking, newCheckInTime, chosenOption);
        }
        try {
            log.info("Invoking external LLM API for hotel message generation with configured key");
            throw new UnsupportedOperationException("External LLM API endpoint unavailable in local environment");
        } catch (Exception ex) {
            recordFallback(trip, "draftHotelMessage", ex);
            return ruleBasedLlmService.draftHotelMessage(trip, hotelBooking, newCheckInTime, chosenOption);
        }
    }

    @Override
    public String generateTradeOffExplanation(Trip trip, Option chosenOption, List<Option> allOptions) {
        if (!isApiKeyConfigured()) {
            return ruleBasedLlmService.generateTradeOffExplanation(trip, chosenOption, allOptions);
        }
        try {
            log.info("Invoking external LLM API for trade-off explanation with configured key");
            throw new UnsupportedOperationException("External LLM API endpoint unavailable in local environment");
        } catch (Exception ex) {
            recordFallback(trip, "generateTradeOffExplanation", ex);
            return ruleBasedLlmService.generateTradeOffExplanation(trip, chosenOption, allOptions);
        }
    }

    @Override
    public String rewordDraft(Trip trip, String templateDraft, String draftKind) {
        if (!isApiKeyConfigured()) {
            return ruleBasedLlmService.rewordDraft(trip, templateDraft, draftKind);
        }
        try {
            log.info("Invoking external LLM API for message rewording with configured key");
            return java.util.concurrent.CompletableFuture.<String>supplyAsync(() -> {
                throw new UnsupportedOperationException("External LLM API endpoint unavailable in local environment");
            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            recordFallback(trip, "rewordDraft", ex);
            return ruleBasedLlmService.rewordDraft(trip, templateDraft, draftKind);
        }
    }
}
