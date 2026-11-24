package com.odyssey.service;

import com.odyssey.config.ScoringProperties;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.Option;
import com.odyssey.model.Trip;
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

class ScoringServiceTest {

    private ScoringService scoringService;
    private LocalDateTime baseDate;
    private Trip sampleTrip;
    private Booking disruptedFlight;

    @BeforeEach
    void setUp() {
        ScoringProperties scoringProperties = new ScoringProperties();
        com.odyssey.config.RecoveryProperties recoveryProperties = new com.odyssey.config.RecoveryProperties();
        scoringService = new ScoringService(scoringProperties, recoveryProperties);

        baseDate = LocalDateTime.of(2026, 10, 21, 0, 0);

        disruptedFlight = Booking.builder()
                .id("BKG-101")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-534")
                .from("BLR")
                .to("IXE")
                .startTime(baseDate.withHour(10).withMinute(30))
                .endTime(baseDate.withHour(11).withMinute(45))
                .cost(new BigDecimal("4500.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("15000.00"))
                .mustArriveBy(baseDate.withHour(17).withMinute(30))
                .avoidOvernightTravel(true)
                .preferredModes(List.of(BookingType.FLIGHT, BookingType.CAB))
                .hardCommitments(List.of("Family Event at 6:00 PM in Mangaluru"))
                .build();

        sampleTrip = Trip.builder()
                .id("TRIP-TEST-001")
                .title("Bengaluru to Mangaluru")
                .bookings(new ArrayList<>(List.of(disruptedFlight)))
                .constraints(constraints)
                .activeDisruptions(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Case 1: Perfect options exist - 3 distinct options picked for FASTEST, CHEAPEST, SAFEST")
    void testPerfectOptionExists() {
        InventoryItem fastFlight = InventoryItem.builder()
                .id("INV-FL-001")
                .mode(BookingType.FLIGHT)
                .provider("IndiGo 6E-6512")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(13).withMinute(15))
                .arrival(baseDate.withHour(14).withMinute(25))
                .cost(new BigDecimal("6200.00"))
                .transfers(0)
                .seatsAvailable(5)
                .reliabilityScore(92)
                .build();

        InventoryItem cheapBus = InventoryItem.builder()
                .id("INV-BS-001")
                .mode(BookingType.BUS)
                .provider("KSRTC Airavat Club Class")
                .from("Bengaluru (Majestic)")
                .to("Mangaluru (Bejai)")
                .departure(baseDate.withHour(10).withMinute(45))
                .arrival(baseDate.withHour(17).withMinute(15))
                .cost(new BigDecimal("1250.00"))
                .transfers(0)
                .seatsAvailable(14)
                .reliabilityScore(86)
                .build();

        InventoryItem train = InventoryItem.builder()
                .id("INV-TR-001")
                .mode(BookingType.TRAIN)
                .provider("Superfast Express 16515")
                .from("Bengaluru (KSR City)")
                .to("Mangaluru (Central)")
                .departure(baseDate.withHour(7).withMinute(30))
                .arrival(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("2100.00"))
                .transfers(0)
                .seatsAvailable(20)
                .reliabilityScore(96)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(sampleTrip, disruptedFlight,
                List.of(fastFlight, cheapBus, train));

        assertNotNull(response);
        assertFalse(response.isNoPerfectOption(), "Should find feasible options meeting all hard constraints");
        assertEquals(3, response.getOptions().size());

        Option fastest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.FASTEST).findFirst().orElseThrow();
        Option cheapest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.CHEAPEST).findFirst().orElseThrow();
        Option safest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.SAFEST).findFirst().orElseThrow();

        // Must be three distinct options
        assertNotEquals(fastest.getId(), cheapest.getId());
        assertNotEquals(cheapest.getId(), safest.getId());
        assertNotEquals(fastest.getId(), safest.getId());

        // FASTEST: earliest arrival (14:25)
        assertEquals(baseDate.withHour(14).withMinute(25), fastest.getArrivalTime());
        assertEquals(new BigDecimal("6200.00"), fastest.getTotalCost());
        assertTrue(fastest.getConstraintsMet().stream().anyMatch(m -> m.contains("before the 17:30 deadline")));
        assertTrue(fastest.getExplanation().contains("Net trip cost"));

        // CHEAPEST from remainder: KSRTC Airavat (Rs 1250.00)
        assertEquals(new BigDecimal("1250.00"), cheapest.getTotalCost());
        // Soft constraint: mode BUS is not preferred (FLIGHT, CAB)
        assertTrue(cheapest.getConstraintsViolated().stream().anyMatch(v -> v.contains("Mode BUS is not a preferred mode")));

        // SAFEST from remainder: Superfast train (rel 96, buffer 120 min)
        assertEquals(new BigDecimal("2100.00"), safest.getTotalCost());
        assertTrue(safest.getConstraintsViolated().stream().anyMatch(v -> v.contains("Mode TRAIN is not a preferred mode")));
    }

