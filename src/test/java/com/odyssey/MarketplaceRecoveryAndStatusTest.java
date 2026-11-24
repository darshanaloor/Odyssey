package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.ReplacementBookingRequest;
import com.odyssey.dto.market.SimulateDepartureRequest;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.entity.*;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.Option;
import com.odyssey.model.RejectedOption;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.OptionLabel;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.*;
import com.odyssey.service.status.StatusCheckService;
import com.odyssey.service.status.StatusProviderSelector;
import com.odyssey.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MarketplaceRecoveryAndStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketplaceSeeder marketplaceSeeder;

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private StatusCheckService statusCheckService;

    @Autowired
    private StatusProviderSelector statusProviderSelector;

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private TripJpaRepository tripRepo;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private OperatorJpaRepository operatorRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private SeatHoldJpaRepository seatHoldRepo;

    @Autowired
    private AuditEntryJpaRepository auditRepo;

    @Autowired(required = false)
    private DisruptionJpaRepository disruptionRepo;

    @Autowired
    private RecoveryPlanJpaRepository recoveryPlanRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity userA;
    private UserEntity userB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setUp() throws Exception {
        marketplaceSeeder.seedMarketplace();

        userA = createOrGetUser("rec_user_a@example.com", "Password123", "User Alpha", "+919876540001");
        userB = createOrGetUser("rec_user_b@example.com", "Password123", "User Beta", "+919876540002");

        sessionA = loginAs("rec_user_a@example.com", "Password123");
        sessionB = loginAs("rec_user_b@example.com", "Password123");
    }

    private UserEntity createOrGetUser(String email, String password, String name, String phone) {
        return userRepo.findByEmail(email.toLowerCase()).orElseGet(() -> {
            UserEntity u = UserEntity.builder()
                    .id(IdGenerator.generate("USR"))
                    .email(email.toLowerCase())
                    .passwordHash(passwordEncoder.encode(password))
                    .name(name)
                    .phone(phone)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepo.save(u);
        });
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());
        return session;
    }

    @Test
    @DisplayName("Simulating departure delay creates 1 disruption per affected trip and none for unaffected trips")
    void testSimulateDepartureCreatesDisruptionForAffectedTripOnly() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity dep = createTestDeparture("SRV-TEST-STATUS-1", "Jaipur", "Udaipur", travelDate, 40, "BUS");

        // User A creates a trip and holds a booking on this departure
        TripEntity tripA = createTestTrip(userA, "Trip Jaipur to Udaipur");
        createMarketplaceBooking(userA, tripA, dep, "BUS", new BigDecimal("750.00"));

        // User B creates a separate trip with an unrelated booking
        TripEntity tripB = createTestTrip(userB, "Trip Mumbai to Pune");

        // Simulate DELAYED 75 min by User A
        SimulateDepartureRequest req = SimulateDepartureRequest.builder()
                .serviceDepartureId(dep.getId())
                .state("DELAYED")
                .delayMinutes(75)
                .build();

        mockMvc.perform(post("/api/market/simulate/departure")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify departure row was updated
        ServiceDepartureEntity updatedDep = departureRepo.findById(dep.getId()).orElseThrow();
        assertEquals("DELAYED", updatedDep.getStatus());
        assertEquals(75, updatedDep.getDelayMinutes());

        // Verify Trip A has a disruption logged
        List<DisruptionEntity> tripADisruptions = disruptionRepo.findByTripIdOrderByCreatedAtDesc(tripA.getId());
        assertFalse(tripADisruptions.isEmpty(), "Trip A should have at least 1 disruption");
        DisruptionEntity disA = tripADisruptions.get(0);
        assertEquals("SYSTEM", disA.getActor());
        assertTrue(disA.getSource().contains(dep.getService().getVehicleNumber()),
                "Disruption source should contain vehicle number");

        // Verify Trip B has 0 disruptions
        List<DisruptionEntity> tripBDisruptions = disruptionRepo.findByTripIdOrderByCreatedAtDesc(tripB.getId());
        assertTrue(tripBDisruptions.isEmpty(), "Trip B should have no disruptions");
    }

    @Test
    @DisplayName("Scheduler deduplication: repeated status checks do not create duplicate disruptions")
    void testStatusCheckDeduplication() {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity dep = createTestDeparture("SRV-TEST-DEDUP-1", "Jaipur", "Udaipur", travelDate, 40, "BUS");
        dep.setStatus("DELAYED");
        dep.setDelayMinutes(60);
        departureRepo.save(dep);

        TripEntity trip = createTestTrip(userA, "Deduplication Test Trip");
        createMarketplaceBooking(userA, trip, dep, "BUS", new BigDecimal("800.00"));

        // Run check 1
        statusCheckService.checkTripNow(trip.getId());
        int disruptionCount1 = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size();
        assertEquals(1, disruptionCount1, "First check should create 1 disruption");

        // Run check 2 without state change
        statusCheckService.checkTripNow(trip.getId());
        int disruptionCount2 = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size();
        assertEquals(1, disruptionCount2, "Second check with same delay band should NOT duplicate disruption");
    }

    @Test
    @DisplayName("User B cannot simulate status for departure they do not hold a booking on (403 Forbidden)")
    void testUserBForbiddenToSimulateUserADeparture() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity dep = createTestDeparture("SRV-TEST-FORBID-1", "Delhi", "Agra", travelDate, 30, "TRAIN");

        // Only User A books on this departure
        TripEntity tripA = createTestTrip(userA, "User A Trip");
        createMarketplaceBooking(userA, tripA, dep, "TRAIN", new BigDecimal("500.00"));

        // User B tries to simulate status
        SimulateDepartureRequest req = SimulateDepartureRequest.builder()
                .serviceDepartureId(dep.getId())
                .state("CANCELLED")
                .delayMinutes(0)
                .build();

        mockMvc.perform(post("/api/market/simulate/departure")
                        .session(sessionB)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recovery on-demand generation creates MARKETPLACE options and rejects cancelled/sold out departures")
    void testRecoveryWithMarketplaceCandidatesAndRejections() {
        LocalDate travelDate = LocalDate.now().plusDays(3);

        // Disrupted departure
        ServiceDepartureEntity disruptedDep = createTestDeparture("SRV-DISRUPTED-DEP", "Jaipur", "Udaipur", travelDate, 40, "BUS");
        disruptedDep.setStatus("CANCELLED");
        departureRepo.save(disruptedDep);

        // Cancelled recovery candidate
        ServiceDepartureEntity cancelledDep = createTestDeparture("SRV-CANDIDATE-CANC", "Jaipur", "Udaipur", travelDate, 40, "BUS");
        cancelledDep.setStatus("CANCELLED");
        departureRepo.save(cancelledDep);

        // Sold-out recovery candidate
        ServiceDepartureEntity soldOutDep = createTestDeparture("SRV-CANDIDATE-SOLDOUT", "Jaipur", "Udaipur", travelDate, 0, "BUS");
        soldOutDep.setStatus("SCHEDULED");
        departureRepo.save(soldOutDep);

        // Active recovery candidate with seats
        ServiceDepartureEntity validDep = createTestDeparture("SRV-CANDIDATE-VALID", "Jaipur", "Udaipur", travelDate, 25, "BUS");
        validDep.setStatus("SCHEDULED");
        departureRepo.save(validDep);

        TripEntity tripEntity = createTestTrip(userA, "Jaipur to Udaipur Trip");
        BookingEntity disruptedBooking = createMarketplaceBooking(userA, tripEntity, disruptedDep, "BUS", new BigDecimal("700.00"));
        disruptedBooking.setStatus("CANCELLED");
        bookingRepo.save(disruptedBooking);

        // Record a cancellation disruption on the trip so recovery engine knows what to replace
        DisruptionEntity dis = DisruptionEntity.builder()
                .id(IdGenerator.generate("DIS"))
                .trip(tripEntity)
                .bookingId(disruptedBooking.getId())
                .type("CANCELLED")
                .delayMinutes(0)
                .disruptionTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .actor("SYSTEM")
                .source("CARRIER_SIMULATION")
                .reason("Carrier cancellation")
                .build();
        tripEntity.getDisruptions().add(dis);
        tripRepo.save(tripEntity);

        RecoveryResponse result = disruptionService.generateRecoveryOptions(tripEntity.getId());

        assertNotNull(result);
        assertNotNull(result.getOptions());

        // Verify rejected options contain cancelled and sold out reasons
        List<RejectedOption> rejected = result.getRejectedOptions();
        assertNotNull(rejected);
        boolean foundCancelled = rejected.stream().anyMatch(r -> r.getReason() != null && r.getReason().contains("cancelled by carrier"));
        boolean foundSoldOut = rejected.stream().anyMatch(r -> r.getReason() != null && r.getReason().contains("Sold out"));

        assertTrue(foundCancelled, "Rejected options must include cancelled departure reason");
        assertTrue(foundSoldOut, "Rejected options must include sold out reason");

        // Verify valid marketplace option exists
        boolean hasMarketplaceOption = result.getOptions().stream().anyMatch(o -> "MARKETPLACE".equalsIgnoreCase(o.getSource()));
        assertTrue(hasMarketplaceOption, "Should contain at least one MARKETPLACE recovery candidate");
    }

    @Test
    @DisplayName("ESTIMATED options are never SAFEST when MARKETPLACE options exist")
    void testEstimatedNeverSafestWhenMarketplaceExists() {
        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("10000.00"))
                .mustArriveBy(LocalDateTime.now().plusDays(3).withHour(23).withMinute(0))
                .build();

        Trip trip = Trip.builder()
                .id("TRIP-SAFEST-TEST")
                .title("Safest Score Test")
                .constraints(constraints)
                .bookings(new ArrayList<>())
                .build();

        Booking disrupted = Booking.builder()
                .id("BKG-DISRUPTED")
                .type(BookingType.BUS)
                .provider("Previous Bus")
                .cost(new BigDecimal("500.00"))
                .startTime(LocalDateTime.now().plusDays(2).withHour(8).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .from("Jaipur")
                .to("Udaipur")
                .build();

        // One MARKETPLACE inventory item
        InventoryItem marketItem = InventoryItem.builder()
                .id("SRV-MKT-1")
                .serviceDepartureId("DEP-MKT-1")
                .mode(BookingType.BUS)
                .provider("Odyssey Bus Lines")
                .from("Jaipur")
                .to("Udaipur")
                .departure(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .arrival(LocalDateTime.now().plusDays(2).withHour(16).withMinute(0))
                .cost(new BigDecimal("900.00"))
                .source("MARKETPLACE")
                .reliabilityScore(85)
                .seatsAvailable(20)
                .build();

        // One ESTIMATED inventory item with higher reliability
        InventoryItem estimatedItem = InventoryItem.builder()
                .id("SRV-EST-1")
                .mode(BookingType.BUS)
                .provider("Estimated Transit")
                .from("Jaipur")
                .to("Udaipur")
                .departure(LocalDateTime.now().plusDays(2).withHour(10).withMinute(30))
                .arrival(LocalDateTime.now().plusDays(2).withHour(16).withMinute(30))
                .cost(new BigDecimal("850.00"))
                .source("ESTIMATED")
                .reliabilityScore(95)
                .seatsAvailable(10)
                .build();

        RecoveryResponse evaluated = scoringService.scoreAndRank(trip, disrupted, List.of(marketItem, estimatedItem));

        Option safest = evaluated.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.SAFEST).findFirst().orElse(null);
        assertNotNull(safest, "SAFEST option must be evaluated");
        assertEquals("MARKETPLACE", safest.getSource(), "SAFEST option must be MARKETPLACE when one exists");
    }

    @Test
    @DisplayName("Replacement preview returns passenger count, auto-assigned seats, and cost breakdown")
    void testReplacementPreview() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity oldDep = createTestDeparture("SRV-PREV-OLD", "Jaipur", "Udaipur", travelDate, 30, "BUS");
        ServiceDepartureEntity newDep = createTestDeparture("SRV-PREV-NEW", "Jaipur", "Udaipur", travelDate, 20, "BUS");

        TripEntity trip = createTestTrip(userA, "Preview Trip");
        BookingEntity oldBooking = createMarketplaceBooking(userA, trip, oldDep, "BUS", new BigDecimal("750.00"));
        oldBooking.setStatus("CANCELLED");
        bookingRepo.save(oldBooking);

        mockMvc.perform(get("/api/market/replacement/preview")
                        .session(sessionA)
                        .param("tripId", trip.getId())
                        .param("oldBookingId", oldBooking.getId())
                        .param("serviceDepartureId", newDep.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passengerCount").value(1))
                .andExpect(jsonPath("$.data.assignedSeats", hasSize(1)))
                .andExpect(jsonPath("$.data.replacementCost").value(newDep.getService().getPrice().doubleValue()))
                .andExpect(jsonPath("$.data.refundAmount").value(750.00))
                .andExpect(jsonPath("$.data.disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("Atomic replacement booking: sets old to CANCELLED/DEMO_REFUNDED, new to CONFIRMED/DEMO_PAID, logs audit entry")
    void testBookReplacementSuccessfully() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity oldDep = createTestDeparture("SRV-BOOK-OLD", "Jaipur", "Udaipur", travelDate, 30, "BUS");
        ServiceDepartureEntity newDep = createTestDeparture("SRV-BOOK-NEW", "Jaipur", "Udaipur", travelDate, 20, "BUS");

        TripEntity trip = createTestTrip(userA, "Replacement Trip");
        BookingEntity oldBooking = createMarketplaceBooking(userA, trip, oldDep, "BUS", new BigDecimal("700.00"));
        oldBooking.setStatus("CANCELLED");
        bookingRepo.save(oldBooking);

        ReplacementBookingRequest req = ReplacementBookingRequest.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .serviceDepartureId(newDep.getId())
                .build();

        mockMvc.perform(post("/api/market/replacement/book")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.oldBookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.oldPaymentStatus").value("DEMO_REFUNDED"))
                .andExpect(jsonPath("$.data.newBooking.paymentStatus").value("DEMO_PAID"))
                .andExpect(jsonPath("$.data.newBooking.status").value("CONFIRMED"));

        // Verify DB state
        BookingEntity refreshedOld = bookingRepo.findById(oldBooking.getId()).orElseThrow();
        assertEquals("CANCELLED", refreshedOld.getStatus());
        assertEquals("DEMO_REFUNDED", refreshedOld.getPaymentStatus());

        ServiceDepartureEntity refreshedNewDep = departureRepo.findById(newDep.getId()).orElseThrow();
        assertEquals(19, refreshedNewDep.getSeatsAvailable(), "Departure available seats should be decremented by 1");

        // Verify audit entry REPLACEMENT_BOOKED
        List<AuditEntryEntity> audits = auditRepo.findByTripIdOrderByTimestampDesc(trip.getId());
        boolean hasAudit = audits.stream().anyMatch(a -> "REPLACEMENT_BOOKED".equalsIgnoreCase(a.getAction()));
        assertTrue(hasAudit, "Audit log should contain REPLACEMENT_BOOKED action");
    }

    @Test
    @DisplayName("Sold out replacement departure returns 409 Conflict leaving old booking untouched")
    void testSoldOutReplacementReturnsConflict() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity oldDep = createTestDeparture("SRV-SO-OLD", "Jaipur", "Udaipur", travelDate, 30, "BUS");
        ServiceDepartureEntity soldOutDep = createTestDeparture("SRV-SO-NEW", "Jaipur", "Udaipur", travelDate, 0, "BUS");

        TripEntity trip = createTestTrip(userA, "Trip Sold Out Test");
        BookingEntity oldBooking = createMarketplaceBooking(userA, trip, oldDep, "BUS", new BigDecimal("650.00"));
        oldBooking.setStatus("CANCELLED");
        bookingRepo.save(oldBooking);

        ReplacementBookingRequest req = ReplacementBookingRequest.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .serviceDepartureId(soldOutDep.getId())
                .build();

        mockMvc.perform(post("/api/market/replacement/book")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Old booking should still have original payment status
        BookingEntity untouched = bookingRepo.findById(oldBooking.getId()).orElseThrow();
        assertEquals("DEMO_PAID", untouched.getPaymentStatus(), "Old booking payment status should not change to REFUNDED");
    }

    @Test
    @DisplayName("Concurrent replacement onto same departure by two users completes deadlock-free (lock ordering)")
    void testConcurrentReplacementDeadlockFree() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity oldDepA = createTestDeparture("SRV-CONC-A", "Jaipur", "Udaipur", travelDate, 30, "BUS");
        ServiceDepartureEntity oldDepB = createTestDeparture("SRV-CONC-B", "Jaipur", "Udaipur", travelDate, 30, "BUS");
        ServiceDepartureEntity targetDep = createTestDeparture("SRV-CONC-TARGET", "Jaipur", "Udaipur", travelDate, 2, "BUS");

        TripEntity tripA = createTestTrip(userA, "Concurrent Trip A");
        BookingEntity bkgA = createMarketplaceBooking(userA, tripA, oldDepA, "BUS", new BigDecimal("500.00"));
        bkgA.setStatus("CANCELLED");
        bookingRepo.save(bkgA);

        TripEntity tripB = createTestTrip(userB, "Concurrent Trip B");
        BookingEntity bkgB = createMarketplaceBooking(userB, tripB, oldDepB, "BUS", new BigDecimal("500.00"));
        bkgB.setStatus("CANCELLED");
        bookingRepo.save(bkgB);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> taskA = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                mockMvc.perform(post("/api/market/replacement/book")
                                .session(sessionA)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(ReplacementBookingRequest.builder()
                                        .tripId(tripA.getId())
                                        .oldBookingId(bkgA.getId())
                                        .serviceDepartureId(targetDep.getId())
                                        .build())))
                        .andDo(result -> {
                            int sc = result.getResponse().getStatus();
                            if (sc == 200) successCount.incrementAndGet();
                            else if (sc == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception e) {
                // Ignore
            }
            return null;
        };

        Callable<Void> taskB = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                mockMvc.perform(post("/api/market/replacement/book")
                                .session(sessionB)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(ReplacementBookingRequest.builder()
                                        .tripId(tripB.getId())
                                        .oldBookingId(bkgB.getId())
                                        .serviceDepartureId(targetDep.getId())
                                        .build())))
                        .andDo(result -> {
                            int sc = result.getResponse().getStatus();
                            if (sc == 200) successCount.incrementAndGet();
                            else if (sc == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception e) {
                // Ignore
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(taskA, taskB));
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Target departure had 2 seats, both users book 1 seat each -> both should succeed deadlock-free!
        assertEquals(2, successCount.get(), "Both concurrent replacements should succeed without deadlock");

        ServiceDepartureEntity finalDep = departureRepo.findById(targetDep.getId()).orElseThrow();
        assertEquals(0, finalDep.getSeatsAvailable(), "All 2 seats should be booked");
    }

    @Test
    @DisplayName("After replacement, no booking dependsOn references a missing id and plan newLegId points to new booking")
    void testDependsOnConsistencyAndPlanNewLegIdAfterReplacement() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(3);
        ServiceDepartureEntity oldDep = createTestDeparture("SRV-DEP-OLD", "Jaipur", "Udaipur", travelDate, 20, "BUS");
        ServiceDepartureEntity newDep = createTestDeparture("SRV-DEP-NEW", "Jaipur", "Udaipur", travelDate, 20, "BUS");

        TripEntity trip = createTestTrip(userA, "Trip DependsOn Consistency Test");
        BookingEntity oldBooking = createMarketplaceBooking(userA, trip, oldDep, "BUS", new BigDecimal("550.00"));
        oldBooking.setStatus("CANCELLED");
        bookingRepo.save(oldBooking);

        // Placeholder leg created during recovery plan
        BookingEntity placeholderLeg = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(userA)
                .type("BUS")
                .provider("Placeholder Bus")
                .fromLocation("Jaipur")
                .toLocation("Udaipur")
                .startTime(travelDate.atTime(10, 0))
                .endTime(travelDate.atTime(16, 0))
                .cost(new BigDecimal("550.00"))
                .status("PROPOSED")
                .paymentStatus("PENDING")
                .build());
        trip.getBookings().add(placeholderLeg);

        // Downstream cab booking that depended on the placeholder/old booking
        BookingEntity cabBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(userA)
                .type("CAB")
                .provider("Udaipur City Cab")
                .fromLocation("Udaipur Bus Stand")
                .toLocation("Udaipur Hotel")
                .startTime(travelDate.atTime(16, 30))
                .endTime(travelDate.atTime(17, 15))
                .cost(new BigDecimal("350.00"))
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .dependsOn(new ArrayList<>(List.of(placeholderLeg.getId(), oldBooking.getId())))
                .build());
        trip.getBookings().add(cabBooking);

        // Attach recovery plan pointing to placeholder leg
        RecoveryPlanEntity plan = RecoveryPlanEntity.builder()
                .id(IdGenerator.generate("PLN"))
                .trip(trip)
                .optionId("OPT-TEST")
                .newLegId(placeholderLeg.getId())
                .netTripCost(new BigDecimal("550.00"))
                .maxBudget(new BigDecimal("10000.00"))
                .remainingBudget(new BigDecimal("9450.00"))
                .createdAt(LocalDateTime.now())
                .build();
        if (trip.getRecoveryPlans() == null) {
            trip.setRecoveryPlans(new ArrayList<>());
        }
        trip.getRecoveryPlans().add(plan);
        trip = tripRepo.save(trip);

        ReplacementBookingRequest req = ReplacementBookingRequest.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .serviceDepartureId(newDep.getId())
                .build();

        mockMvc.perform(post("/api/market/replacement/book")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Reload trip and all bookings
        TripEntity reloadedTrip = tripRepo.findById(trip.getId()).orElseThrow();
        assertNotNull(reloadedTrip);
        List<BookingEntity> reloadedBookings = bookingRepo.findByTripIdOrderByStartTimeAsc(trip.getId());
        List<RecoveryPlanEntity> reloadedPlans = recoveryPlanRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertFalse(reloadedPlans.isEmpty(), "Recovery plans must exist");

        // 1. Plan newLegId must point to the new booking
        RecoveryPlanEntity latestPlan = reloadedPlans.get(0);
        assertNotNull(latestPlan.getNewLegId(), "Plan newLegId must not be null");
        assertNotEquals(placeholderLeg.getId(), latestPlan.getNewLegId(), "Plan newLegId must no longer point to placeholder");

        BookingEntity newBooking = reloadedBookings.stream()
                .filter(b -> b.getId().equals(latestPlan.getNewLegId()))
                .findFirst()
                .orElse(null);
        assertNotNull(newBooking, "New booking referenced by newLegId must exist in the database");
        assertEquals("CONFIRMED", newBooking.getStatus());
        assertEquals("DEMO_PAID", newBooking.getPaymentStatus());

        // 2. Placeholder leg must be removed
        assertFalse(bookingRepo.existsById(placeholderLeg.getId()), "Placeholder leg must be deleted");

        // 3. Cab's dependsOn must point to the new booking, and not reference any missing booking id
        BookingEntity reloadedCab = reloadedBookings.stream()
                .filter(b -> b.getId().equals(cabBooking.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(reloadedCab.getDependsOn().contains(newBooking.getId()), "Cab dependsOn must contain the new booking ID");
        assertFalse(reloadedCab.getDependsOn().contains(placeholderLeg.getId()), "Cab dependsOn must not reference deleted placeholder");
        assertFalse(reloadedCab.getDependsOn().contains(oldBooking.getId()), "Cab dependsOn must not reference old booking");

        // 4. No booking's dependsOn in the trip may reference a missing booking id
        Set<String> existingBookingIds = reloadedBookings.stream().map(BookingEntity::getId).collect(Collectors.toSet());
        for (BookingEntity b : reloadedBookings) {
            if (b.getDependsOn() != null) {
                for (String depId : b.getDependsOn()) {
                    assertTrue(existingBookingIds.contains(depId),
                            "dependsOn ID " + depId + " on booking " + b.getId() + " must exist in the trip's bookings");
                }
            }
        }
    }

    @Test
    @DisplayName("Replacement rejects departure departing before disruption time or arriving after must-arrive-by with 409")
    void testReplacementRejectsDepartureBeforeDisruptionTimeOrAfterMustArriveBy() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(4);
        ServiceDepartureEntity oldDep = createTestDeparture("SRV-DIS-OLD", "Jaipur", "Udaipur", travelDate, 20, "BUS");

        // Departure departing at 08:00 (before 10:00 disruption)
        ServiceDepartureEntity earlyDep = createTestDeparture("SRV-DIS-EARLY", "Jaipur", "Udaipur", travelDate, 20, "BUS");
        ServiceEntity earlySrv = earlyDep.getService();
        earlySrv.setDepartureTime(LocalTime.of(8, 0));
        earlySrv.setArrivalTime(LocalTime.of(14, 0));
        serviceRepo.save(earlySrv);

        // Departure arriving at 22:00 (after 20:00 mustArriveBy)
        ServiceDepartureEntity lateDep = createTestDeparture("SRV-DIS-LATE", "Jaipur", "Udaipur", travelDate, 20, "BUS");
        ServiceEntity lateSrv = lateDep.getService();
        lateSrv.setDepartureTime(LocalTime.of(15, 0));
        lateSrv.setArrivalTime(LocalTime.of(22, 0));
        serviceRepo.save(lateSrv);

        TripEntity trip = createTestTrip(userA, "Trip Constraint Rejection Test");
        trip.getConstraints().setMustArriveBy(travelDate.atTime(20, 0));
        trip = tripRepo.save(trip);

        BookingEntity oldBooking = createMarketplaceBooking(userA, trip, oldDep, "BUS", new BigDecimal("500.00"));
        oldBooking.setStatus("CANCELLED");
        bookingRepo.save(oldBooking);

        DisruptionEntity disruption = DisruptionEntity.builder()
                .id(IdGenerator.generate("DIS"))
                .trip(trip)
                .bookingId(oldBooking.getId())
                .type("CANCELLATION")
                .reason("Carrier cancellation")
                .disruptionTime(travelDate.atTime(10, 0))
                .createdAt(LocalDateTime.now())
                .source("SIMULATED")
                .actor("SYSTEM")
                .build();
        if (disruptionRepo != null) {
            disruptionRepo.save(disruption);
        }
        trip.getDisruptions().add(disruption);
        tripRepo.save(trip);

        // 1. Try to book early departure (departs at 08:00, before 10:00 disruption) -> 409 Conflict
        ReplacementBookingRequest reqEarly = ReplacementBookingRequest.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .serviceDepartureId(earlyDep.getId())
                .build();

        mockMvc.perform(post("/api/market/replacement/book")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEarly)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // 2. Try to book late departure (arrives at 22:00, after 20:00 deadline) -> 409 Conflict
        ReplacementBookingRequest reqLate = ReplacementBookingRequest.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .serviceDepartureId(lateDep.getId())
                .build();

        mockMvc.perform(post("/api/market/replacement/book")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Simulation endpoint requires authentication (401/403) and validates bad inputs (400)")
    void testSimulationEndpointValidations() throws Exception {
        // 1. Unauthenticated -> 401/403
        mockMvc.perform(post("/api/market/simulate/departure")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceDepartureId\":\"DEP-123\",\"state\":\"DELAYED\",\"delayMinutes\":60}"))
                .andExpect(status().isUnauthorized());

        // 2. Bad input: invalid state
        SimulateDepartureRequest badStateReq = SimulateDepartureRequest.builder()
                .serviceDepartureId("DEP-123")
                .state("FLYING")
                .delayMinutes(30)
                .build();

        mockMvc.perform(post("/api/market/simulate/departure")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badStateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 3. Bad input: DELAYED with delayMinutes <= 0
        SimulateDepartureRequest zeroDelayReq = SimulateDepartureRequest.builder()
                .serviceDepartureId("DEP-123")
                .state("DELAYED")
                .delayMinutes(0)
                .build();

        mockMvc.perform(post("/api/market/simulate/departure")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroDelayReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("When fewer than 3 feasible options exist, SAFEST is shown as same result as FASTEST/CHEAPEST")
    void testSafestOptionWhenFewerThanThreeOptions() {
        Trip trip = Trip.builder()
                .id("TRIP-SAFEST-TEST")
                .title("Safest Test Trip")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(LocalDateTime.now().plusDays(2).withHour(22).withMinute(0))
                        .build())
                .bookings(new ArrayList<>())
                .build();

        Booking disrupted = Booking.builder()
                .id("BKG-DISRUPTED")
                .type(BookingType.BUS)
                .from("Jaipur")
                .to("Udaipur")
                .startTime(LocalDateTime.now().plusDays(2).withHour(8).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .cost(new BigDecimal("600.00"))
                .status(com.odyssey.model.enums.BookingStatus.CANCELLED)
                .build();

        // 2 candidates: one MARKETPLACE (rel 90), one ESTIMATED (rel 70)
        InventoryItem mktItem = InventoryItem.builder()
                .id("DEP-MKT-001")
                .mode(BookingType.BUS)
                .provider("Rathore Travels")
                .from("Jaipur")
                .to("Udaipur")
                .departure(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .arrival(LocalDateTime.now().plusDays(2).withHour(16).withMinute(0))
                .cost(new BigDecimal("700.00"))
                .source("MARKETPLACE")
                .reliabilityScore(90)
                .build();

        InventoryItem estItem = InventoryItem.builder()
                .id("DEP-EST-002")
                .mode(BookingType.BUS)
                .provider("Estimated Transit")
                .from("Jaipur")
                .to("Udaipur")
                .departure(LocalDateTime.now().plusDays(2).withHour(9).withMinute(0))
                .arrival(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0))
                .cost(new BigDecimal("500.00"))
                .source("ESTIMATED")
                .reliabilityScore(70)
                .build();

        RecoveryResponse resp = scoringService.scoreAndRank(trip, disrupted, List.of(mktItem, estItem));
        assertNotNull(resp);
        assertEquals(3, resp.getOptions().size(), "Must have 3 options (FASTEST, CHEAPEST, SAFEST)");

        Option safest = resp.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.SAFEST).findFirst().orElseThrow();
        assertNotNull(safest);
        // Rule: Never label ESTIMATED as SAFEST while a MARKETPLACE option exists
        assertEquals("MARKETPLACE", safest.getSource(), "SAFEST option must pick MARKETPLACE candidate over ESTIMATED");
        assertTrue(safest.getExplanation().contains("Same result as"), "SAFEST must explain it is the same result");
    }

    @Test
    @DisplayName("Seeder skips inserting duplicate departure when service+travelDate already exists under a custom ID")
    void testSeederSkipsExistingServiceTravelDateWithCustomId() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        ServiceEntity service = serviceRepo.findById("SRV-HBX-GOA-BS-1").orElseThrow();

        // Replace the standard seeded departure for (SRV-HBX-GOA-BS-1, targetDate) with a custom ID
        departureRepo.findByServiceIdAndTravelDate(service.getId(), targetDate)
                .ifPresent(departureRepo::delete);
        departureRepo.flush();

        String customId = "DEP-CUSTOM-" + UUID.randomUUID().toString().substring(0, 8);
        departureRepo.saveAndFlush(ServiceDepartureEntity.builder()
                .id(customId)
                .service(service)
                .travelDate(targetDate)
                .seatsAvailable(service.getSeatsTotal())
                .status("SCHEDULED")
                .delayMinutes(0)
                .build());

        // Re-running seeder must not fail on uq_service_travel_date
        assertDoesNotThrow(() -> marketplaceSeeder.seedMarketplace());

        ServiceDepartureEntity preserved = departureRepo.findByServiceIdAndTravelDate(service.getId(), targetDate).orElseThrow();
        assertEquals(customId, preserved.getId());
    }

    @Test
    @DisplayName("Phase 7 simulate status + check status now marks Marketplace trip as DISRUPTED so recovery options enable")
    void testPhase7SimulateStatusAndCheckNowDisruptsMarketplaceTrip() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(2);
        ServiceDepartureEntity dep = createTestDeparture("SRV-SIM-PHASE7", "Jaipur", "Udaipur", travelDate, 40, "BUS");
        TripEntity trip = createTestTrip(userA, "Phase 7 Sim Trip");
        BookingEntity bkg = createMarketplaceBooking(userA, trip, dep, "BUS", new BigDecimal("750.00"));

        // 1. Simulate DELAYED via /api/simulate/status (Transit Status & Monitoring drawer)
        mockMvc.perform(post("/api/simulate/status")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tripId", trip.getId(),
                                "bookingId", bkg.getId(),
                                "state", "DELAYED",
                                "delayMinutes", 90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Click "Check status now" (/api/trips/{id}/status/check)
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/status/check")
                        .session(sessionA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].state").value("DELAYED"))
                .andExpect(jsonPath("$.data[0].delayMinutes").value(90));

        // 3. Reload trip and verify status is DISRUPTED (enables Find Recovery Options)
        mockMvc.perform(get("/api/trips/" + trip.getId())
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISRUPTED"));
    }

    // Helper methods
    private ServiceDepartureEntity createTestDeparture(String srvId, String origin, String dest, LocalDate date, int seats, String mode) {
        OperatorEntity op = operatorRepo.findById("OP-TEST").orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id("OP-TEST")
                        .name("Royal Rajasthan Express")
                        .mode(mode)
                        .contact("+91-1800-000-001")
                        .build()));

        ServiceEntity srv = serviceRepo.findById(srvId).orElseGet(() ->
                serviceRepo.save(ServiceEntity.builder()
                        .id(srvId)
                        .operator(op)
                        .mode(mode)
                        .vehicleNumber("RJ-14-" + Math.abs(srvId.hashCode() % 9000 + 1000))
                        .originCity(origin)
                        .destinationCity(dest)
                        .originStop(origin + " Central Stand")
                        .destinationStop(dest + " Terminal")
                        .departureTime(LocalTime.of(8, 0))
                        .arrivalTime(LocalTime.of(14, 0))
                        .arrivesNextDay(false)
                        .price(new BigDecimal("750.00"))
                        .seatsTotal(seats > 0 ? seats : 40)
                        .reliabilityScore(92)
                        .active(true)
                        .source("GENERATED")
                        .build()));

        String depId = "DEP-" + srvId + "-" + date;
        return departureRepo.findById(depId).orElseGet(() ->
                departureRepo.save(ServiceDepartureEntity.builder()
                        .id(depId)
                        .service(srv)
                        .travelDate(date)
                        .seatsAvailable(seats)
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .build()));
    }

    private TripEntity createTestTrip(UserEntity user, String title) {
        String tripId = IdGenerator.generate("TRP");
        TripEntity trip = TripEntity.builder()
                .id(tripId)
                .user(user)
                .userId(user.getId())
                .title(title)
                .travellerName(user.getName())
                .travellerPhone(user.getPhone())
                .travellerEmail(user.getEmail())
                .status("BOOKED")
                .bookings(new ArrayList<>())
                .disruptions(new ArrayList<>())
                .auditEntries(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        TripConstraintsEntity constraints = TripConstraintsEntity.builder()
                .id(IdGenerator.generate("CST"))
                .trip(trip)
                .maxBudget(new BigDecimal("15000.00"))
                .mustArriveBy(LocalDateTime.now().plusDays(4))
                .build();
        trip.setConstraints(constraints);

        return tripRepo.save(trip);
    }

    private BookingEntity createMarketplaceBooking(UserEntity user, TripEntity trip, ServiceDepartureEntity dep, String mode, BigDecimal cost) {
        BookingEntity b = BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(user)
                .type(mode)
                .provider(dep.getService().getOperator().getName())
                .fromLocation(dep.getService().getOriginCity())
                .toLocation(dep.getService().getDestinationCity())
                .startTime(dep.getTravelDate().atTime(dep.getService().getDepartureTime()))
                .endTime(dep.getTravelDate().atTime(dep.getService().getArrivalTime()))
                .cost(cost)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .source("MARKETPLACE")
                .bookingReference("ODY-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .serviceId(dep.getService().getId())
                .departureId(dep.getId())
                .vehicleNumber(dep.getService().getVehicleNumber())
                .operator(dep.getService().getOperator().getName())
                .passengerNames(user.getName())
                .seatNumbers("1A")
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        BookingEntity saved = bookingRepo.save(b);
        trip.getBookings().add(saved);
        tripRepo.save(trip);

        SeatHoldEntity hold = SeatHoldEntity.builder()
                .id(IdGenerator.generate("HLD"))
                .departure(dep)
                .bookingId(saved.getId())
                .seatNumber("1A")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .build();
        seatHoldRepo.save(hold);

        return saved;
    }
}
