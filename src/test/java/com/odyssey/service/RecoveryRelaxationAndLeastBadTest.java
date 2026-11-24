package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.config.ScoringProperties;
import com.odyssey.dto.request.RelaxConstraintsRequest;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.model.*;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.model.enums.OptionLabel;
import com.odyssey.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RecoveryRelaxationAndLeastBadTest {

    private ScoringService scoringService;
    private DisruptionService disruptionService;
    private TripRepository tripRepository;
    private InventoryService inventoryService;
    private RecoveryProperties recoveryProperties;

    private LocalDateTime tripDate;
    private Trip userTrip;
    private Booking disruptedBus;

    @BeforeEach
    void setUp() {
        recoveryProperties = new RecoveryProperties();
        recoveryProperties.setSearchWindowDays(2);
        ScoringProperties scoringProperties = new ScoringProperties();
        scoringService = new ScoringService(scoringProperties, recoveryProperties);

        tripRepository = Mockito.mock(TripRepository.class);
        inventoryService = Mockito.mock(InventoryService.class);
        DependencyGraph dependencyGraph = Mockito.mock(DependencyGraph.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);

        disruptionService = new DisruptionService(
                tripRepository,
                dependencyGraph,
                notificationService,
                inventoryService,
                scoringService,
                recoveryProperties
        );

        tripDate = LocalDateTime.of(2026, 10, 21, 10, 0);

        disruptedBus = Booking.builder()
                .id("BKG-USER-BUS-01")
                .type(BookingType.BUS)
                .provider("KSRTC Sarige")
                .from("Hubballi")
                .to("Goa")
                .startTime(tripDate)
                .endTime(tripDate.plusHours(4))
                .cost(new BigDecimal("350.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("10000.00"))
                .mustArriveBy(tripDate.withHour(23).withMinute(59))
                .avoidOvernightTravel(true)
                .build();

        userTrip = Trip.builder()
                .id("TRIP-USER-HUB-01")
                .title("Hubballi to Goa Trip")
                .travellerName("Rohan Mehta")
                .travellerPhone("+919876543210")
                .bookings(new ArrayList<>(List.of(disruptedBus)))
                .constraints(constraints)
                .activeDisruptions(new ArrayList<>(List.of(
                        Disruption.builder()
                                .id("DIS-HUB-01")
                                .bookingId(disruptedBus.getId())
                                .type(DisruptionType.CANCELLATION)
                                .disruptionTime(tripDate)
                                .reportedAt(tripDate)
                                .build()
                )))
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById(userTrip.getId())).thenReturn(Optional.of(userTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Cheapest option arriving at 22:54 returns least-bad with noPerfectOption=true and violated constraint named")
    void testCheapestOptionArrivingAt2254ReturnsLeastBadWithNoPerfectOptionTrue() {
        // Cheapest option arrives at 22:54 (outside day-travel window ending at 21:00)
        InventoryItem cheapNightBus = InventoryItem.builder()
                .id("SYN-BS-CHEAP-2254")
                .mode(BookingType.BUS)
                .provider("Hubballi - Goa Late Bus")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.withHour(20).withMinute(24))
                .arrival(tripDate.withHour(22).withMinute(54))
                .cost(new BigDecimal("350.00"))
                .transfers(0)
                .seatsAvailable(20)
                .reliabilityScore(70)
                .source("ESTIMATED")
                .build();

        // Second option arrives even later: 23:45
        InventoryItem laterBus = InventoryItem.builder()
                .id("SYN-BS-LATER-2345")
                .mode(BookingType.BUS)
                .provider("Hubballi - Goa Midnight Bus")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.withHour(21).withMinute(15))
                .arrival(tripDate.withHour(23).withMinute(45))
                .cost(new BigDecimal("400.00"))
                .transfers(0)
                .seatsAvailable(10)
                .reliabilityScore(65)
                .source("ESTIMATED")
                .build();

        when(inventoryService.search(any(), any(), any())).thenReturn(List.of(cheapNightBus, laterBus));

        // Call recovery service without relaxation
        RecoveryResponse response = disruptionService.generateRecoveryOptions(userTrip.getId());

        assertNotNull(response);
        assertTrue(response.isNoPerfectOption(), "Should set noPerfectOption = true when all candidates breach day-travel window");
        assertFalse(response.getOptions().isEmpty(), "Must never leave options area empty when candidates exist");
        assertEquals(2, response.getOptions().size());

        // Check least-bad option: the cheap night bus arriving at 22:54 is the closest to feasible
        Option bestAvailable = response.getOptions().get(0);
        assertEquals("SYN-BS-CHEAP-2254", bestAvailable.getLegs().get(0).getId().replace("BKG-REC-", ""));
        assertEquals(tripDate.withHour(22).withMinute(54), bestAvailable.getArrivalTime());

        // Violated constraint must be named
        assertTrue(bestAvailable.getExplanation().contains("Best available, breaks: Day-travel window")
                || bestAvailable.getExplanation().contains("outside day-travel window"));
        assertTrue(bestAvailable.getConstraintsViolated().stream()
                .anyMatch(v -> v.contains("22:54") && v.contains("day-travel window")));

        // Rejected options should also be populated and sorted closest to feasible
        assertEquals(2, response.getRejectedOptions().size());
        assertEquals("Day-travel window", response.getRejectedOptions().get(0).getBlockedBy());
        assertTrue(response.getRejectedOptions().get(0).getReason().contains("22:54"));
        // First rejected option is the one at 22:54 (closest to 21:00 cutoff)
        assertEquals("SYN-BS-CHEAP-2254", response.getRejectedOptions().get(0).getId());
    }

    @Test
    @DisplayName("Relaxing overnight constraint makes the 22:54 option feasible without changing the saved trip")
    void testRelaxingOvernightMakesFeasibleAndDoesNotChangeSavedTrip() {
        InventoryItem cheapNightBus = InventoryItem.builder()
                .id("SYN-BS-CHEAP-2254")
                .mode(BookingType.BUS)
                .provider("Hubballi - Goa Late Bus")
                .from("Hubballi")
                .to("Goa")
                .departure(tripDate.withHour(20).withMinute(24))
                .arrival(tripDate.withHour(22).withMinute(54))
                .cost(new BigDecimal("350.00"))
                .transfers(0)
                .seatsAvailable(20)
                .reliabilityScore(70)
                .source("ESTIMATED")
                .build();

        when(inventoryService.search(any(), any(), any())).thenReturn(List.of(cheapNightBus));

        // 1. Initial search: no options feasible
        RecoveryResponse strictResponse = disruptionService.generateRecoveryOptions(userTrip.getId());
        assertTrue(strictResponse.isNoPerfectOption());

        // 2. Relaxed search: allow overnight travel
        RelaxConstraintsRequest relaxRequest = RelaxConstraintsRequest.builder()
                .allowOvernight(true)
                .build();

        RecoveryResponse relaxedResponse = disruptionService.generateRecoveryOptions(userTrip.getId(), relaxRequest);

        assertNotNull(relaxedResponse);
        assertFalse(relaxedResponse.isNoPerfectOption(), "Relaxing overnight travel must make the 22:54 option feasible");
        assertEquals(2, relaxedResponse.getOptions().size());
        assertEquals(OptionLabel.FASTEST, relaxedResponse.getOptions().get(0).getLabel());
        assertEquals("SYN-BS-CHEAP-2254", relaxedResponse.getOptions().get(0).getLegs().get(0).getId().replace("BKG-REC-", ""));
        assertEquals(OptionLabel.SAFEST, relaxedResponse.getOptions().get(1).getLabel());
        assertEquals("SYN-BS-CHEAP-2254", relaxedResponse.getOptions().get(1).getLegs().get(0).getId().replace("BKG-REC-", ""));
        assertTrue(relaxedResponse.getOptions().get(1).getBestAvailableNote().contains("Same result as FASTEST"));
        assertNotNull(relaxedResponse.getRelaxationApplied());
        assertTrue(relaxedResponse.getRelaxationApplied().contains("overnight"));

        // 3. Confirm the saved trip in DB was NOT changed
        assertTrue(userTrip.getConstraints().getAvoidOvernightTravel(),
                "Saved trip constraints in DB must remain avoidOvernightTravel = true");
    }

    @Test
    @DisplayName("Future user trip earliest feasible departure searches from trip date, not from now")
    void testFutureUserTripSearchesFromTripDate() {
        LocalDateTime futureDate = LocalDateTime.now().plusDays(5).withHour(11).withMinute(0);
        userTrip.getBookings().get(0).setStartTime(futureDate);

        when(inventoryService.search(any(), any(), any())).thenAnswer(invocation -> {
            LocalDateTime earliestDepartureArg = invocation.getArgument(2);
            assertEquals(futureDate, earliestDepartureArg,
                    "Earliest feasible departure for a future user trip must search from trip date, not from now");
            return List.of();
        });

        disruptionService.generateRecoveryOptions(userTrip.getId());
    }
}
