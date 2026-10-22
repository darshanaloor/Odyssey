package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.config.StatusProperties;
import com.odyssey.controller.StatusController;
import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.request.SimulateStatusRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.service.status.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusCheckAndDisruptionDetectionTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private StatusProviderSelector statusProviderSelector;

    @Mock
    private BookingJpaRepository bookingJpaRepository;

    @Mock
    private TripAccessService tripAccessService;

    private DependencyGraph dependencyGraph;
    private NotificationService notificationService;
    private RecoveryProperties recoveryProperties;
    private StatusProperties statusProperties;
    private SimulatedStatusProvider simulatedStatusProvider;
    private StatusCheckService statusCheckService;
    private StatusController statusController;

    private Trip testTrip;
    private Booking testBooking;

    private StatusProvider providerWith(StatusState state, int delayMinutes, String error) {
        return new StatusProvider() {
            @Override
            public StatusResult getStatus(Booking booking) {
                return StatusResult.builder()
                        .bookingId(booking.getId())
                        .state(state)
                        .delayMinutes(delayMinutes)
                        .source("SIMULATED")
                        .checkedAt(LocalDateTime.now())
                        .error(error)
                        .build();
            }

            @Override
            public boolean supports(Booking booking) {
                return true;
            }
        };
    }

    @BeforeEach
    void setUp() {
        dependencyGraph = new DependencyGraph();
        notificationService = new NotificationService();
        recoveryProperties = new RecoveryProperties();
        recoveryProperties.setDelayThresholdMinutes(30);

        statusProperties = new StatusProperties();
        statusProperties.setCheckIntervalMinutes(5);
        statusProperties.setLookaheadHours(24);

        simulatedStatusProvider = new SimulatedStatusProvider();

        statusCheckService = new StatusCheckService(
                tripRepository,
                statusProviderSelector,
                dependencyGraph,
                notificationService,
                recoveryProperties,
                statusProperties
        );

        statusController = new StatusController(
                statusProviderSelector,
                statusCheckService,
                simulatedStatusProvider,
                bookingJpaRepository,
                tripRepository,
                tripAccessService
        );

        LocalDateTime now = LocalDateTime.now();
        testBooking = Booking.builder()
                .id("BKG-001")
                .type(BookingType.BUS)
                .provider("KSRTC Express")
                .from("Hubballi")
                .to("Goa")
                .startTime(now.plusHours(2))
                .endTime(now.plusHours(6))
                .cost(new BigDecimal("650.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        testTrip = Trip.builder()
                .id("TRIP-USER-100")
                .title("Hubballi to Goa Getaway")
                .status("CONFIRMED")
                .travellerName("Rohan Sharma")
                .travellerPhone("+919876543210")
                .bookings(new ArrayList<>(List.of(testBooking)))
                .activeDisruptions(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .options(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Delay above threshold creates automatic disruption, runs impact analysis, and logs audit entries")
    void testDelayAboveThresholdCreatesDisruption() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 45, null));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals("DISRUPTED", testTrip.getStatus());
        assertEquals(1, testTrip.getActiveDisruptions().size());

        Disruption d = testTrip.getActiveDisruptions().get(0);
        assertEquals(DisruptionType.FLIGHT_DELAY, d.getType());
        assertEquals("BKG-001", d.getBookingId());
        assertEquals(45, d.getDelayMinutes());

        assertEquals(BookingStatus.DELAYED, testBooking.getStatus());
        assertEquals("DELAYED", testBooking.getLastStatusState());
        assertEquals(45, testBooking.getLastDelayMinutes());
        assertEquals("BKG-001_FLIGHT_DELAY_30", testBooking.getLastDedupKey());

        // Check audit entries: STATUS_CHECKED, DISRUPTION_AUTO_DETECTED, IMPACT_ANALYSIS_COMPLETED, NOTIFICATION_DISPATCHED
        List<AuditEntry> auditList = testTrip.getAuditLog();
        assertTrue(auditList.stream().anyMatch(a -> "STATUS_CHECKED".equals(a.getAction()) && a.getActor() == AuditActor.SYSTEM));
        AuditEntry autoDetectAudit = auditList.stream()
                .filter(a -> "DISRUPTION_AUTO_DETECTED".equals(a.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(autoDetectAudit);
        assertEquals(AuditActor.SYSTEM, autoDetectAudit.getActor());
        assertTrue(autoDetectAudit.getDetails().contains("SIMULATED"));
        assertTrue(autoDetectAudit.getDetails().contains("45 mins"));
        assertTrue(autoDetectAudit.getDetails().contains("30 mins")); // band

        assertTrue(auditList.stream().anyMatch(a -> "IMPACT_ANALYSIS_COMPLETED".equals(a.getAction())));
        assertTrue(auditList.stream().anyMatch(a -> "NOTIFICATION_DISPATCHED".equals(a.getAction())));
    }

    @Test
    @DisplayName("Delay below threshold updates booking status fields but creates no disruption")
    void testDelayBelowThresholdCreatesNoDisruption() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 20, null));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals("CONFIRMED", testTrip.getStatus());
        assertEquals(0, testTrip.getActiveDisruptions().size());
        assertEquals(BookingStatus.CONFIRMED, testBooking.getStatus());
        assertEquals("DELAYED", testBooking.getLastStatusState());
        assertEquals(20, testBooking.getLastDelayMinutes());
        assertNull(testBooking.getLastDedupKey());
    }

    @Test
    @DisplayName("Cancellation creates automatic CANCELLATION disruption")
    void testCancellationCreatesDisruption() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.CANCELLED, 0, null));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals("DISRUPTED", testTrip.getStatus());
        assertEquals(1, testTrip.getActiveDisruptions().size());
        assertEquals(DisruptionType.CANCELLATION, testTrip.getActiveDisruptions().get(0).getType());
        assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
    }

    @Test
    @DisplayName("ON_TIME status updates tracking fields and creates no disruption")
    void testOnTimeChangesNothing() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.ON_TIME, 0, null));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals("CONFIRMED", testTrip.getStatus());
        assertEquals(0, testTrip.getActiveDisruptions().size());
        assertEquals("ON_TIME", testBooking.getLastStatusState());
        assertEquals(0, testBooking.getLastDelayMinutes());
    }

    @Test
    @DisplayName("UNKNOWN status records error only and never creates a disruption")
    void testUnknownChangesNothingAndRecordsError() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.UNKNOWN, 0, "Carrier status unavailable"));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals("CONFIRMED", testTrip.getStatus());
        assertEquals(0, testTrip.getActiveDisruptions().size());
        assertEquals("UNKNOWN", testBooking.getLastStatusState());
        assertEquals("Carrier status unavailable", testBooking.getStatusCheckError());
    }

    @Test
    @DisplayName("Provider throwing an exception is handled safely and records error")
    void testProviderExceptionHandledGracefully() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenThrow(new RuntimeException("Connection timeout"));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(1, report.getResults().size());
        assertEquals(StatusState.UNKNOWN, report.getResults().get(0).getState());
        assertEquals("CONFIRMED", testTrip.getStatus());
        assertEquals(0, testTrip.getActiveDisruptions().size());
        assertTrue(testBooking.getStatusCheckError().contains("Connection timeout"));
    }

    @Test
    @DisplayName("Flight parser parses flight codes correctly")
    void testFlightCodeParsing() {
        assertEquals("6E534", LiveFlightStatusProvider.parseFlightNumber("IndiGo 6E-534"));
        assertEquals("AI102", LiveFlightStatusProvider.parseFlightNumber("Air India AI 102"));
        assertEquals("UK981", LiveFlightStatusProvider.parseFlightNumber("Vistara UK-981"));
        assertNull(LiveFlightStatusProvider.parseFlightNumber("Invalid Provider"));
    }

    @Test
    @DisplayName("Deduplication prevents second disruption on repeat scheduler run")
    void testDeduplicationAcrossRuns() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 40, null));

        // First run
        statusCheckService.checkTripNow(testTrip.getId());
        assertEquals(1, testTrip.getActiveDisruptions().size());

        // Second run with same delay
        statusCheckService.checkTripNow(testTrip.getId());
        assertEquals(1, testTrip.getActiveDisruptions().size(), "Should not create duplicate disruption");
    }

    @Test
    @DisplayName("Larger delay band creates updated disruption and clears stale recovery options")
    void testLargerDelayBandCreatesUpdatedDisruption() {
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 35, null));

        // Run 1: 35 min delay (band 30)
        statusCheckService.checkTripNow(testTrip.getId());
        assertEquals(1, testTrip.getActiveDisruptions().size());

        // Add a dummy stale recovery option
        testTrip.getOptions().add(Option.builder().id("OPT-1").build());
        assertEquals(1, testTrip.getOptions().size());

        // Run 2: 95 min delay (band 90)
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 95, null));

        statusCheckService.checkTripNow(testTrip.getId());

        assertEquals(2, testTrip.getActiveDisruptions().size());
        assertTrue(testTrip.getOptions().isEmpty(), "Stale options should be cleared");
        assertEquals("Status changed, please search again", testTrip.getRecoveryNote());
        assertEquals("BKG-001_FLIGHT_DELAY_90", testBooking.getLastDedupKey());
    }

    @Test
    @DisplayName("RESOLVED trip is skipped completely")
    void testResolvedTripIsSkipped() {
        testTrip.setStatus("RESOLVED");
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(testTrip.getId());

        assertTrue(report.getResults().isEmpty());
        assertTrue(report.getNote().contains("RESOLVED"));
        verifyNoInteractions(statusProviderSelector);
    }

    @Test
    @DisplayName("Simulate endpoint returns 409 in LIVE mode, 404 for missing booking, and 400 for negative delay")
    void testSimulateEndpointValidations() {
        // Case 1: LIVE mode returns 409
        when(statusProviderSelector.isLiveMode()).thenReturn(true);
        SimulateStatusRequest req = SimulateStatusRequest.builder()
                .bookingId("BKG-001")
                .state("DELAYED")
                .delayMinutes(45)
                .build();

        assertThrows(ConflictException.class, () -> statusController.simulateStatus(req));

        // Case 2: SIMULATED mode but missing booking returns 404
        when(statusProviderSelector.isLiveMode()).thenReturn(false);
        when(bookingJpaRepository.findById("BKG-UNKNOWN")).thenReturn(Optional.empty());

        SimulateStatusRequest missingReq = SimulateStatusRequest.builder()
                .bookingId("BKG-UNKNOWN")
                .state("DELAYED")
                .delayMinutes(45)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> statusController.simulateStatus(missingReq));

        // Case 3: SIMULATED mode with negative delay returns 400
        BookingEntity bookingEntity = BookingEntity.builder()
                .id("BKG-001")
                .trip(TripEntity.builder().id("TRIP-100").build())
                .build();
        when(bookingJpaRepository.findById("BKG-001")).thenReturn(Optional.of(bookingEntity));
        when(tripRepository.existsById("TRIP-100")).thenReturn(true);
        when(tripAccessService.requireOwner("TRIP-100")).thenReturn(testTrip);

        SimulateStatusRequest negativeDelayReq = SimulateStatusRequest.builder()
                .bookingId("BKG-001")
                .state("DELAYED")
                .delayMinutes(-10)
                .build();

        assertThrows(IllegalArgumentException.class, () -> statusController.simulateStatus(negativeDelayReq));

        // Case 4: Valid simulation succeeds
        SimulateStatusRequest validReq = SimulateStatusRequest.builder()
                .bookingId("BKG-001")
                .state("DELAYED")
                .delayMinutes(90)
                .build();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = statusController.simulateStatus(validReq);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals("DELAYED", response.getBody().getData().get("state"));
        assertEquals(90, response.getBody().getData().get("delayMinutes"));
    }

    @Test
    @DisplayName("New trip with all ON_TIME bookings stays clean after check: 0 disruptions, status unchanged, 0 options")
    void testNewTripStaysCleanAfterStatusCheck() {
        Trip newTrip = Trip.builder()
                .id("TRIP-CLEAN-001")
                .status("CONFIRMED")
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id("TRIP-CLEAN-001-BKG-1")
                                .type(BookingType.FLIGHT)
                                .provider("Akasa Air QP-1102")
                                .from("BLR")
                                .to("BOM")
                                .startTime(LocalDateTime.now().plusHours(2))
                                .endTime(LocalDateTime.now().plusHours(4))
                                .status(BookingStatus.CONFIRMED)
                                .lastStatusState("ON_TIME")
                                .lastDelayMinutes(0)
                                .lastStatusSource("SIMULATED")
                                .build()
                )))
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById(newTrip.getId())).thenReturn(Optional.of(newTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));
        when(statusProviderSelector.getProvider(any(Booking.class)))
                .thenReturn(providerWith(StatusState.ON_TIME, 0, null));

        StatusCheckService.StatusCheckReport report = statusCheckService.checkTripNow(newTrip.getId());

        assertNotNull(report);
        assertEquals(1, report.getResults().size());
        assertEquals(StatusState.ON_TIME, report.getResults().get(0).getState());
        assertEquals("CONFIRMED", newTrip.getStatus(), "Trip status must remain CONFIRMED");
        assertTrue(newTrip.getActiveDisruptions().isEmpty(), "No disruptions should be created for ON_TIME bookings");
        assertTrue(newTrip.getOptions().isEmpty(), "No recovery options should be generated");
    }

    @Test
    @DisplayName("Simulation on Trip A does not affect Trip B with the same route")
    void testSimulationScopedByTripId() {
        Booking bookingA = Booking.builder()
                .id("BKG-COMMON-01")
                .type(BookingType.FLIGHT)
                .from("BLR")
                .to("GOI")
                .startTime(LocalDateTime.now().plusHours(3))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        Booking bookingB = Booking.builder()
                .id("BKG-COMMON-01")
                .type(BookingType.FLIGHT)
                .from("BLR")
                .to("GOI")
                .startTime(LocalDateTime.now().plusHours(3))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        // Simulate 75 min delay scoped to TRIP-ALPHA
        simulatedStatusProvider.setScriptedStatus("TRIP-ALPHA", "BKG-COMMON-01", StatusState.DELAYED, 75);

        StatusResult resultA = simulatedStatusProvider.getStatus("TRIP-ALPHA", bookingA);
        StatusResult resultB = simulatedStatusProvider.getStatus("TRIP-BETA", bookingB);

        assertEquals(StatusState.DELAYED, resultA.getState(), "Trip A must see DELAYED status");
        assertEquals(75, resultA.getDelayMinutes());
        assertEquals("SIMULATED", resultA.getSource());

        assertEquals(StatusState.ON_TIME, resultB.getState(), "Trip B must remain ON_TIME and unaffected by Trip A");
        assertEquals(0, resultB.getDelayMinutes());
        assertEquals("SIMULATED", resultB.getSource());
    }

    @Test
    @DisplayName("Manual problem report creates one disruption with actor USER and source MANUAL")
    void testManualProblemReportCreatesOneDisruption() {
        Booking manualBooking = Booking.builder()
                .id("TRIP-MAN-BKG-1")
                .type(BookingType.FLIGHT)
                .provider("Air India AI-501")
                .from("BLR")
                .to("DEL")
                .startTime(LocalDateTime.now().plusHours(2))
                .endTime(LocalDateTime.now().plusHours(5))
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking downstreamCab = Booking.builder()
                .id("TRIP-MAN-BKG-2")
                .type(BookingType.CAB)
                .provider("Delhi Cab")
                .from("DEL")
                .to("Hotel")
                .startTime(LocalDateTime.now().plusHours(5).plusMinutes(30))
                .endTime(LocalDateTime.now().plusHours(6).plusMinutes(30))
                .status(BookingStatus.CONFIRMED)
                .dependsOn(List.of("TRIP-MAN-BKG-1"))
                .build();

        Trip manualTrip = Trip.builder()
                .id("TRIP-MAN-001")
                .title("Delhi Business Trip")
                .status("CONFIRMED")
                .bookings(new ArrayList<>(List.of(manualBooking, downstreamCab)))
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>(List.of(Option.builder().id("OLD-OPT-1").build())))
                .auditLog(new ArrayList<>())
                .build();

        when(tripRepository.findById("TRIP-MAN-001")).thenReturn(Optional.of(manualTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));

        InventoryService mockInventory = mock(InventoryService.class);
        ScoringService mockScoring = mock(ScoringService.class);
        DisruptionService disruptionService = new DisruptionService(
                tripRepository,
                dependencyGraph,
                notificationService,
                mockInventory,
                mockScoring,
                recoveryProperties
        );

        ReportDisruptionRequest reportReq = ReportDisruptionRequest.builder()
                .bookingId("TRIP-MAN-BKG-1")
                .type(DisruptionType.CANCELLATION)
                .delayMinutes(0)
                .reason("Flight cancelled by airline at gate")
                .actor(AuditActor.USER)
                .source("MANUAL")
                .build();

        var response = disruptionService.reportDisruption("TRIP-MAN-001", reportReq);

        assertNotNull(response);
        assertEquals(1, manualTrip.getActiveDisruptions().size(), "Must create exactly one disruption");
        Disruption d = manualTrip.getActiveDisruptions().get(0);
        assertEquals("TRIP-MAN-BKG-1", d.getBookingId());
        assertEquals(DisruptionType.CANCELLATION, d.getType());
        assertEquals("MANUAL", d.getSource(), "Source must be MANUAL");
        assertEquals("USER", d.getActor(), "Actor must be USER");
        assertEquals("DISRUPTED", manualTrip.getStatus(), "Trip status must change to DISRUPTED");
        assertEquals(BookingStatus.CANCELLED, manualBooking.getStatus());
        assertEquals(BookingStatus.CANCELLED, downstreamCab.getStatus(), "BFS impact must mark downstream cab CANCELLED when upstream is cancelled");
        assertTrue(manualTrip.getOptions().isEmpty(), "Stale options must be cleared");
        assertEquals("Status changed, please search again", manualTrip.getRecoveryNote());

        boolean userAuditFound = manualTrip.getAuditLog().stream()
                .anyMatch(a -> a.getActor() == AuditActor.USER && "DISRUPTION_RECORDED".equals(a.getAction()));
        assertTrue(userAuditFound, "Audit log must contain entry with actor USER");
    }

    @Test
    @DisplayName("Manual check trip status endpoint does not dispatch alerts to email/sms")
    void testCheckTripStatusEndpointDoesNotDispatchNotifications() {
        when(tripAccessService.requireOwner(testTrip.getId())).thenReturn(testTrip);
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 45, null));

        var response = statusController.checkTripStatus(testTrip.getId());
        assertEquals(200, response.getStatusCode().value());

        // Audit log should contain STATUS_CHECKED, DISRUPTION_AUTO_DETECTED, IMPACT_ANALYSIS_COMPLETED
        // but NOT NOTIFICATION_DISPATCHED because dispatchAlert is false for Check Status Now
        List<AuditEntry> auditList = testTrip.getAuditLog();
        assertTrue(auditList.stream().anyMatch(a -> "STATUS_CHECKED".equals(a.getAction())));
        assertFalse(auditList.stream().anyMatch(a -> "NOTIFICATION_DISPATCHED".equals(a.getAction())),
                "Check status now must NOT dispatch alerts to email and phone");
    }

    @Test
    @DisplayName("Apply simulation triggers immediate status check and alert dispatch")
    void testSimulateStatusTriggersImmediateAlertDispatch() {
        BookingEntity bookingEntity = BookingEntity.builder()
                .id(testBooking.getId())
                .trip(TripEntity.builder().id(testTrip.getId()).build())
                .build();

        when(bookingJpaRepository.findById(testBooking.getId())).thenReturn(Optional.of(bookingEntity));
        when(tripRepository.existsById(testTrip.getId())).thenReturn(true);
        when(tripAccessService.requireOwner(testTrip.getId())).thenReturn(testTrip);
        when(tripRepository.findById(testTrip.getId())).thenReturn(Optional.of(testTrip));
        when(statusProviderSelector.getProvider(any())).thenReturn(providerWith(StatusState.DELAYED, 60, null));

        SimulateStatusRequest req = SimulateStatusRequest.builder()
                .bookingId(testBooking.getId())
                .state("DELAYED")
                .delayMinutes(60)
                .build();

        var response = statusController.simulateStatus(req);
        assertEquals(200, response.getStatusCode().value());

        // Upon applying simulation, alert dispatch is triggered immediately
        List<AuditEntry> auditList = testTrip.getAuditLog();
        assertTrue(auditList.stream().anyMatch(a -> "NOTIFICATION_DISPATCHED".equals(a.getAction())),
                "Apply simulation must trigger immediate alert dispatch to email and mobile");
    }
}
