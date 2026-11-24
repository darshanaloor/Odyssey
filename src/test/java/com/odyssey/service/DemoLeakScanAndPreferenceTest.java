package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.config.ScoringProperties;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.model.*;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.model.enums.OptionLabel;
import com.odyssey.notification.EmailChannel;
import com.odyssey.notification.SimulatedChannel;
import com.odyssey.notification.SmsChannel;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DemoLeakScanAndPreferenceTest {

    private TripRepository tripRepository;
    private RecoveryProperties recoveryProperties;
    private ScoringProperties scoringProperties;
    private RuleBasedLlmService llmService;
    private RecoveryService recoveryService;
    private ScoringService scoringService;
    private NotificationService notificationService;

    private static final List<String> FORBIDDEN_DEMO_STRINGS = List.of(
            "mangaluru",
            "ananya",
            "gateway",
            "old port road"
    );

    @BeforeEach
    void setUp() {
        tripRepository = Mockito.mock(TripRepository.class);
        recoveryProperties = new RecoveryProperties();
        recoveryProperties.setReplacementCabCost(new BigDecimal("400.00"));
        recoveryProperties.setReplacementCabBufferMinutes(30);
        recoveryProperties.setDayTravelStart(LocalTime.of(6, 0));
        recoveryProperties.setDayTravelEnd(LocalTime.of(21, 0));
        recoveryProperties.setAssumeFullRefundFlight(true);
        recoveryProperties.setAssumeFullRefundCab(true);
        recoveryProperties.setStationBufferMinutes(45);

        scoringProperties = new ScoringProperties();
        llmService = new RuleBasedLlmService();
        recoveryService = new RecoveryService(tripRepository, recoveryProperties, llmService);
        scoringService = new ScoringService(scoringProperties, recoveryProperties);
        notificationService = new NotificationService(
                new EmailChannel(null, 587, null, null, null),
                new SmsChannel(null, null, null, null),
                new SimulatedChannel(),
                Mockito.mock(NotificationJpaRepository.class),
                Mockito.mock(TripJpaRepository.class),
                tripRepository,
                recoveryProperties
        );
    }

    private void assertNoDemoStringsLeak(String text, String context) {
        if (text == null) return;
        String lower = text.toLowerCase();
        for (String forbidden : FORBIDDEN_DEMO_STRINGS) {
            assertFalse(lower.contains(forbidden),
                    String.format("Demo leak detected! Forbidden substring '%s' found in %s: \"%s\"",
                            forbidden, context, text));
        }
    }

    @Test
    @DisplayName("Requirement 4: Leak-scan test fails build if demo strings appear in non-demo trip outputs")
    void testNoDemoStringsLeakIntoNonDemoTrip() {
        LocalDate tripDate = LocalDate.of(2026, 11, 15);
        Trip nonDemoTrip = Trip.builder()
                .id("TRIP-GOA-999")
                .title("Hubballi to Goa Vacation")
                .travellerName("Rohit Sharma")
                .travellerPhone("+919811122233")
                .travellerEmail("rohit@example.com")
                .status("DISRUPTED")
                .needsDestinationCab(true)
                .destinationCabDropoff("Calangute, Goa")
                .needsHotel(true)
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(tripDate.atTime(21, 0))
                        .avoidOvernightTravel(true)
                        .preferredModes(List.of(BookingType.BUS, BookingType.CAB))
                        .build())
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id("BKG-BUS-01")
                                .type(BookingType.BUS)
                                .provider("KSRTC Sarige")
                                .from("Hubballi")
                                .to("Goa")
                                .startTime(tripDate.atTime(9, 0))
                                .endTime(tripDate.atTime(14, 0))
                                .cost(new BigDecimal("650.00"))
                                .status(BookingStatus.CANCELLED)
                                .build(),
                        Booking.builder()
                                .id("BKG-HOTEL-01")
                                .type(BookingType.HOTEL)
                                .provider("Taj Fort Aguada Resort, Goa")
                                .from("Sinquerim, Candolim, Goa")
                                .to("Sinquerim, Candolim, Goa")
                                .startTime(tripDate.atTime(15, 30))
                                .endTime(tripDate.plusDays(1).atTime(11, 0))
                                .cost(new BigDecimal("8500.00"))
                                .status(BookingStatus.CONFIRMED)
                                .build()
                )))
                .activeDisruptions(List.of(
                        Disruption.builder()
                                .id("DIS-HUB-01")
                                .bookingId("BKG-BUS-01")
                                .type(DisruptionType.CANCELLATION)
                                .reason("Highway landslide")
                                .disruptionTime(tripDate.atTime(8, 0))
                                .build()
                ))
                .options(new ArrayList<>(List.of(
                        Option.builder()
                                .id("OPT-GOA-BUS-02")
                                .label(com.odyssey.model.enums.OptionLabel.SAFEST)
                                .totalCost(new BigDecimal("750.00"))
                                .arrivalTime(tripDate.atTime(15, 0))
                                .source("INVENTORY")
                                .legs(List.of(
                                        Booking.builder()
                                                .id("INV-BS-GOA-01")
                                                .type(BookingType.BUS)
                                                .provider("VRL Travels Multi-Axle")
                                                .from("Hubballi")
                                                .to("Goa")
                                                .startTime(tripDate.atTime(10, 0))
                                                .endTime(tripDate.atTime(15, 0))
                                                .cost(new BigDecimal("750.00"))
                                                .status(BookingStatus.PROPOSED)
                                                .build()
                                ))
                                .build()
                )))
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById("TRIP-GOA-999")).thenReturn(Optional.of(nonDemoTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryPlan plan = recoveryService.buildPlan("TRIP-GOA-999", "OPT-GOA-BUS-02");

        // Scan hotel message
        assertNotNull(plan.getHotelMessage(), "Hotel message should be drafted for trip with HOTEL booking");
        assertNoDemoStringsLeak(plan.getHotelMessage(), "Hotel Message");
        assertTrue(plan.getHotelMessage().contains("Rohit Sharma"), "Hotel message should contain real traveller name");
        assertTrue(plan.getHotelMessage().contains("Taj Fort Aguada"), "Hotel message should mention real hotel name");

        // Scan notification
        NotificationMessage notification = notificationService.generateAlert(
                nonDemoTrip,
                nonDemoTrip.getBookings().get(0),
                nonDemoTrip.getActiveDisruptions().get(0),
                TripImpactReport.builder()
                        .summary("KSRTC Sarige cancelled, so hotel check-in BKG-HOTEL-01 is at risk.")
                        .mustArriveByBreached("SAFE")
                        .hardCommitmentsBreached(List.of())
                        .impactedBookings(List.of())
                        .build()
        );
        assertNoDemoStringsLeak(notification.getMessage(), "Notification Alert Content");

        // Scan checklist
        for (ActionChecklistItem item : plan.getChecklist()) {
            assertNoDemoStringsLeak(item.getText(), "Checklist item (" + item.getId() + ")");
        }

        // Scan diff
        for (ItineraryDiffItem diff : plan.getItineraryDiff()) {
            assertNoDemoStringsLeak(diff.getChangeDescription(), "Itinerary Diff (" + diff.getBookingId() + ")");
            assertNoDemoStringsLeak(diff.getProvider(), "Itinerary Diff Provider (" + diff.getBookingId() + ")");
        }

        // Scan cost breakdown
        for (CostBreakdownLine line : plan.getCostBreakdown().getLines()) {
            assertNoDemoStringsLeak(line.getLabel(), "Cost line label");
            assertNoDemoStringsLeak(line.getAssumptionNote(), "Cost line assumption");
        }

        // Scan replacement cab
        assertNotNull(plan.getReplacementCab());
        assertNoDemoStringsLeak(plan.getReplacementCab().getTo(), "Replacement Cab destination");
        assertNoDemoStringsLeak(plan.getReplacementCab().getFrom(), "Replacement Cab origin");
        assertNoDemoStringsLeak(plan.getReplacementCab().getProvider(), "Replacement Cab provider");

        // Scan trade-off explanation
        assertNoDemoStringsLeak(plan.getTradeOffExplanation(), "Trade-off explanation");
    }

    @Test
    @DisplayName("Requirement 5: Replacement cab destination for bus-only trip with cab on is drop-off (Calangute, Goa), not Mangaluru")
    void testReplacementCabDestinationIsDropoffNotMangaluru() {
        LocalDate tripDate = LocalDate.of(2026, 11, 20);
        Trip busOnlyWithCab = Trip.builder()
                .id("TRIP-BUS-CAB-01")
                .title("Hubballi to Goa Conference")
                .travellerName("Suresh Kumar")
                .travellerPhone("+919444555666")
                .status("DISRUPTED")
                .needsDestinationCab(true)
                .destinationCabDropoff("Calangute, Goa")
                .needsHotel(false)
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id("BKG-BUS-HUB-GOA")
                                .type(BookingType.BUS)
                                .provider("KSRTC Airavat")
                                .from("Hubballi")
                                .to("Goa")
                                .startTime(tripDate.atTime(8, 0))
                                .endTime(tripDate.atTime(13, 0))
                                .cost(new BigDecimal("600.00"))
                                .status(BookingStatus.CANCELLED)
                                .build()
                )))
                .activeDisruptions(List.of(
                        Disruption.builder()
                                .id("DIS-BUS-01")
                                .bookingId("BKG-BUS-HUB-GOA")
                                .type(DisruptionType.CANCELLATION)
                                .reason("Bus breakdown")
                                .disruptionTime(tripDate.atTime(7, 30))
                                .build()
                ))
                .options(new ArrayList<>(List.of(
                        Option.builder()
                                .id("OPT-REPL-BUS")
                                .label(com.odyssey.model.enums.OptionLabel.FASTEST)
                                .totalCost(new BigDecimal("650.00"))
                                .arrivalTime(tripDate.atTime(14, 0))
                                .source("INVENTORY")
                                .legs(List.of(
                                        Booking.builder()
                                                .id("INV-BUS-REPL")
                                                .type(BookingType.BUS)
                                                .provider("KSRTC Sarige")
                                                .from("Hubballi")
                                                .to("Goa")
                                                .startTime(tripDate.atTime(9, 0))
                                                .endTime(tripDate.atTime(14, 0))
                                                .cost(new BigDecimal("650.00"))
                                                .status(BookingStatus.PROPOSED)
                                                .build()
                                ))
                                .build()
                )))
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById("TRIP-BUS-CAB-01")).thenReturn(Optional.of(busOnlyWithCab));

        RecoveryPlan plan = recoveryService.buildPlan("TRIP-BUS-CAB-01", "OPT-REPL-BUS");

        // Verify replacement cab exists
        assertNotNull(plan.getReplacementCab(), "Replacement cab must be created when needsDestinationCab is true");
        assertEquals("Calangute, Goa", plan.getReplacementCab().getTo(),
                "Replacement cab destination must be the configured drop-off (Calangute, Goa)");
        assertNotEquals("Mangaluru", plan.getReplacementCab().getTo(),
                "Replacement cab destination must NOT be Mangaluru");

        // Verify Hotel elements are completely absent (Requirements 1 & 2)
        assertNull(plan.getHotelMessage(), "Hotel message must be absent when trip has no HOTEL booking");
        assertFalse(plan.getChecklist().stream().anyMatch(i -> "MESSAGE_HOTEL".equals(i.getId())),
                "Checklist must not have MESSAGE_HOTEL when no HOTEL booking exists");
        assertFalse(plan.getCostBreakdown().getLines().stream().anyMatch(l -> l.getLabel().toLowerCase().contains("hotel")),
                "Cost breakdown must not have Hotel line when no HOTEL booking exists");

        // Verify Event / confirm arrival is absent
        assertFalse(plan.getChecklist().stream().anyMatch(i -> "CONFIRM_FAMILY".equals(i.getId())),
                "Confirm arrival must be absent when no event or hard commitments exist");
    }

    @Test
    @DisplayName("Requirements 1 & 2: Pure bus-only trip without cab or hotel has no cab and no hotel in plan")
    void testPureBusOnlyTripHasNoCabAndNoHotel() {
        LocalDate tripDate = LocalDate.of(2026, 11, 20);
        Trip pureBusTrip = Trip.builder()
                .id("TRIP-PURE-BUS")
                .title("Hubballi to Goa Direct")
                .travellerName("Kavita Nair")
                .travellerPhone("+919777888999")
                .status("DISRUPTED")
                .needsDestinationCab(false)
                .needsHotel(false)
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id("BKG-BUS-ONLY")
                                .type(BookingType.BUS)
                                .provider("NWKRTC Express")
                                .from("Hubballi")
                                .to("Goa")
                                .startTime(tripDate.atTime(8, 0))
                                .endTime(tripDate.atTime(13, 0))
                                .cost(new BigDecimal("500.00"))
                                .status(BookingStatus.CANCELLED)
                                .build()
                )))
                .activeDisruptions(List.of(
                        Disruption.builder()
                                .id("DIS-BUS-02")
                                .bookingId("BKG-BUS-ONLY")
                                .type(DisruptionType.CANCELLATION)
                                .reason("Route diversion")
                                .disruptionTime(tripDate.atTime(7, 30))
                                .build()
                ))
                .options(new ArrayList<>(List.of(
                        Option.builder()
                                .id("OPT-BUS-ALT")
                                .label(com.odyssey.model.enums.OptionLabel.CHEAPEST)
                                .totalCost(new BigDecimal("520.00"))
                                .arrivalTime(tripDate.atTime(13, 30))
                                .source("INVENTORY")
                                .legs(List.of(
                                        Booking.builder()
                                                .id("INV-BUS-ALT")
                                                .type(BookingType.BUS)
                                                .provider("KSRTC Express")
                                                .from("Hubballi")
                                                .to("Goa")
                                                .startTime(tripDate.atTime(8, 30))
                                                .endTime(tripDate.atTime(13, 30))
                                                .cost(new BigDecimal("520.00"))
                                                .status(BookingStatus.PROPOSED)
                                                .build()
                                ))
                                .build()
                )))
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById("TRIP-PURE-BUS")).thenReturn(Optional.of(pureBusTrip));

        RecoveryPlan plan = recoveryService.buildPlan("TRIP-PURE-BUS", "OPT-BUS-ALT");

        assertNull(plan.getReplacementCab(), "Replacement cab must be null for pure bus trip");
        assertNull(plan.getHotelMessage(), "Hotel message must be null for pure bus trip");
        assertFalse(plan.getChecklist().stream().anyMatch(i -> "BOOK_REPLACEMENT_CAB".equals(i.getId())),
                "Checklist must not have BOOK_REPLACEMENT_CAB");
        assertFalse(plan.getChecklist().stream().anyMatch(i -> "MESSAGE_HOTEL".equals(i.getId())),
                "Checklist must not have MESSAGE_HOTEL");
        assertFalse(plan.getCostBreakdown().getLines().stream().anyMatch(l -> l.getLabel().toLowerCase().contains("cab")),
                "Cost breakdown must not have Cab line");
        assertFalse(plan.getCostBreakdown().getLines().stream().anyMatch(l -> l.getLabel().toLowerCase().contains("hotel")),
                "Cost breakdown must not have Hotel line");
    }

    @Test
    @DisplayName("Requirement 6: Day-travel window rejects legs arriving after 21:00 or departing before 06:00 with dates in reason")
    void testDayTravelWindowRejectionsWithDates() {
        LocalDate tripDate = LocalDate.of(2026, 10, 21);
        Trip trip = Trip.builder()
                .id("TRIP-DAY-WINDOW")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(tripDate.atTime(23, 59))
                        .avoidOvernightTravel(true)
                        .build())
                .bookings(new ArrayList<>())
                .build();

        Booking disrupted = Booking.builder()
                .id("BKG-ORIG")
                .startTime(tripDate.atTime(4, 0))
                .endTime(tripDate.atTime(9, 0))
                .build();

        // Leg departing before 06:00 (e.g. 05:30)
        InventoryItem earlyLeg = InventoryItem.builder()
                .id("INV-EARLY-01")
                .mode(BookingType.BUS)
                .provider("Early Bird Express")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.atTime(5, 30))
                .arrival(tripDate.atTime(10, 30))
                .cost(new BigDecimal("600.00"))
                .seatsAvailable(5)
                .build();

        // Leg arriving after 21:00 (e.g. 21:45)
        InventoryItem lateLeg = InventoryItem.builder()
                .id("INV-LATE-01")
                .mode(BookingType.BUS)
                .provider("Late Night Express")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.atTime(16, 0))
                .arrival(tripDate.atTime(21, 45))
                .cost(new BigDecimal("600.00"))
                .seatsAvailable(5)
                .build();

        // Leg inside window (09:00 to 14:00)
        InventoryItem validLeg = InventoryItem.builder()
                .id("INV-VALID-01")
                .mode(BookingType.BUS)
                .provider("Midday Sarige")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.atTime(9, 0))
                .arrival(tripDate.atTime(14, 0))
                .cost(new BigDecimal("600.00"))
                .seatsAvailable(5)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(trip, disrupted, List.of(earlyLeg, lateLeg, validLeg));

        // Both early and late legs must be rejected; single valid leg yields FASTEST and SAFEST ("Same result as FASTEST")
        assertEquals(2, response.getRejectedOptions().size());
        assertEquals(2, response.getOptions().size());
        assertEquals(OptionLabel.FASTEST, response.getOptions().get(0).getLabel());
        assertTrue(response.getOptions().get(0).getId().contains("INV-VALID-01"));
        assertTrue(response.getOptions().get(0).getLegs().get(0).getId().contains("INV-VALID-01"));
        assertEquals(OptionLabel.SAFEST, response.getOptions().get(1).getLabel());
        assertTrue(response.getOptions().get(1).getId().contains("INV-VALID-01"));
        assertTrue(response.getOptions().get(1).getBestAvailableNote().contains("Same result as FASTEST"));

        RejectedOption rejEarly = response.getRejectedOptions().stream()
                .filter(r -> "INV-EARLY-01".equals(r.getId())).findFirst().orElseThrow();
        assertTrue(rejEarly.getReason().contains("before the day-travel window starts at 06:00"),
                "Early leg reason should state before 06:00 window");
        assertTrue(rejEarly.getReason().contains("2026-10-21 05:30"),
                "Early leg reason must include departure date and time");

        RejectedOption rejLate = response.getRejectedOptions().stream()
                .filter(r -> "INV-LATE-01".equals(r.getId())).findFirst().orElseThrow();
        assertTrue(rejLate.getReason().contains("after the day-travel window ends at 21:00"),
                "Late leg reason should state after 21:00 window");
        assertTrue(rejLate.getReason().contains("2026-10-21 21:45"),
                "Late leg reason must include arrival date and time");
    }
}
