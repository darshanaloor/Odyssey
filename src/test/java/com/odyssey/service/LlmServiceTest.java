package com.odyssey.service;

import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.Option;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.OptionLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmServiceTest {

    private RuleBasedLlmService ruleBasedLlmService;
    private ApiLlmService apiLlmServiceWithNoKey;
    private ApiLlmService apiLlmServiceWithKey;

    @BeforeEach
    void setUp() {
        ruleBasedLlmService = new RuleBasedLlmService();
        apiLlmServiceWithNoKey = new ApiLlmService(ruleBasedLlmService, "");
        apiLlmServiceWithKey = new ApiLlmService(ruleBasedLlmService, "sk-test-dummy-key-12345");
    }

    @Test
    @DisplayName("RuleBasedLlmService: extracts structured bookings from raw confirmation text without ranking or deciding")
    void testExtractBookingsFromPastedText() {
        String confirmationEmail =
                "Flight Confirmation:\n" +
                "Airline: IndiGo 6E-534\n" +
                "From: BLR (Kempegowda International Airport, Bengaluru)\n" +
                "To: IXE (Mangaluru International Airport)\n" +
                "Departure: 2026-10-21 10:30\n" +
                "Arrival: 2026-10-21 11:45\n" +
                "Fare: Rs 4500.00\n\n" +
                "Hotel Reservation:\n" +
                "Hotel: The Gateway Hotel, Old Port Road, Mangaluru\n" +
                "Check-in: 2026-10-21 13:30\n" +
                "Check-out: 2026-10-22 11:00\n" +
                "Cost: Rs 6000.00";

        List<Booking> extracted = ruleBasedLlmService.extractBookings(confirmationEmail);

        assertNotNull(extracted);
        assertEquals(2, extracted.size(), "Should extract both flight and hotel bookings");

        Booking flight = extracted.get(0);
        assertEquals(BookingType.FLIGHT, flight.getType());
        assertTrue(flight.getProvider().contains("IndiGo"));
        assertEquals(new BigDecimal("4500.00"), flight.getCost());
        assertEquals(LocalDateTime.of(2026, 10, 21, 10, 30), flight.getStartTime());
        assertEquals(LocalDateTime.of(2026, 10, 21, 11, 45), flight.getEndTime());

        Booking hotel = extracted.get(1);
        assertEquals(BookingType.HOTEL, hotel.getType());
        assertTrue(hotel.getProvider().contains("Gateway Hotel"));
        assertEquals(new BigDecimal("6000.00"), hotel.getCost());
        assertEquals(LocalDateTime.of(2026, 10, 21, 13, 30), hotel.getStartTime());
    }

    @Test
    @DisplayName("Hotel Message Drafting: uses real trip and timing data")
    void testDraftHotelMessage() {
        Trip trip = Trip.builder().id("TRIP-DEMO-001").build();
        Booking hotelBooking = Booking.builder()
                .id("BKG-103")
                .provider("The Gateway Hotel, Mangaluru")
                .startTime(LocalDateTime.of(2026, 10, 21, 13, 30))
                .build();
        LocalDateTime newCheckIn = LocalDateTime.of(2026, 10, 21, 16, 45);

        Booking trainLeg = Booking.builder()
                .provider("Panchaganga Express (16595)")
                .build();
        Option option = Option.builder()
                .legs(List.of(trainLeg))
                .build();

        String message = ruleBasedLlmService.draftHotelMessage(trip, hotelBooking, newCheckIn, option);

        assertNotNull(message);
        assertTrue(message.contains("The Gateway Hotel, Mangaluru"));
        assertTrue(message.contains("BKG-103"));
        assertTrue(message.contains("Panchaganga Express"));
        assertTrue(message.contains("13:30"));
        assertTrue(message.contains("16:45"));
        assertTrue(message.contains("TRIP-DEMO-001"));
        assertTrue(message.contains("Ananya Rao"));
        assertTrue(message.contains("+91-9876543210"));
    }

    @Test
    @DisplayName("Trade-off Explanation: compares chosen option against alternatives with actual numbers")
    void testTradeOffExplanation() {
        Trip trip = Trip.builder().id("TRIP-DEMO-001").build();

        Option chosenTrain = Option.builder()
                .id("OPT-TRAIN")
                .label(OptionLabel.SAFEST)
                .legs(List.of(Booking.builder().provider("Panchaganga Express").build()))
                .totalCost(new BigDecimal("950.00"))
                .arrivalTime(LocalDateTime.of(2026, 10, 21, 15, 30))
                .build();

        Option otherFlight = Option.builder()
                .id("OPT-FLIGHT")
                .label(OptionLabel.FASTEST)
                .legs(List.of(Booking.builder().provider("Air India AI-671").build()))
                .totalCost(new BigDecimal("5200.00"))
                .arrivalTime(LocalDateTime.of(2026, 10, 21, 13, 10))
                .build();

        Option otherBus = Option.builder()
                .id("OPT-BUS")
                .label(OptionLabel.CHEAPEST)
                .legs(List.of(Booking.builder().provider("KSRTC Airavat").build()))
                .totalCost(new BigDecimal("850.00"))
                .arrivalTime(LocalDateTime.of(2026, 10, 21, 16, 0))
                .build();

        String explanation = ruleBasedLlmService.generateTradeOffExplanation(
                trip, chosenTrain, List.of(chosenTrain, otherFlight, otherBus));

        assertNotNull(explanation);
        assertTrue(explanation.contains("SAFEST"));
        assertTrue(explanation.contains("Panchaganga Express"));
        assertTrue(explanation.contains("FASTEST"));
        assertTrue(explanation.contains("Air India"));
        assertTrue(explanation.contains("CHEAPEST"));
        assertTrue(explanation.contains("saves Rs 4250.00 compared to FASTEST"));
        assertTrue(explanation.contains("costs Rs 100.00 more than CHEAPEST"));
    }

    @Test
    @DisplayName("ApiLlmService fallback: when API key fails, delegates to rule-based and logs SYSTEM audit entry")
    void testApiFallbackToRuleBased() {
        Trip trip = Trip.builder()
                .id("TRIP-DEMO-001")
                .auditLog(new ArrayList<>())
                .build();

        Booking hotel = Booking.builder()
                .id("BKG-103")
                .provider("The Gateway Hotel")
                .startTime(LocalDateTime.of(2026, 10, 21, 13, 30))
                .build();
        Option option = Option.builder()
                .id("OPT-1")
                .legs(List.of(Booking.builder().provider("Express").build()))
                .build();

        String drafted = apiLlmServiceWithKey.draftHotelMessage(trip, hotel, LocalDateTime.of(2026, 10, 21, 16, 45), option);

        assertNotNull(drafted);
        assertTrue(drafted.contains("The Gateway Hotel"));

        // Verify fallback audit entry
        List<AuditEntry> systemAudits = trip.getAuditLog().stream()
                .filter(a -> a.getActor() == AuditActor.SYSTEM && "LLM_API_FALLBACK".equals(a.getAction()))
                .toList();
        assertEquals(1, systemAudits.size());
        assertTrue(systemAudits.get(0).getDetails().contains("fell back to RuleBasedLlmService"));
    }

    @Test
    @DisplayName("Enhanced Parser: handles 'departs <city> 10:00 arrives <city> 14:30', dates, costs, types, and unparsed lines")
    void testEnhancedParserDepartsArrivesAndDatesAndCosts() {
        String inputText =
                "Bus confirmation KSRTC Sarige departs Hubballi 10:00 arrives Goa 14:30 cost Rs 650\n" +
                "Cab booking Ola from Goa to Hotel departs 2026-10-21 14:30 arrives 2026-10-21 15:15 cost INR 600\n" +
                "Hotel booking Taj Resort Goa check-in 21 Oct 2026 16:00 cost Rs 3500\n" +
                "Random meeting notes that are not a booking";

        com.odyssey.dto.response.ParseResult result = ruleBasedLlmService.parseText(inputText);

        assertNotNull(result);
        assertEquals(3, result.getBookings().size(), "Should parse 3 bookings (bus, cab, hotel)");
        assertEquals(1, result.getUnparsedLines().size(), "Should flag 1 unparsed line");
        assertTrue(result.getUnparsedLines().get(0).startsWith("Could not read this line"));
        assertTrue(result.getUnparsedLines().get(0).contains("Random meeting notes"));

        // 1. Bus
        Booking bus = result.getBookings().get(0);
        assertEquals(BookingType.BUS, bus.getType());
        assertEquals("KSRTC Sarige", bus.getProvider());
        assertEquals("Hubballi", bus.getFrom());
        assertEquals("Goa", bus.getTo());
        assertEquals(LocalDateTime.of(2026, 10, 21, 10, 0), bus.getStartTime());
        assertEquals(LocalDateTime.of(2026, 10, 21, 14, 30), bus.getEndTime());
        assertEquals(new BigDecimal("650"), bus.getCost());

        // 2. Cab
        Booking cab = result.getBookings().get(1);
        assertEquals(BookingType.CAB, cab.getType());
        assertEquals("Ola", cab.getProvider());
        assertEquals("Goa", cab.getFrom());
        assertEquals("Hotel", cab.getTo());
        assertEquals(LocalDateTime.of(2026, 10, 21, 14, 30), cab.getStartTime());
        assertEquals(LocalDateTime.of(2026, 10, 21, 15, 15), cab.getEndTime());
        assertEquals(new BigDecimal("600"), cab.getCost());

        // 3. Hotel
        Booking hotel = result.getBookings().get(2);
        assertEquals(BookingType.HOTEL, hotel.getType());
        assertEquals("Taj Resort Goa", hotel.getProvider());
        assertEquals(LocalDateTime.of(2026, 10, 21, 16, 0), hotel.getStartTime());
        assertEquals(new BigDecimal("3500"), hotel.getCost());
    }
}
