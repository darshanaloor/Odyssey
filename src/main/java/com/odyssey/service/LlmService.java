package com.odyssey.service;

import com.odyssey.dto.response.ParseResult;
import com.odyssey.model.Booking;
import com.odyssey.model.Option;
import com.odyssey.model.Trip;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmService {

    /**
     * Parses confirmation text into structured bookings and tracks unparsed lines.
     */
    ParseResult parseText(String confirmationText);

    /**
     * Extracts bookings from pasted confirmation text into structured Booking objects.
     * Text only; never decides or ranks.
     */
    default List<Booking> extractBookings(String confirmationText) {
        ParseResult result = parseText(confirmationText);
        return result != null ? result.getBookings() : java.util.Collections.emptyList();
    }

    /**
     * Generates a draft message to the hotel based on real itinerary and disruption data.
     */
    String draftHotelMessage(Trip trip, Booking hotelBooking, LocalDateTime newCheckInTime, Option chosenOption);

    /**
     * Synthesizes plain-language trade-off explanations comparing the chosen option
     * to the other alternatives using real figures (costs, arrival times, buffers, reliability).
     */
    String generateTradeOffExplanation(Trip trip, Option chosenOption, List<Option> allOptions);

    /**
     * Rewords a deterministic template draft into polished prose.
     * Must preserve all underlying facts (dates, times, numbers, references, names).
     */
    default String rewordDraft(Trip trip, String templateDraft, String draftKind) {
        return templateDraft;
    }
}
