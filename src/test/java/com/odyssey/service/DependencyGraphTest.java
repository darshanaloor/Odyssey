package com.odyssey.service;

import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.Disruption;
import com.odyssey.model.Trip;
import com.odyssey.model.TripImpactReport;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    private DependencyGraph dependencyGraph;
    private LocalDateTime baseDate;
    private Trip sampleTrip;
    private Booking flight;
    private Booking cab;
    private Booking hotel;
    private Booking event;
    private Booking standaloneBooking;

    @BeforeEach
    void setUp() {
        dependencyGraph = new DependencyGraph();
        baseDate = LocalDateTime.now().plusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);

        flight = Booking.builder()
                .id("BKG-101")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-534")
                .from("BLR")
                .to("IXE")
                .startTime(baseDate.withHour(10).withMinute(30))
                .endTime(baseDate.withHour(11).withMinute(45))
                .cost(new BigDecimal("4500.00"))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(new ArrayList<>())
                .build();

        cab = Booking.builder()
                .id("BKG-102")
                .type(BookingType.CAB)
                .provider("Airport Prepaid Taxi")
                .from("IXE")
                .to("Hotel")
                .startTime(baseDate.withHour(12).withMinute(15))
                .endTime(baseDate.withHour(13).withMinute(0))
                .cost(new BigDecimal("800.00"))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(List.of("BKG-101"))
                .build();

        hotel = Booking.builder()
                .id("BKG-103")
                .type(BookingType.HOTEL)
                .provider("The Gateway Hotel")
                .from("Mangaluru")
                .to("Mangaluru")
                .startTime(baseDate.withHour(13).withMinute(30))
                .endTime(baseDate.plusDays(1).withHour(11).withMinute(0))
                .cost(new BigDecimal("5200.00"))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(List.of("BKG-102"))
                .build();

        event = Booking.builder()
                .id("BKG-104")
                .type(BookingType.EVENT)
                .provider("Family Gathering & Celebration")
                .from("Ocean Pearl")
                .to("Ocean Pearl")
                .startTime(baseDate.withHour(18).withMinute(0))
                .endTime(baseDate.withHour(22).withMinute(0))
                .cost(new BigDecimal("2000.00"))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(List.of("BKG-103"))
                .build();

        standaloneBooking = Booking.builder()
                .id("BKG-STANDALONE")
                .type(BookingType.CAB)
                .provider("Standalone Sightseeing Cab")
                .from("Mangaluru Beach")
                .to("Mangaluru Temple")
                .startTime(baseDate.plusDays(1).withHour(14).withMinute(0))
                .endTime(baseDate.plusDays(1).withHour(16).withMinute(0))
                .cost(new BigDecimal("500.00"))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(new ArrayList<>())
                .build();

        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("15000.00"))
                .mustArriveBy(baseDate.withHour(17).withMinute(30))
                .hardCommitments(List.of("Family Gathering & Celebration"))
                .build();

        sampleTrip = Trip.builder()
                .id("TRIP-TEST-001")
                .title("Bengaluru to Mangaluru")
                .bookings(new ArrayList<>(List.of(flight, cab, hotel, event, standaloneBooking)))
                .constraints(constraints)
                .activeDisruptions(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Test 1: Flight Delay (180 mins) leaves 6 PM event SAFE since arrival 17:30 <= 17:30 buffer threshold")
    void testFlightDelay180LeavesEventSafe() {
        Disruption disruption = Disruption.builder()
                .id("DIS-101")
                .type(DisruptionType.FLIGHT_DELAY)
                .bookingId("BKG-101")
                .delayMinutes(180)
                .reason("Monsoon weather turbulence at departure airport")
                .build();

        TripImpactReport report = dependencyGraph.analyze(sampleTrip, flight, disruption);

        assertNotNull(report);
        assertEquals(3, report.getImpactedBookings().size()); // cab, hotel, event
        assertEquals("PENDING_RECOVERY", report.getMustArriveByBreached());

        // Cab should be AT_RISK with delay details
        var cabImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-102")).findFirst().orElseThrow();
        assertEquals(BookingStatus.AT_RISK, cabImpact.getStatus());
        assertTrue(cabImpact.getReason().contains("late by 180 minutes"));

        // Hotel check-in should be AT_RISK with late check-in
        var hotelImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-103")).findFirst().orElseThrow();
        assertEquals(BookingStatus.AT_RISK, hotelImpact.getStatus());
        assertTrue(hotelImpact.getReason().contains("late by 150 minutes"));

        // Event at 6 PM should be SAFE: arrival 17:30 <= (18:00 - 30m buffer)
        var eventImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-104")).findFirst().orElseThrow();
        assertEquals(BookingStatus.SAFE, eventImpact.getStatus());
        assertEquals("Still reachable: projected arrival 17:30, event starts 18:00", eventImpact.getReason());

        assertTrue(report.getSummary().contains("airport cab BKG-102 is at risk (late by 180 minutes)"));
        assertTrue(report.getSummary().contains("hotel check-in BKG-103 is at risk (late by 150 minutes)"));
        assertFalse(report.getSummary().contains("event BKG-104 is at risk"));
    }

    @Test
    @DisplayName("Test 1b: Major Flight Delay (420 mins) pushes arrival past buffer threshold and flags event AT_RISK")
    void testFlightDelay420MarksEventAtRisk() {
        Disruption disruption = Disruption.builder()
                .id("DIS-420")
                .type(DisruptionType.FLIGHT_DELAY)
                .bookingId("BKG-101")
                .delayMinutes(420) // 7 hours delay
                .reason("Radar failure and air traffic ground stop")
                .build();

        TripImpactReport report = dependencyGraph.analyze(sampleTrip, flight, disruption);

        var eventImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-104")).findFirst().orElseThrow();
        assertEquals(BookingStatus.AT_RISK, eventImpact.getStatus());
        assertEquals("At risk unless a replacement journey reaches Mangaluru by 17:30.", eventImpact.getReason());
        assertTrue(report.getSummary().contains("the 6 PM event BKG-104 is at risk"));
    }

    @Test
    @DisplayName("Test 2: Minor Flight Delay (20 mins) leaves 6 PM event SAFE")
    void testMinorFlightDelayLeavesEventSafe() {
        Disruption disruption = Disruption.builder()
                .id("DIS-102")
                .type(DisruptionType.FLIGHT_DELAY)
                .bookingId("BKG-101")
                .delayMinutes(20)
                .reason("Gate congestion")
                .build();

        TripImpactReport report = dependencyGraph.analyze(sampleTrip, flight, disruption);

        var eventImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-104")).findFirst().orElseThrow();
        assertEquals(BookingStatus.SAFE, eventImpact.getStatus());
        assertTrue(eventImpact.getReason().contains("Still reachable"));
    }

    @Test
    @DisplayName("Test 3: Flight Cancellation cascades to wasted cab and at-risk event")
    void testFlightCancellationCascade() {
        Disruption disruption = Disruption.builder()
                .id("DIS-103")
                .type(DisruptionType.CANCELLATION)
                .bookingId("BKG-101")
                .reason("Engine technical failure")
                .build();

        TripImpactReport report = dependencyGraph.analyze(sampleTrip, flight, disruption);

        assertEquals(3, report.getImpactedBookings().size());

        // Cab is cancelled
        var cabImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-102")).findFirst().orElseThrow();
        assertEquals(BookingStatus.CANCELLED, cabImpact.getStatus());
        assertTrue(cabImpact.getReason().contains("airport cab BKG-102 (Airport Prepaid Taxi) is wasted"));

        // Hotel is at risk
        var hotelImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-103")).findFirst().orElseThrow();
        assertEquals(BookingStatus.AT_RISK, hotelImpact.getStatus());
        assertTrue(hotelImpact.getReason().contains("hotel check-in BKG-103"));

        // Event is at risk with exact reason
        var eventImpact = report.getImpactedBookings().stream()
                .filter(b -> b.getBookingId().equals("BKG-104")).findFirst().orElseThrow();
        assertEquals(BookingStatus.AT_RISK, eventImpact.getStatus());
        assertEquals("At risk unless a replacement journey reaches Mangaluru by 17:30.", eventImpact.getReason());

        assertTrue(report.getSummary().contains("airport cab BKG-102 is cancelled"));
        assertTrue(report.getSummary().contains("hotel check-in BKG-103 is at risk"));
        assertTrue(report.getSummary().contains("the 6 PM event BKG-104 is at risk"));
    }

    @Test
    @DisplayName("Test 4: Disruption on a Booking with No Dependents returns zero downstream impacts")
    void testBookingWithNoDependents() {
        Disruption disruption = Disruption.builder()
                .id("DIS-104")
                .type(DisruptionType.CANCELLATION)
                .bookingId("BKG-STANDALONE")
                .reason("Driver unavailable")
                .build();

        TripImpactReport report = dependencyGraph.analyze(sampleTrip, standaloneBooking, disruption);

        assertNotNull(report);
        assertTrue(report.getImpactedBookings().isEmpty(), "Standalone booking should have zero downstream impacts");
        assertEquals("BKG-STANDALONE", report.getRootBookingId());
        assertTrue(report.getSummary().contains("cab cancelled"));
    }
}