    @Test
    @DisplayName("Case 2: No option meets all constraints - populated rejectedOptions and explanation")
    void testNoOptionMeetsAllConstraints() {
        // Impossible arrival deadline: 11:00 AM
        sampleTrip.getConstraints().setMustArriveBy(baseDate.withHour(11).withMinute(0));

        InventoryItem item1 = InventoryItem.builder()
                .id("INV-FL-001")
                .mode(BookingType.FLIGHT)
                .provider("IndiGo 6E-6512")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(13).withMinute(15))
                .arrival(baseDate.withHour(14).withMinute(25))
                .cost(new BigDecimal("6200.00"))
                .transfers(0)
                .seatsAvailable(5)
                .reliabilityScore(92)
                .build();

        InventoryItem item2 = InventoryItem.builder()
                .id("INV-BS-001")
                .mode(BookingType.BUS)
                .provider("KSRTC Airavat Club Class")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(10).withMinute(45))
                .arrival(baseDate.withHour(17).withMinute(15))
                .cost(new BigDecimal("1250.00"))
                .transfers(0)
                .seatsAvailable(14)
                .reliabilityScore(86)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(sampleTrip, disruptedFlight, List.of(item1, item2));

        assertNotNull(response);
        assertTrue(response.isNoPerfectOption(), "Should flag noPerfectOption=true when all candidates breach deadline");
        assertTrue(response.getExplanation().contains("No feasible options meet all hard constraints"));
        assertEquals(2, response.getOptions().size(), "Should return least-bad candidates rather than leaving options empty");
        assertTrue(response.getOptions().stream().allMatch(o -> o.getExplanation().contains("Best available, breaks")));
        assertEquals(2, response.getRejectedOptions().size());
        assertTrue(response.getRejectedOptions().stream().allMatch(r -> r.getReason().contains("deadline")));
    }

    @Test
    @DisplayName("Case 3: Overnight constraint excludes cheap night bus into rejectedOptions")
    void testOvernightConstraintExcludesCheapNightBus() {
        sampleTrip.getConstraints().setAvoidOvernightTravel(true);
        sampleTrip.getConstraints().setMustArriveBy(baseDate.plusDays(1).withHour(9).withMinute(0));

        // Option A: Day flight (Rs 4,800, arr 15:45, day travel)
        InventoryItem dayFlight = InventoryItem.builder()
                .id("INV-FL-002")
                .mode(BookingType.FLIGHT)
                .provider("Air India Express IX-782")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(14).withMinute(30))
                .arrival(baseDate.withHour(15).withMinute(45))
                .cost(new BigDecimal("4800.00"))
                .transfers(0)
                .seatsAvailable(8)
                .reliabilityScore(88)
                .build();

        // Option B: Overnight sleeper bus (Rs 1,400, dep 22:30, arr 06:15 next day)
        InventoryItem nightBus = InventoryItem.builder()
                .id("INV-BS-003")
                .mode(BookingType.BUS)
                .provider("Anand Travels AC Sleeper")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(22).withMinute(30))
                .arrival(baseDate.plusDays(1).withHour(6).withMinute(15))
                .cost(new BigDecimal("1400.00"))
                .transfers(0)
                .seatsAvailable(10)
                .reliabilityScore(82)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(sampleTrip, disruptedFlight, List.of(dayFlight, nightBus));

        // Night bus must be rejected
        assertEquals(1, response.getRejectedOptions().size());
        assertEquals("INV-BS-003", response.getRejectedOptions().get(0).getId());
        assertTrue(response.getRejectedOptions().get(0).getReason().contains("Overnight travel violates avoid-overnight constraint"));

        // Only 1 feasible option (day flight), returned as FASTEST and SAFEST ("Same result as FASTEST")
        assertEquals(2, response.getOptions().size());
        assertEquals("OPT-FASTEST-INV-FL-002", response.getOptions().get(0).getId());
        assertEquals("OPT-SAFEST-INV-FL-002", response.getOptions().get(1).getId());
        assertTrue(response.getOptions().get(1).getBestAvailableNote().contains("Same result as FASTEST"));
        assertTrue(response.getExplanation().contains("Only 1 feasible option available"));
    }

    @Test
    @DisplayName("Candidate departing before disruptionTime + buffer is rejected with the exact reason")
    void testEarlyDepartureBeforeDisruptionTimeRejectedWithExactReason() {
        LocalDateTime disruptionTime = baseDate.withHour(9).withMinute(0);
        int stationBufferMinutes = 45;
        LocalDateTime earliestFeasible = disruptionTime.plusMinutes(stationBufferMinutes); // 09:45

        InventoryItem earlyTrain = InventoryItem.builder()
                .id("INV-TR-001")
                .mode(BookingType.TRAIN)
                .provider("Mangaluru Superfast Express (16515)")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(7).withMinute(30))
                .arrival(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("1950.00"))
                .seatsAvailable(20)
                .reliabilityScore(95)
                .build();

        InventoryItem validTrain = InventoryItem.builder()
                .id("INV-TR-003")
                .mode(BookingType.TRAIN)
                .provider("Panchaganga Express (16595)")
                .from("BLR")
                .to("IXE")
                .departure(baseDate.withHour(10).withMinute(15))
                .arrival(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("2100.00"))
                .seatsAvailable(20)
                .reliabilityScore(96)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(
                sampleTrip, disruptedFlight, List.of(earlyTrain, validTrain),
                earliestFeasible, disruptionTime, stationBufferMinutes);

        assertEquals(1, response.getRejectedOptions().size());
        var rejected = response.getRejectedOptions().get(0);
        assertEquals("INV-TR-001", rejected.getId());
        assertEquals("Departs 07:30, before the disruption was reported at 09:00 (earliest feasible departure 09:45)",
                rejected.getReason());

        assertEquals(2, response.getOptions().size());
        assertEquals(OptionLabel.FASTEST, response.getOptions().get(0).getLabel());
        assertEquals("BKG-REC-INV-TR-003", response.getOptions().get(0).getLegs().get(0).getId());
        assertEquals(OptionLabel.SAFEST, response.getOptions().get(1).getLabel());
        assertEquals("BKG-REC-INV-TR-003", response.getOptions().get(1).getLegs().get(0).getId());
        assertTrue(response.getOptions().get(1).getBestAvailableNote().contains("Same result as FASTEST"));
    }

    @Test
    @DisplayName("Demo scenario returns 3 distinct options (flight, bus, train) and at least 3 rejected options")
    void testDemoScenarioReturnsThreeDistinctOptionsAndThreeRejected() {
        LocalDateTime disruptionTime = baseDate.withHour(9).withMinute(0);
        int stationBufferMinutes = 45;
        LocalDateTime earliestFeasible = disruptionTime.plusMinutes(stationBufferMinutes);

        // Feasible flight, bus, train
        InventoryItem flight = InventoryItem.builder()
                .id("INV-FL-001")
                .mode(BookingType.FLIGHT)
                .provider("IndiGo 6E-6512")
                .departure(baseDate.withHour(13).withMinute(15))
                .arrival(baseDate.withHour(14).withMinute(25))
                .cost(new BigDecimal("6200.00"))
                .seatsAvailable(5)
                .reliabilityScore(92)
                .build();

        InventoryItem bus = InventoryItem.builder()
                .id("INV-BS-001")
                .mode(BookingType.BUS)
                .provider("KSRTC Airavat")
                .departure(baseDate.withHour(10).withMinute(45))
                .arrival(baseDate.withHour(17).withMinute(15))
                .cost(new BigDecimal("1250.00"))
                .seatsAvailable(14)
                .reliabilityScore(86)
                .build();

        InventoryItem train = InventoryItem.builder()
                .id("INV-TR-003")
                .mode(BookingType.TRAIN)
                .provider("Panchaganga Express")
                .departure(baseDate.withHour(10).withMinute(15))
                .arrival(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("2100.00"))
                .seatsAvailable(22)
                .reliabilityScore(96)
                .build();

        // 3 Rejected items
        InventoryItem earlyTrain = InventoryItem.builder()
                .id("INV-TR-001")
                .mode(BookingType.TRAIN)
                .provider("Early Train")
                .departure(baseDate.withHour(7).withMinute(30))
                .arrival(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("1950.00"))
                .seatsAvailable(10)
                .reliabilityScore(95)
                .build();

        InventoryItem lateBus = InventoryItem.builder()
                .id("INV-BS-002")
                .mode(BookingType.BUS)
                .provider("Late Bus")
                .departure(baseDate.withHour(11).withMinute(30))
                .arrival(baseDate.withHour(19).withMinute(0))
                .cost(new BigDecimal("750.00"))
                .seatsAvailable(10)
                .reliabilityScore(80)
                .build();

        InventoryItem sleeperBus = InventoryItem.builder()
                .id("INV-BS-003")
                .mode(BookingType.BUS)
                .provider("Overnight Sleeper")
                .departure(baseDate.withHour(22).withMinute(30))
                .arrival(baseDate.plusDays(1).withHour(6).withMinute(15))
                .cost(new BigDecimal("1400.00"))
                .seatsAvailable(8)
                .reliabilityScore(82)
                .build();

        RecoveryResponse response = scoringService.scoreAndRank(
                sampleTrip, disruptedFlight, List.of(flight, bus, train, earlyTrain, lateBus, sleeperBus),
                earliestFeasible, disruptionTime, stationBufferMinutes);

        // Verify exactly 3 distinct top options
        assertEquals(3, response.getOptions().size());
        var modes = response.getOptions().stream().map(o -> o.getLegs().get(0).getType()).toList();
        assertTrue(modes.contains(BookingType.FLIGHT));
        assertTrue(modes.contains(BookingType.BUS));
        assertTrue(modes.contains(BookingType.TRAIN));

        var labels = response.getOptions().stream().map(Option::getLabel).toList();
        assertTrue(labels.contains(OptionLabel.FASTEST));
        assertTrue(labels.contains(OptionLabel.CHEAPEST));
        assertTrue(labels.contains(OptionLabel.SAFEST));

        // Verify SAFEST is train, FASTEST is flight, CHEAPEST is bus
        Option fastest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.FASTEST).findFirst().orElseThrow();
        assertEquals(BookingType.FLIGHT, fastest.getLegs().get(0).getType());

        Option cheapest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.CHEAPEST).findFirst().orElseThrow();
        assertEquals(BookingType.BUS, cheapest.getLegs().get(0).getType());

        Option safest = response.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.SAFEST).findFirst().orElseThrow();
        assertEquals(BookingType.TRAIN, safest.getLegs().get(0).getType());

        // Verify at least 3 rejected options
        assertTrue(response.getRejectedOptions().size() >= 3);
    }
}
