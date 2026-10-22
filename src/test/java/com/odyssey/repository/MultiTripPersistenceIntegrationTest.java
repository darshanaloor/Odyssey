package com.odyssey.repository;

import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.RecoveryOptionJpaRepository;
import com.odyssey.repository.jpa.RecoveryPlanJpaRepository;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiTripPersistenceIntegrationTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private RecoveryOptionJpaRepository recoveryOptionJpaRepository;

    @Autowired
    private RecoveryPlanJpaRepository recoveryPlanJpaRepository;

    @Autowired
    private AuditEntryJpaRepository auditEntryJpaRepository;

    @Test
    @DisplayName("Multiple trips store recovery options, plans, and audit entries independently keyed by trip_id")
    void testIndependentTripPersistence() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);

        // 1. Create and save Trip Alpha
        String alphaId = "TRIP-ALPHA-TEST";
        Trip tripAlpha = Trip.builder()
                .id(alphaId)
                .title("Alpha Bengaluru Expedition")
                .travellerName("Alice Voyager")
                .travellerEmail("alice@example.com")
                .status("CONFIRMED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("12000.00"))
                        .mustArriveBy(now.plusHours(8))
                        .avoidOvernightTravel(true)
                        .build())
                .hardCommitments(new ArrayList<>())
                .bookings(new ArrayList<>())
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .build();

        tripAlpha.getHardCommitments().add(HardCommitment.builder()
                .id("HC-A1")
                .name("Alpha Keynote")
                .commitmentTime(now.plusHours(7))
                .location("Mangaluru Town Hall")
                .build());

        tripAlpha.getBookings().add(Booking.builder()
                .id("BKG-A1")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 101")
                .from("Bengaluru")
                .to("Mangaluru")
                .startTime(now)
                .endTime(now.plusHours(1))
                .cost(new BigDecimal("3500.00"))
                .status(BookingStatus.CANCELLED)
                .source("INVENTORY")
                .build());

        tripAlpha.getActiveDisruptions().add(Disruption.builder()
                .id("DIS-A1")
                .bookingId("BKG-A1")
                .type(DisruptionType.CANCELLATION)
                .disruptionTime(now)
                .reason("Technical fault")
                .build());

        tripAlpha.getAuditLog().add(AuditEntry.builder()
                .id("AUD-A1")
                .timestamp(now)
                .actor(AuditActor.SYSTEM)
                .action("DISRUPTION_LOGGED")
                .details("Disruption for Trip Alpha")
                .build());

        Option optAlpha = Option.builder()
                .id("OPT-ALPHA-1")
                .label(OptionLabel.FASTEST)
                .score(92.5)
                .totalCost(new BigDecimal("4200.00"))
                .arrivalTime(now.plusHours(3))
                .explanation("Fastest recovery for Alpha")
                .source("INVENTORY")
                .legs(List.of(Booking.builder()
                        .id("LEG-A1")
                        .type(BookingType.FLIGHT)
                        .provider("Air India 202")
                        .from("Bengaluru")
                        .to("Mangaluru")
                        .startTime(now.plusHours(1))
                        .endTime(now.plusHours(2))
                        .cost(new BigDecimal("4200.00"))
                        .status(BookingStatus.PROPOSED)
                        .source("INVENTORY")
                        .build()))
                .build();
        tripAlpha.getOptions().add(optAlpha);

        RecoveryPlan planAlpha = RecoveryPlan.builder()
                .id("PLAN-ALPHA-1")
                .tripId(alphaId)
                .chosenOptionId(optAlpha.getId())
                .tradeOffExplanation("Alpha trade-off: higher cost for speed")
                .hotelMessage("Alpha: delay notification")
                .costBreakdown(CostBreakdown.builder()
                        .netTripCost(new BigDecimal("4200.00"))
                        .maxBudget(new BigDecimal("12000.00"))
                        .remainingBudget(new BigDecimal("7800.00"))
                        .build())
                .checklist(List.of(ActionChecklistItem.builder()
                        .id("CHK-A1")
                        .text("Alpha: confirm flight rebooking")
                        .done(false)
                        .build()))
                .build();
        tripAlpha.setLatestPlan(planAlpha);

        tripRepository.save(tripAlpha);

        // 2. Create and save Trip Beta (completely independent, synthetic/estimated)
        String betaId = "TRIP-BETA-TEST";
        Trip tripBeta = Trip.builder()
                .id(betaId)
                .title("Beta Hubballi Journey")
                .travellerName("Bob Explorer")
                .travellerPhone("+91-9876543212")
                .status("CONFIRMED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("8000.00"))
                        .mustArriveBy(now.plusHours(6))
                        .avoidOvernightTravel(false)
                        .build())
                .bookings(new ArrayList<>())
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .build();

        tripBeta.getBookings().add(Booking.builder()
                .id("BKG-B1")
                .type(BookingType.BUS)
                .provider("KSRTC Sarige")
                .from("Hubballi")
                .to("Goa")
                .startTime(now.plusHours(1))
                .endTime(now.plusHours(5))
                .cost(new BigDecimal("500.00"))
                .status(BookingStatus.CANCELLED)
                .source("ESTIMATED")
                .build());

        tripBeta.getActiveDisruptions().add(Disruption.builder()
                .id("DIS-B1")
                .bookingId("BKG-B1")
                .type(DisruptionType.FLIGHT_DELAY)
                .delayMinutes(120)
                .disruptionTime(now)
                .reason("Road block")
                .build());

        tripBeta.getAuditLog().add(AuditEntry.builder()
                .id("AUD-B1")
                .timestamp(now)
                .actor(AuditActor.SYSTEM)
                .action("DISRUPTION_LOGGED")
                .details("Disruption for Trip Beta")
                .build());

        Option optBeta = Option.builder()
                .id("OPT-BETA-1")
                .label(OptionLabel.CHEAPEST)
                .score(78.0)
                .totalCost(new BigDecimal("900.00"))
                .arrivalTime(now.plusHours(6))
                .explanation("Cheapest estimated recovery for Beta")
                .source("ESTIMATED")
                .legs(List.of(Booking.builder()
                        .id("LEG-B1")
                        .type(BookingType.BUS)
                        .provider("Estimated Express Bus")
                        .from("Hubballi")
                        .to("Goa")
                        .startTime(now.plusHours(2))
                        .endTime(now.plusHours(6))
                        .cost(new BigDecimal("900.00"))
                        .status(BookingStatus.PROPOSED)
                        .source("ESTIMATED")
                        .build()))
                .build();
        tripBeta.getOptions().add(optBeta);

        RecoveryPlan planBeta = RecoveryPlan.builder()
                .id("PLAN-BETA-1")
                .tripId(betaId)
                .chosenOptionId(optBeta.getId())
                .tradeOffExplanation("Beta trade-off: slower travel time")
                .hotelMessage("Beta: late check-in request")
                .costBreakdown(CostBreakdown.builder()
                        .netTripCost(new BigDecimal("900.00"))
                        .maxBudget(new BigDecimal("8000.00"))
                        .remainingBudget(new BigDecimal("7100.00"))
                        .build())
                .checklist(List.of(ActionChecklistItem.builder()
                        .id("CHK-B1")
                        .text("Beta: board bus at Hubballi")
                        .done(false)
                        .build()))
                .build();
        tripBeta.setLatestPlan(planBeta);

        tripRepository.save(tripBeta);

        // 3. Verify Trip Alpha isolation
        Trip loadedAlpha = tripRepository.findById(alphaId).orElseThrow();
        assertEquals("Alpha Bengaluru Expedition", loadedAlpha.getTitle());
        assertEquals("alice@example.com", loadedAlpha.getTravellerEmail());
        assertEquals(1, loadedAlpha.getHardCommitments().size());
        assertEquals("Alpha Keynote", loadedAlpha.getHardCommitments().get(0).getName());
        assertEquals("Mangaluru Town Hall", loadedAlpha.getHardCommitments().get(0).getLocation());
        assertEquals(1, loadedAlpha.getBookings().size());
        assertEquals("BKG-A1", loadedAlpha.getBookings().get(0).getId());
        assertEquals(1, loadedAlpha.getActiveDisruptions().size());
        assertEquals("DIS-A1", loadedAlpha.getActiveDisruptions().get(0).getId());
        assertEquals(1, loadedAlpha.getOptions().size());
        assertEquals("OPT-ALPHA-1", loadedAlpha.getOptions().get(0).getId());
        assertEquals("INVENTORY", loadedAlpha.getOptions().get(0).getSource());
        assertNotNull(loadedAlpha.getLatestPlan());
        assertEquals("PLAN-ALPHA-1", loadedAlpha.getLatestPlan().getId());
        assertEquals("Alpha trade-off: higher cost for speed", loadedAlpha.getLatestPlan().getTradeOffExplanation());
        assertEquals(1, loadedAlpha.getAuditLog().size());
        assertEquals("AUD-A1", loadedAlpha.getAuditLog().get(0).getId());

        // 4. Verify Trip Beta isolation
        Trip loadedBeta = tripRepository.findById(betaId).orElseThrow();
        assertEquals("Beta Hubballi Journey", loadedBeta.getTitle());
        assertEquals(1, loadedBeta.getBookings().size());
        assertEquals("BKG-B1", loadedBeta.getBookings().get(0).getId());
        assertEquals(1, loadedBeta.getActiveDisruptions().size());
        assertEquals("DIS-B1", loadedBeta.getActiveDisruptions().get(0).getId());
        assertEquals(1, loadedBeta.getOptions().size());
        assertEquals("OPT-BETA-1", loadedBeta.getOptions().get(0).getId());
        assertEquals("ESTIMATED", loadedBeta.getOptions().get(0).getSource());
        assertNotNull(loadedBeta.getLatestPlan());
        assertEquals("PLAN-BETA-1", loadedBeta.getLatestPlan().getId());
        assertEquals("Beta trade-off: slower travel time", loadedBeta.getLatestPlan().getTradeOffExplanation());
        assertEquals(1, loadedBeta.getAuditLog().size());
        assertEquals("AUD-B1", loadedBeta.getAuditLog().get(0).getId());

        // 5. Verify direct database foreign key isolation (trip_id)
        var optionsForAlpha = recoveryOptionJpaRepository.findByTripIdOrderByScoreDesc(alphaId);
        assertEquals(1, optionsForAlpha.size());
        assertEquals("OPT-ALPHA-1", optionsForAlpha.get(0).getId());

        var optionsForBeta = recoveryOptionJpaRepository.findByTripIdOrderByScoreDesc(betaId);
        assertEquals(1, optionsForBeta.size());
        assertEquals("OPT-BETA-1", optionsForBeta.get(0).getId());

        var plansForAlpha = recoveryPlanJpaRepository.findByTripIdOrderByCreatedAtDesc(alphaId);
        assertEquals(1, plansForAlpha.size());
        assertEquals("PLAN-ALPHA-1", plansForAlpha.get(0).getId());

        var plansForBeta = recoveryPlanJpaRepository.findByTripIdOrderByCreatedAtDesc(betaId);
        assertEquals(1, plansForBeta.size());
        assertEquals("PLAN-BETA-1", plansForBeta.get(0).getId());

        var auditsForAlpha = auditEntryJpaRepository.findByTripIdOrderByTimestampAsc(alphaId);
        assertEquals(1, auditsForAlpha.size());
        assertEquals("AUD-A1", auditsForAlpha.get(0).getId());

        var auditsForBeta = auditEntryJpaRepository.findByTripIdOrderByTimestampAsc(betaId);
        assertEquals(1, auditsForBeta.size());
        assertEquals("AUD-B1", auditsForBeta.get(0).getId());

        // 6. Delete Trip Alpha and verify Cascade Delete without affecting Beta
        tripRepository.deleteById(alphaId);

        assertFalse(tripRepository.existsById(alphaId));
        assertTrue(recoveryOptionJpaRepository.findByTripIdOrderByScoreDesc(alphaId).isEmpty(), "Alpha options cascaded");
        assertTrue(recoveryPlanJpaRepository.findByTripIdOrderByCreatedAtDesc(alphaId).isEmpty(), "Alpha plans cascaded");
        assertTrue(auditEntryJpaRepository.findByTripIdOrderByTimestampAsc(alphaId).isEmpty(), "Alpha audits cascaded");

        // Beta still completely intact
        assertTrue(tripRepository.existsById(betaId));
        assertEquals(1, recoveryOptionJpaRepository.findByTripIdOrderByScoreDesc(betaId).size());
        assertEquals(1, recoveryPlanJpaRepository.findByTripIdOrderByCreatedAtDesc(betaId).size());
        assertEquals(1, auditEntryJpaRepository.findByTripIdOrderByTimestampAsc(betaId).size());

        // Cleanup
        tripRepository.deleteById(betaId);
        assertFalse(tripRepository.existsById(betaId));
    }
}
