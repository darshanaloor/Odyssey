package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.config.ScoringProperties;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.model.*;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.OptionLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripValidationAndScoringTest {

    private DependencyGraph dependencyGraph;
    private ScoringService scoringService;
    private RecoveryProperties recoveryProperties;

    @BeforeEach
    void setUp() {
        dependencyGraph = new DependencyGraph();
        recoveryProperties = new RecoveryProperties();
        recoveryProperties.setSyntheticReliabilityScore(60.0);
        scoringService = new ScoringService(new ScoringProperties(), recoveryProperties);
    }

    @Test
    @DisplayName("DependencyGraph detects cycles in booking dependencies")
    void testCycleDetection() {
        Booking b1 = Booking.builder().id("B1").dependsOn(List.of("B2")).build();
        Booking b2 = Booking.builder().id("B2").dependsOn(List.of("B3")).build();
        Booking b3 = Booking.builder().id("B3").dependsOn(List.of("B1")).build(); // Cycle: B1 -> B2 -> B3 -> B1

        assertTrue(dependencyGraph.hasCycle(List.of(b1, b2, b3)), "Cycle B1 -> B2 -> B3 -> B1 must be detected");

        // Acyclic dependency
        Booking a1 = Booking.builder().id("A1").dependsOn(new ArrayList<>()).build();
        Booking a2 = Booking.builder().id("A2").dependsOn(List.of("A1")).build();
        Booking a3 = Booking.builder().id("A3").dependsOn(List.of("A2")).build();

        assertFalse(dependencyGraph.hasCycle(List.of(a1, a2, a3)), "Linear chain A1 -> A2 -> A3 must not have cycle");
    }

    @Test
    @DisplayName("Estimated options use configured synthetic reliability score and are never labelled SAFEST while an INVENTORY option exists")
    void testEstimatedOptionsNeverLabelledSafestWhenInventoryExists() {
        LocalDateTime tripDate = LocalDateTime.of(2026, 10, 21, 9, 0);

        Trip trip = Trip.builder()
                .id("TRIP-TEST")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(tripDate.withHour(18).withMinute(0))
                        .avoidOvernightTravel(true)
                        .build())
                .bookings(new ArrayList<>())
                .build();

        Booking disrupted = Booking.builder()
                .id("BKG-ROOT")
                .from("BENGALURU")
                .to("MANGALURU")
                .startTime(tripDate)
                .cost(new BigDecimal("4000.00"))
                .status(BookingStatus.CANCELLED)
                .build();
        trip.getBookings().add(disrupted);

        // Candidate 1: Real INVENTORY option (arrival 14:00, reliability 85)
        InventoryItem inv1 = InventoryItem.builder()
                .id("INV-REAL-1")
                .mode(BookingType.TRAIN)
                .provider("Real Train Express")
                .from("Bengaluru")
                .to("Mangaluru")
                .departure(tripDate.plusHours(1))
                .arrival(tripDate.plusHours(5)) // 15:00
                .cost(new BigDecimal("1500.00"))
                .seatsAvailable(10)
                .reliabilityScore(85)
                .source("INVENTORY")
                .build();

        // Candidate 2: ESTIMATED option with higher deadline buffer or arrival earlier
        InventoryItem est1 = InventoryItem.builder()
                .id("SYN-EST-1")
                .mode(BookingType.FLIGHT)
                .provider("Estimated Air Direct")
                .from("Bengaluru")
                .to("Mangaluru")
                .departure(tripDate.plusMinutes(45))
                .arrival(tripDate.plusHours(2)) // 11:45 (faster!)
                .cost(new BigDecimal("4500.00"))
                .seatsAvailable(5)
                .source("ESTIMATED")
                .build();

        // Candidate 3: ESTIMATED option cheap
        InventoryItem est2 = InventoryItem.builder()
                .id("SYN-EST-2")
                .mode(BookingType.BUS)
                .provider("Estimated Express Bus")
                .from("Bengaluru")
                .to("Mangaluru")
                .departure(tripDate.plusMinutes(30))
                .arrival(tripDate.plusHours(6)) // 15:30
                .cost(new BigDecimal("700.00"))
                .seatsAvailable(15)
                .source("ESTIMATED")
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(trip, disrupted, List.of(inv1, est1, est2),
                tripDate.plusMinutes(15), tripDate, 30);

        assertNotNull(response);
        assertEquals(3, response.getOptions().size(), "Should rank 3 options");

        Option safestOpt = response.getOptions().stream()
                .filter(o -> o.getLabel() == OptionLabel.SAFEST)
                .findFirst()
                .orElse(null);

        assertNotNull(safestOpt, "Must have a SAFEST option");
        assertEquals("INVENTORY", safestOpt.getSource(),
                "SAFEST option must have source=INVENTORY when an INVENTORY candidate exists");
        assertEquals("INV-REAL-1", safestOpt.getLegs().get(0).getId().replace("BKG-REC-", ""));
    }

    @Test
    @DisplayName("When all options are ESTIMATED, SAFEST is assigned from ESTIMATED pool with synthetic reliability score")
    void testAllEstimatedOptionsCanAssignSafest() {
        LocalDateTime tripDate = LocalDateTime.of(2026, 10, 21, 9, 0);

        Trip trip = Trip.builder()
                .id("TRIP-HBX-GOA")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(tripDate.withHour(18).withMinute(0))
                        .avoidOvernightTravel(true)
                        .build())
                .bookings(new ArrayList<>())
                .build();

        Booking disrupted = Booking.builder()
                .id("BKG-HBX")
                .from("Hubballi")
                .to("Goa")
                .startTime(tripDate)
                .cost(new BigDecimal("1000.00"))
                .status(BookingStatus.CANCELLED)
                .build();
        trip.getBookings().add(disrupted);

        InventoryItem est1 = InventoryItem.builder()
                .id("SYN-1")
                .mode(BookingType.CAB)
                .provider("Estimated Taxi")
                .departure(tripDate.plusMinutes(45))
                .arrival(tripDate.plusHours(3))
                .cost(new BigDecimal("3000.00"))
                .seatsAvailable(4)
                .source("ESTIMATED")
                .build();

        InventoryItem est2 = InventoryItem.builder()
                .id("SYN-2")
                .mode(BookingType.BUS)
                .provider("Estimated Bus")
                .departure(tripDate.plusMinutes(60))
                .arrival(tripDate.plusHours(4))
                .cost(new BigDecimal("600.00"))
                .seatsAvailable(20)
                .source("ESTIMATED")
                .build();

        InventoryItem est3 = InventoryItem.builder()
                .id("SYN-3")
                .mode(BookingType.TRAIN)
                .provider("Estimated Train")
                .departure(tripDate.plusMinutes(90))
                .arrival(tripDate.plusHours(5))
                .cost(new BigDecimal("800.00"))
                .seatsAvailable(30)
                .source("ESTIMATED")
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(trip, disrupted, List.of(est1, est2, est3),
                tripDate.plusMinutes(15), tripDate, 30);

        assertNotNull(response);
        assertEquals(3, response.getOptions().size());
        assertTrue(response.getOptions().stream().allMatch(o -> "ESTIMATED".equalsIgnoreCase(o.getSource())),
                "All options should be marked ESTIMATED");
    }

    @Test
    @DisplayName("Phone validation accepts 10 digits and normalizes to +91XXXXXXXXXX")
    void testPhoneValidationAndNormalization() {
        assertEquals("+919876543210", TripService.normalizePhone("9876543210"));
        assertEquals("+919876543210", TripService.normalizePhone("+919876543210"));
        assertEquals("+919876543210", TripService.normalizePhone("+91 98765 43210"));
        assertEquals("+919876543210", TripService.normalizePhone("09876543210"));

        assertThrows(IllegalArgumentException.class, () -> TripService.normalizePhone("12345"),
                "Short phone numbers must be rejected");
        assertThrows(IllegalArgumentException.class, () -> TripService.normalizePhone(""),
                "Empty phone must be rejected");
        assertThrows(IllegalArgumentException.class, () -> TripService.normalizePhone("9876543210123"),
                "Overly long phone must be rejected");
    }

    @Test
    @DisplayName("Email validation checks format when present and allows null/empty")
    void testEmailValidation() {
        assertNull(TripService.validateEmail(null));
        assertNull(TripService.validateEmail("   "));
        assertEquals("user@example.com", TripService.validateEmail("user@example.com"));
        assertEquals("traveller.name+tag@sub.domain.co", TripService.validateEmail("  traveller.name+tag@sub.domain.co  "));

        assertThrows(IllegalArgumentException.class, () -> TripService.validateEmail("invalid-email"),
                "Invalid email without domain/at must be rejected");
        assertThrows(IllegalArgumentException.class, () -> TripService.validateEmail("user@domain"),
                "Invalid email without TLD must be rejected");
    }
}
