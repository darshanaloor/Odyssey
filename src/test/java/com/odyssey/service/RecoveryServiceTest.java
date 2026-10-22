package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
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

class RecoveryServiceTest {

    private TripRepository tripRepository;
    private RecoveryProperties recoveryProperties;
    private LlmService llmService;
    private RecoveryService recoveryService;

    private Trip sampleTrip;
    private Option safestTrainOption;
    private Option lateOption;
    private LocalDateTime baseDate;

    @BeforeEach
    void setUp() {
        tripRepository = Mockito.mock(TripRepository.class);
        recoveryProperties = new RecoveryProperties();
        recoveryProperties.setReplacementCabCost(new BigDecimal("400.00"));
        recoveryProperties.setReplacementCabBufferMinutes(30);
        recoveryProperties.setAssumeFullRefundFlight(true);
        recoveryProperties.setAssumeFullRefundCab(true);
        recoveryProperties.setDemoDisruptionTime(LocalDateTime.of(2026, 10, 21, 9, 0, 0));
        recoveryProperties.setStationBufferMinutes(45);

        llmService = new RuleBasedLlmService();
        recoveryService = new RecoveryService(tripRepository, recoveryProperties, llmService);

        baseDate = LocalDateTime.of(2026, 10, 21, 0, 0);

        Booking flight = Booking.builder()
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

        Booking cab = Booking.builder()
                .id("BKG-102")
                .type(BookingType.CAB)
                .provider("Airport Taxi")
                .from("IXE")
                .to("The Gateway Hotel")
                .startTime(baseDate.withHour(12).withMinute(0))
                .endTime(baseDate.withHour(12).withMinute(45))
                .cost(new BigDecimal("800.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking hotel = Booking.builder()
                .id("BKG-103")
                .type(BookingType.HOTEL)
                .provider("The Gateway Hotel, Old Port Road, Mangaluru")
                .from("The Gateway Hotel, Old Port Road, Mangaluru")
                .to("The Gateway Hotel, Old Port Road, Mangaluru")
                .startTime(baseDate.withHour(13).withMinute(30))
                .endTime(baseDate.plusDays(1).withHour(11).withMinute(0))
                .cost(new BigDecimal("6000.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking event = Booking.builder()
                .id("BKG-104")
                .type(BookingType.EVENT)
                .provider("Family Gathering at Ocean Pearl Banquet")
                .from("Ocean Pearl Banquet")
                .to("Ocean Pearl Banquet")
                .startTime(baseDate.withHour(18).withMinute(0))
                .endTime(baseDate.withHour(22).withMinute(0))
                .cost(new BigDecimal("1500.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking trainLeg = Booking.builder()
                .id("INV-TR-003")
                .type(BookingType.TRAIN)
                .provider("Panchaganga Express (16595)")
                .from("BENGALURU")
                .to("MANGALURU")
                .startTime(baseDate.withHour(10).withMinute(15))
                .endTime(baseDate.withHour(15).withMinute(30))
                .cost(new BigDecimal("2100.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        safestTrainOption = Option.builder()
                .id("OPT-SAFEST-INV-TR-003")
                .label(OptionLabel.SAFEST)
                .legs(List.of(trainLeg))
                .totalCost(new BigDecimal("2100.00"))
                .arrivalTime(baseDate.withHour(15).withMinute(30))
                .score(88.0)
                .build();

        Booking lateBusLeg = Booking.builder()
                .id("INV-BUS-LATE")
                .type(BookingType.BUS)
                .provider("Late Evening Bus")
                .from("BENGALURU")
                .to("MANGALURU")
                .startTime(baseDate.withHour(10).withMinute(0))
                .endTime(baseDate.withHour(17).withMinute(45))
                .cost(new BigDecimal("1100.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        lateOption = Option.builder()
                .id("OPT-LATE")
                .label(OptionLabel.CHEAPEST)
                .legs(List.of(lateBusLeg))
                .totalCost(new BigDecimal("1100.00"))
                .arrivalTime(baseDate.withHour(17).withMinute(45))
                .score(60.0)
                .build();

        sampleTrip = Trip.builder()
                .id("TRIP-TEST-001")
                .title("Bengaluru to Mangaluru")
                .travellerName("Ananya Rao")
                .travellerPhone("+91-9876543210")
                .status("DISRUPTED")
                .bookings(new ArrayList<>(List.of(flight, cab, hotel, event)))
                .options(new ArrayList<>(List.of(safestTrainOption, lateOption)))
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(baseDate.withHour(17).withMinute(30))
                        .build())
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById("TRIP-TEST-001")).thenReturn(Optional.of(sampleTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Retiming: Option arriving at 15:30 retimes cab to 16:00, hotel to 16:45, event remains SAFE")
    void testRetimingCalculations() {
        RecoveryPlan plan = recoveryService.buildPlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003");

        assertNotNull(plan);
        assertEquals("OPT-SAFEST-INV-TR-003", plan.getChosenOptionId());

        // Cab pickup = 15:30 + 30m = 16:00; dropoff = 16:00 + 45m = 16:45
        assertNotNull(plan.getReplacementCab());
        assertEquals(baseDate.withHour(16).withMinute(0), plan.getReplacementCab().getStartTime());
        assertEquals(baseDate.withHour(16).withMinute(45), plan.getReplacementCab().getEndTime());

        // Hotel check-in retimed to 16:45 in itinerary diff
        ItineraryDiffItem hotelDiff = plan.getItineraryDiff().stream()
                .filter(d -> d.getType() == BookingType.HOTEL)
                .findFirst()
                .orElseThrow();
        assertEquals(baseDate.withHour(16).withMinute(45), hotelDiff.getNewStart());

        // Event ready time: 16:45 + 30m = 17:15 <= 18:00 -> SAFE
        ItineraryDiffItem eventDiff = plan.getItineraryDiff().stream()
                .filter(d -> d.getType() == BookingType.EVENT)
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.SAFE, eventDiff.getNewStatus());
        assertTrue(eventDiff.getChangeDescription().contains("Attendance preserved"));
    }

    @Test
    @DisplayName("Plan shows PROPOSED status for new legs and leaves trip bookings unchanged")
    void testPlanShowsProposedStatusAndIsolatesTrip() {
        int originalBookingCount = sampleTrip.getBookings().size();
        assertEquals("DISRUPTED", sampleTrip.getStatus());

        RecoveryPlan plan = recoveryService.buildPlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003");

        assertNotNull(plan);
        // Verify PROPOSED on plan objects
        assertEquals(BookingStatus.PROPOSED, plan.getNewLeg().getStatus());
        assertEquals(BookingStatus.PROPOSED, plan.getReplacementCab().getStatus());

        // Verify PROPOSED in itinerary diff
        ItineraryDiffItem newLegDiff = plan.getItineraryDiff().stream()
                .filter(d -> d.getBookingId().equals(plan.getNewLeg().getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.PROPOSED, newLegDiff.getNewStatus());

        ItineraryDiffItem cabDiff = plan.getItineraryDiff().stream()
                .filter(d -> d.getBookingId().equals(plan.getReplacementCab().getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.PROPOSED, cabDiff.getNewStatus());

        // Verify trip itself is completely unmutated
        assertEquals("DISRUPTED", sampleTrip.getStatus());
        assertEquals(originalBookingCount, sampleTrip.getBookings().size());
    }

    @Test
    @DisplayName("Checklist order matches exact requirements (1: leg, 2: cab, 3: hotel, 4: cancel cab, 5: refund, 6: family)")
    void testChecklistOrder() {
        RecoveryPlan plan = recoveryService.buildPlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003");
        List<ActionChecklistItem> checklist = plan.getChecklist();

        assertEquals(6, checklist.size());
        assertEquals("BOOK_NEW_LEG", checklist.get(0).getId());
        assertEquals("BOOK_REPLACEMENT_CAB", checklist.get(1).getId());
        assertEquals("MESSAGE_HOTEL", checklist.get(2).getId());
        assertEquals("CANCEL_OLD_CAB", checklist.get(3).getId());
        assertEquals("REQUEST_FLIGHT_REFUND", checklist.get(4).getId());
        assertEquals("CONFIRM_FAMILY", checklist.get(5).getId());
    }

    @Test
    @DisplayName("Approve commits: new leg/cab and retimed hotel become REBOOKED, event stays SAFE, trip RESOLVED")
    void testApprovePlanCommits() {
        int originalBookingCount = sampleTrip.getBookings().size();

        RecoveryPlan approvedPlan = recoveryService.approvePlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003");

        assertNotNull(approvedPlan);
        assertEquals("RESOLVED", sampleTrip.getStatus());
        assertEquals(originalBookingCount + 2, sampleTrip.getBookings().size());

        // Verify hotel booking status became REBOOKED
        Booking hotel = sampleTrip.getBookings().stream()
                .filter(b -> b.getId().equals("BKG-103"))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.REBOOKED, hotel.getStatus());

        // Verify replacement bookings became REBOOKED
        Booking newLeg = sampleTrip.getBookings().stream()
                .filter(b -> b.getId().equals(approvedPlan.getNewLeg().getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.REBOOKED, newLeg.getStatus());

        Booking cab = sampleTrip.getBookings().stream()
                .filter(b -> b.getId().equals(approvedPlan.getReplacementCab().getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.REBOOKED, cab.getStatus());

        // Verify event stays SAFE
        Booking event = sampleTrip.getBookings().stream()
                .filter(b -> b.getId().equals("BKG-104"))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.SAFE, event.getStatus());

        // Verify checklist items are done
        assertTrue(approvedPlan.getChecklist().stream().allMatch(ActionChecklistItem::isDone));

        // Verify a second approve throws 409 Conflict
        assertThrows(ConflictException.class, () ->
                recoveryService.approvePlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003"));
    }

    @Test
    @DisplayName("Error validation: 404 for unknown trip or option, 409 when RESOLVED")
    void testErrorScenarios() {
        // Unknown trip -> 404
        assertThrows(ResourceNotFoundException.class, () ->
                recoveryService.buildPlan("NON-EXISTENT", "OPT-SAFEST-INV-TR-003"));

        // Unknown optionId -> 404
        assertThrows(ResourceNotFoundException.class, () ->
                recoveryService.buildPlan("TRIP-TEST-001", "OPT-INVALID-UNKNOWN"));

        assertThrows(ResourceNotFoundException.class, () ->
                recoveryService.approvePlan("TRIP-TEST-001", "OPT-INVALID-UNKNOWN"));

        // Already resolved -> 409
        sampleTrip.setStatus("RESOLVED");
        assertThrows(ConflictException.class, () ->
                recoveryService.buildPlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003"));

        assertThrows(ConflictException.class, () ->
                recoveryService.approvePlan("TRIP-TEST-001", "OPT-SAFEST-INV-TR-003"));
    }
}
