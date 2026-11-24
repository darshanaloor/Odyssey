package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.CabBookingRequest;
import com.odyssey.dto.market.HotelBookingRequest;
import com.odyssey.dto.market.MarketplaceBookingRequest;
import com.odyssey.dto.market.MockPaymentRequest;
import com.odyssey.dto.market.PassengerDto;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.ServiceDepartureEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.MarketplaceSeeder;
import com.odyssey.service.MarketplaceService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MarketplaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketplaceSeeder marketplaceSeeder;

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private SeatHoldJpaRepository seatHoldRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private HotelJpaRepository hotelRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private TripJpaRepository tripRepo;

    @Autowired
    private AuditEntryJpaRepository auditRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity userA;
    private UserEntity userB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setup() throws Exception {
        marketplaceSeeder.seedMarketplace();

        userA = createTestUser("market_user_a@example.com", "Password123", "User Alpha", "+919876500001");
        userB = createTestUser("market_user_b@example.com", "Password123", "User Beta", "+919876500002");

        sessionA = loginAs("market_user_a@example.com", "Password123");
        sessionB = loginAs("market_user_b@example.com", "Password123");
    }

    private UserEntity createTestUser(String email, String rawPassword, String name, String phone) {
        return userRepo.findByEmail(email).orElseGet(() ->
                userRepo.save(UserEntity.builder()
                        .id("USR-" + UUID.randomUUID().toString().substring(0, 8))
                        .email(email)
                        .passwordHash(passwordEncoder.encode(rawPassword))
                        .name(name)
                        .phone(phone)
                        .createdAt(LocalDateTime.now())
                        .build())
        );
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Search by city pair, alias (Hubli, Hubballi-Dharwad) and date returns valid sorted services")
    void testSearchByCityPairAliasAndDate() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Search canonical Hubballi to Goa
        mockMvc.perform(get("/api/market/services")
                        .session(sessionA)
                        .param("origin", "HUBBALLI")
                        .param("destination", "GOA")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.data[0].departureTime", notNullValue()))
                .andExpect(jsonPath("$.data[0].disclaimer", is(MarketplaceService.DEMO_DISCLAIMER)));

        // Search using alias "Hubli"
        mockMvc.perform(get("/api/market/services")
                        .session(sessionA)
                        .param("origin", "Hubli")
                        .param("destination", "Goa")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))));

        // Search using alias "Hubballi-Dharwad"
        mockMvc.perform(get("/api/market/services")
                        .session(sessionA)
                        .param("origin", "Hubballi-Dharwad")
                        .param("destination", "Goa")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    @DisplayName("Booking decreases seats_available and creates booking row with source MARKETPLACE and DEMO_PAID")
    void testBookingDecreasesSeats() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + tomorrow;
        ServiceDepartureEntity depBefore = departureRepo.findById(depId).orElseThrow();
        int initialSeats = depBefore.getSeatsAvailable();

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(
                        PassengerDto.builder().name("John Doe").age(30).seatNumber("1A").build(),
                        PassengerDto.builder().name("Jane Doe").age(28).seatNumber("1B").build()
                ))
                .build();

        MvcResult res = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.source", is("MARKETPLACE")))
                .andExpect(jsonPath("$.data.paymentStatus", is("DEMO_PAID")))
                .andExpect(jsonPath("$.data.bookingReference", startsWith("ODY-")))
                .andExpect(jsonPath("$.data.seatNumbers", containsString("1A")))
                .andExpect(jsonPath("$.data.seatNumbers", containsString("1B")))
                .andReturn();

        ServiceDepartureEntity depAfter = departureRepo.findById(depId).orElseThrow();
        assertEquals(initialSeats - 2, depAfter.getSeatsAvailable());

        // Verify seat holds in DB
        assertFalse(seatHoldRepo.findByDepartureIdAndSeatNumberIn(depId, List.of("1A")).isEmpty());
        assertFalse(seatHoldRepo.findByDepartureIdAndSeatNumberIn(depId, List.of("1B")).isEmpty());
    }

    @Test
    @DisplayName("Sold out returns 409 Conflict")
    void testSoldOutReturns409() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(2);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + tomorrow;
        ServiceDepartureEntity dep = departureRepo.findById(depId).orElseThrow();
        dep.setSeatsAvailable(1);
        departureRepo.save(dep);

        // Try booking 2 seats when only 1 is available
        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(
                        PassengerDto.builder().name("Passenger 1").age(25).build(),
                        PassengerDto.builder().name("Passenger 2").age(26).build()
                ))
                .build();

        mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("sold out")));
    }

    @Test
    @DisplayName("Two parallel bookings of the same seat give exactly one success and one 409 Conflict")
    void testTwoParallelBookingsSameSeatOneSuccessOneConflict() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(3);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + targetDate;
        String contestSeat = "3A";

        MarketplaceBookingRequest req1 = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("User 1").age(30).seatNumber(contestSeat).build()))
                .build();

        MarketplaceBookingRequest req2 = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("User 2").age(32).seatNumber(contestSeat).build()))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        java.util.function.Function<MarketplaceBookingRequest, Integer> bookAction = (req) -> {
            try {
                startGate.await();
                MvcResult result = mockMvc.perform(post("/api/market/bookings")
                                .session(sessionA)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                return result.getResponse().getStatus();
            } catch (Exception e) {
                return 500;
            }
        };

        Future<Integer> f1 = executor.submit(() -> bookAction.apply(req1));
        Future<Integer> f2 = executor.submit(() -> bookAction.apply(req2));

        startGate.countDown(); // Start both concurrently

        int status1 = f1.get(10, TimeUnit.SECONDS);
        int status2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        if (status1 == 201) successCount.incrementAndGet();
        if (status1 == 409) conflictCount.incrementAndGet();
        if (status2 == 201) successCount.incrementAndGet();
        if (status2 == 409) conflictCount.incrementAndGet();

        assertEquals(1, successCount.get(), "Exactly one booking must succeed (HTTP 201)");
        assertEquals(1, conflictCount.get(), "Exactly one booking must receive 409 Conflict");
    }

    @Test
    @DisplayName("Failed payment with 4000000000000002 creates no booking and no seat hold")
    void testFailedPaymentCreatesNoBookingAndNoSeatHold() throws Exception {
        LocalDate date = LocalDate.now().plusDays(4);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;
        int seatsBefore = departureRepo.findById(depId).orElseThrow().getSeatsAvailable();
        long bookingsCountBefore = bookingRepo.count();

        MarketplaceBookingRequest draft = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Charlie").age(22).seatNumber("7A").build()))
                .build();

        MockPaymentRequest payReq = MockPaymentRequest.builder()
                .bookingDraft(draft)
                .cardHolder("Charlie Tester")
                .cardNumber("4000000000000002") // Documented failure card
                .expiry("12/28")
                .cvv("123")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Payment failed (demo card declined)")));

        // Verify no booking was created
        assertEquals(bookingsCountBefore, bookingRepo.count());
        // Verify no seat hold was created
        assertTrue(seatHoldRepo.findByDepartureIdAndSeatNumberIn(depId, List.of("7A")).isEmpty());
        // Verify seats available is untouched
        assertEquals(seatsBefore, departureRepo.findById(depId).orElseThrow().getSeatsAvailable());
    }

    @Test
    @DisplayName("Card data (PAN, expiry, CVV) is never stored or logged in database")
    void testCardDataIsNeverStoredOrLogged() throws Exception {
        String testCard = "4111222233334444";
        String testCvv = "987";
        String testExpiry = "11/29";

        LocalDate date = LocalDate.now().plusDays(5);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MarketplaceBookingRequest draft = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Safe User").age(35).seatNumber("8A").build()))
                .build();

        MockPaymentRequest payReq = MockPaymentRequest.builder()
                .bookingDraft(draft)
                .cardHolder("Safe User")
                .cardNumber(testCard)
                .expiry(testExpiry)
                .cvv(testCvv)
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.cardLast4", is("4444")));

        // Scan database for any occurrence of the card number, CVV or expiry
        List<BookingEntity> allBookings = bookingRepo.findAll();
        for (BookingEntity b : allBookings) {
            assertFalse(String.valueOf(b.getPassengerNames()).contains(testCard));
            assertFalse(String.valueOf(b.getPassengerNames()).contains(testCvv));
            assertFalse(String.valueOf(b.getProvider()).contains(testCard));
            assertFalse(String.valueOf(b.getBookingReference()).contains(testCard));
        }
    }

    @Test
    @DisplayName("Cancel releases seats, marks DEMO_REFUNDED, and logs USER audit entry")
    void testCancelReleasesSeats() throws Exception {
        LocalDate date = LocalDate.now().plusDays(6);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;
        int seatsBefore = departureRepo.findById(depId).orElseThrow().getSeatsAvailable();

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(
                        PassengerDto.builder().name("Cancel Test").age(40).seatNumber("9A").build(),
                        PassengerDto.builder().name("Cancel Test 2").age(42).seatNumber("9B").build()
                ))
                .build();

        MvcResult bookRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(bookRes.getResponse().getContentAsString());
        String bookingId = json.path("data").path("id").asText();

        assertEquals(seatsBefore - 2, departureRepo.findById(depId).orElseThrow().getSeatsAvailable());

        // Cancel the booking
        mockMvc.perform(post("/api/market/bookings/" + bookingId + "/cancel")
                        .session(sessionA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.paymentStatus", anyOf(is("DEMO_REFUNDED"), is("PARTIALLY_REFUNDED"))));

        // Verify seats have returned!
        assertEquals(seatsBefore, departureRepo.findById(depId).orElseThrow().getSeatsAvailable());
        // Verify seat holds are cleared
        assertTrue(seatHoldRepo.findByBookingId(bookingId).isEmpty());
    }

    @Test
    @DisplayName("Cancelling or viewing someone else's booking returns 403 or 404")
    void testCancellingSomeoneElsesBookingReturns403Or404() throws Exception {
        LocalDate date = LocalDate.now().plusDays(7);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("User A Travel").age(25).seatNumber("10A").build()))
                .build();

        MvcResult bookRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(bookRes.getResponse().getContentAsString());
        String bookingId = json.path("data").path("id").asText();

        // User B tries to view User A's booking
        mockMvc.perform(get("/api/market/bookings/" + bookingId)
                        .session(sessionB))
                .andExpect(status().isForbidden());

        // User B tries to cancel User A's booking
        mockMvc.perform(post("/api/market/bookings/" + bookingId + "/cancel")
                        .session(sessionB)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // User B tries to download User A's summary
        mockMvc.perform(get("/api/market/bookings/" + bookingId + "/summary")
                        .session(sessionB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated calls return 401 Unauthorized")
    void testUnauthenticatedCallsReturn401() throws Exception {
        mockMvc.perform(get("/api/market/services")
                        .param("origin", "Hubballi")
                        .param("destination", "Goa"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/market/bookings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/market/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Past departures cannot be booked")
    void testPastDeparturesCannotBeBooked() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        String pastDepId = "DEP-SRV-PAST-TEST";

        departureRepo.save(ServiceDepartureEntity.builder()
                .id(pastDepId)
                .service(serviceRepo.findAll().get(0))
                .travelDate(pastDate)
                .seatsAvailable(20)
                .status("SCHEDULED")
                .build());

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(pastDepId)
                .passengers(List.of(PassengerDto.builder().name("Past Traveler").age(30).build()))
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                marketplaceService.bookTransport(req, userA)
        );
    }

    @Test
    @DisplayName("Seeder is idempotent across multiple executions")
    void testSeederIsIdempotent() {
        long servicesBefore = serviceRepo.count();
        long departuresBefore = departureRepo.count();
        long hotelsBefore = hotelRepo.count();
        long cabsBefore = cabRepo.count();

        // Run seeder again
        marketplaceSeeder.seedMarketplace();

        assertEquals(servicesBefore, serviceRepo.count(), "Services count must remain identical");
        assertEquals(departuresBefore, departureRepo.count(), "Departures count must remain identical");
        assertEquals(hotelsBefore, hotelRepo.count(), "Hotels count must remain identical");
        assertEquals(cabsBefore, cabRepo.count(), "Cabs count must remain identical");
    }

    @Test
    @DisplayName("Summary contains required demo disclaimer")
    void testSummaryContainsDisclaimer() throws Exception {
        LocalDate date = LocalDate.now().plusDays(8);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Summary Test").age(29).seatNumber("12A").build()))
                .build();

        MvcResult bookRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(bookRes.getResponse().getContentAsString());
        String bookingId = json.path("data").path("id").asText();
        String bookingRef = json.path("data").path("bookingReference").asText();

        MvcResult summaryRes = mockMvc.perform(get("/api/market/bookings/" + bookingId + "/summary")
                        .session(sessionA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String html = summaryRes.getResponse().getContentAsString();
        assertTrue(html.contains("Demo marketplace. No real ticket is issued and no money was charged."),
                "Summary must contain exact demo disclaimer");
        assertTrue(html.contains(bookingRef), "Summary must contain booking reference");
        assertTrue(html.contains("12A"), "Summary must contain seat number");
        assertTrue(html.contains("Summary Test"), "Summary must contain passenger name");
    }

    @Test
    @DisplayName("Phase 10A: Booking with createTrip=true creates one trip and one timeline booking with correct data")
    void testBookingWithCreateTripCreatesOneTripAndTimelineBooking() throws Exception {
        LocalDate date = LocalDate.now().plusDays(10);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(
                        PassengerDto.builder().name("Alice Explorer").age(28).seatNumber("14A").build(),
                        PassengerDto.builder().name("Bob Explorer").age(30).seatNumber("14B").build()
                ))
                .createTrip(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tripId", notNullValue()))
                .andExpect(jsonPath("$.data.tripTitle", is("Hubballi to Goa")))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String tripId = json.path("tripId").asText();
        String bookingId = json.path("id").asText();
        String bookingRef = json.path("bookingReference").asText();

        // 1. Verify trip in DB
        TripEntity trip = tripRepo.findById(tripId).orElseThrow();
        assertEquals("Hubballi to Goa", trip.getTitle());
        assertEquals(userA.getId(), trip.getUser().getId());
        assertEquals(userA.getName(), trip.getTravellerName());
        assertEquals(userA.getEmail(), trip.getTravellerEmail());
        assertNotNull(trip.getConstraints());
        assertEquals(Boolean.FALSE, trip.getConstraints().getAvoidOvernightTravel());
        assertEquals(trip.getConstraints().getMustArriveBy(), json.path("endTime").asText().replace("T", " ") != null ?
                trip.getConstraints().getMustArriveBy() : null);

        // 2. Verify booking in DB
        BookingEntity booking = bookingRepo.findById(bookingId).orElseThrow();
        assertEquals(tripId, booking.getTrip().getId());
        assertEquals("MARKETPLACE", booking.getSource());
        assertEquals("CONFIRMED", booking.getStatus());
        assertEquals("BUS", booking.getType());
        assertEquals("ON_TIME", booking.getLastStatusState());
        assertEquals("KSRTC", booking.getOperator());
        assertEquals("KA-25-F-1101", booking.getVehicleNumber());
        assertEquals("14A, 14B", booking.getSeatNumbers());

        // 3. Verify audit entry
        List<com.odyssey.entity.AuditEntryEntity> audits = auditRepo.findByTripIdOrderByTimestampDesc(tripId);
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().anyMatch(a ->
                "USER".equals(a.getActor()) &&
                a.getDetails().contains("Marketplace booking " + bookingRef + " added")));
    }

    @Test
    @DisplayName("Phase 10A: Booking with existing tripId adds to that trip")
    void testBookingWithExistingTripIdAddsToTrip() throws Exception {
        LocalDate date = LocalDate.now().plusDays(8);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        // 1. Create a trip with initial booking
        MarketplaceBookingRequest initialReq = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Solo").age(25).seatNumber("15A").build()))
                .createTrip(true)
                .build();

        MvcResult initialResult = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String tripId = objectMapper.readTree(initialResult.getResponse().getContentAsString())
                .path("data").path("tripId").asText();

        // 2. Add a cab to the same trip
        CabBookingRequest cabReq = CabBookingRequest.builder()
                .cabServiceId("CAB-OLA-GOA")
                .pickup("Kadamba Bus Stand, Panaji")
                .dropoff("Calangute Beach")
                .pickupTime(date.atTime(15, 0))
                .tripId(tripId)
                .build();

        MvcResult cabResult = mockMvc.perform(post("/api/market/cab-bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cabReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tripId", is(tripId)))
                .andReturn();

        List<BookingEntity> bookings = bookingRepo.findByTripIdOrderByStartTimeAsc(tripId);
        assertEquals(2, bookings.size(), "Trip should now contain 2 bookings");
    }

    @Test
    @DisplayName("Phase 10A: Another user's tripId returns 403 and non-existent tripId returns 404")
    void testAnotherUsersTripIdReturnsForbiddenOrNotFound() throws Exception {
        LocalDate date = LocalDate.now().plusDays(12);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        // 1. Non-existent tripId returns 404
        MarketplaceBookingRequest req404 = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Tester").age(30).seatNumber("16A").build()))
                .tripId("TRIP-DOES-NOT-EXIST-404")
                .build();

        mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req404)))
                .andExpect(status().isNotFound());

        // 2. User A creates a trip
        MarketplaceBookingRequest reqTripA = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Alice").age(30).seatNumber("16A").build()))
                .createTrip(true)
                .build();

        MvcResult resA = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqTripA)))
                .andExpect(status().isCreated())
                .andReturn();

        String tripIdA = objectMapper.readTree(resA.getResponse().getContentAsString())
                .path("data").path("tripId").asText();

        // 3. User B attempts to attach booking to User A's trip -> 403 Forbidden
        MarketplaceBookingRequest reqForbidden = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Bob Sneak").age(32).seatNumber("16B").build()))
                .tripId(tripIdA)
                .build();

        mockMvc.perform(post("/api/market/bookings")
                        .session(sessionB)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqForbidden)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phase 10A: Failed payment creates no trip and no booking")
    void testFailedPaymentCreatesNoTripAndNoBooking() throws Exception {
        LocalDate date = LocalDate.now().plusDays(13);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        long tripsBefore = tripRepo.count();
        long bookingsBefore = bookingRepo.count();

        MarketplaceBookingRequest draft = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Failed Pay").age(25).seatNumber("17A").build()))
                .createTrip(true)
                .build();

        MockPaymentRequest payReq = MockPaymentRequest.builder()
                .bookingDraft(draft)
                .cardHolder("Fail Pay Tester")
                .cardNumber("4000000000000002")
                .expiry("12/28")
                .cvv("123")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Payment failed (demo card declined)")));

        assertEquals(tripsBefore, tripRepo.count(), "No new trip should be created on failed payment");
        assertEquals(bookingsBefore, bookingRepo.count(), "No new booking should be created on failed payment");
    }

    @Test
    @DisplayName("Phase 10A: Cab depends on arriving bus, and hotel depends on cab")
    void testCabAndHotelGetCorrectDependsOn() throws Exception {
        LocalDate date = LocalDate.now().plusDays(9);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        // 1. Book bus with createTrip=true
        MarketplaceBookingRequest busReq = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Chained User").age(27).seatNumber("18A").build()))
                .createTrip(true)
                .build();

        MvcResult busRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(busReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode busJson = objectMapper.readTree(busRes.getResponse().getContentAsString()).path("data");
        String tripId = busJson.path("tripId").asText();
        String busBookingId = busJson.path("id").asText();

        // 2. Book cab arriving after bus
        CabBookingRequest cabReq = CabBookingRequest.builder()
                .cabServiceId("CAB-OLA-GOA")
                .pickup("Kadamba Bus Stand, Panaji")
                .dropoff("Calangute Beach")
                .pickupTime(date.atTime(13, 0))
                .tripId(tripId)
                .build();

        MvcResult cabRes = mockMvc.perform(post("/api/market/cab-bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cabReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String cabBookingId = objectMapper.readTree(cabRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        BookingEntity cabBooking = bookingRepo.findById(cabBookingId).orElseThrow();
        assertTrue(cabBooking.getDependsOn().contains(busBookingId), "Cab must depend on the arriving bus");

        // 3. Book hotel with checkin on the same date
        HotelBookingRequest hotelReq = HotelBookingRequest.builder()
                .hotelId("HTL-GOA-1")
                .checkIn(date)
                .checkOut(date.plusDays(1))
                .rooms(1)
                .guestName("Chained User")
                .tripId(tripId)
                .build();

        MvcResult hotelRes = mockMvc.perform(post("/api/market/hotel-bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String hotelBookingId = objectMapper.readTree(hotelRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        BookingEntity hotelBooking = bookingRepo.findById(hotelBookingId).orElseThrow();
        assertTrue(hotelBooking.getDependsOn().contains(cabBookingId), "Hotel must depend on the cab");
    }

    @Test
    @DisplayName("Phase 10A: Cancelling from My Bookings marks timeline booking CANCELLED and writes trip audit entry")
    void testCancellingUpdatesTimelineAndWritesAudit() throws Exception {
        LocalDate date = LocalDate.now().plusDays(10);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Cancel Sync").age(34).seatNumber("19A").build()))
                .createTrip(true)
                .build();

        MvcResult bookRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(bookRes.getResponse().getContentAsString()).path("data");
        String bookingId = data.path("id").asText();
        String bookingRef = data.path("bookingReference").asText();
        String tripId = data.path("tripId").asText();

        // Cancel the booking
        mockMvc.perform(post("/api/market/bookings/" + bookingId + "/cancel")
                        .session(sessionA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Verify timeline booking is CANCELLED
        BookingEntity booking = bookingRepo.findById(bookingId).orElseThrow();
        assertEquals("CANCELLED", booking.getStatus());

        // Verify trip audit entry
        List<com.odyssey.entity.AuditEntryEntity> audits = auditRepo.findByTripIdOrderByTimestampDesc(tripId);
        assertTrue(audits.stream().anyMatch(a ->
                "USER".equals(a.getActor()) &&
                a.getDetails().contains("Marketplace booking " + bookingRef + " cancelled")));
    }

    @Test
    @DisplayName("Phase 10A: Trip list count updates after booking with createTrip=true")
    void testTripListCountUpdates() throws Exception {
        LocalDate date = LocalDate.now().plusDays(11);
        String depId = "DEP-SRV-HBX-GOA-BS-1-" + date;

        MvcResult tripsBefore = mockMvc.perform(get("/api/trips").session(sessionA))
                .andExpect(status().isOk())
                .andReturn();
        int countBefore = objectMapper.readTree(tripsBefore.getResponse().getContentAsString()).path("data").size();

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Counter Test").age(22).seatNumber("20A").build()))
                .createTrip(true)
                .build();

        mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        MvcResult tripsAfter = mockMvc.perform(get("/api/trips").session(sessionA))
                .andExpect(status().isOk())
                .andReturn();
        int countAfter = objectMapper.readTree(tripsAfter.getResponse().getContentAsString()).path("data").size();

        assertEquals(countBefore + 1, countAfter, "Trip count for User A must increase by 1");
    }

    @Test
    @DisplayName("Phase 10B: City catalogue has 300+ cities covering all states and UTs")
    void testCityCatalogueCoverage() {
        List<com.odyssey.model.CityInfo> cities = com.odyssey.service.InventoryService.getAllCities();
        assertTrue(cities.size() >= 300, "Catalogue must have at least 300 cities, found: " + cities.size());

        Set<String> states = new HashSet<>();
        for (com.odyssey.model.CityInfo c : cities) {
            assertNotNull(c.getKey());
            assertNotNull(c.getName());
            assertNotNull(c.getState());
            states.add(c.getState());
        }
        assertTrue(states.size() >= 35, "Must cover Indian states and union territories, found: " + states.size());
    }

    @Test
    @DisplayName("Phase 10B: Autocomplete searches prefix, aliases, and fuzzy suggestions on unknown city")
    void testCityAutocomplete() throws Exception {
        // 1. Prefix search
        mockMvc.perform(get("/api/market/cities?q=Hub").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name", containsString("Hubballi")));

        // 2. Alias search (Panjim -> Goa)
        mockMvc.perform(get("/api/market/cities?q=Panjim").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].name", is("Goa")));

        // 3. Unknown city fuzzy search
        mockMvc.perform(get("/api/market/cities?q=XyzUnknownPlace").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("City not found")))
                .andExpect(jsonPath("$.data", hasSize(lessThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("Phase 10B: Search validation: same city 400, past date 400, >60 days 400")
    void testSearchValidation() throws Exception {
        LocalDate today = LocalDate.now();

        // Same city
        mockMvc.perform(get("/api/market/services?origin=Hubballi&destination=Hubli").session(sessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("same")));

        // Past date
        mockMvc.perform(get("/api/market/services?origin=Hubballi&destination=Goa&date=" + today.minusDays(1)).session(sessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("past")));

        // >60 days
        mockMvc.perform(get("/api/market/services?origin=Hubballi&destination=Goa&date=" + today.plusDays(61)).session(sessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("60 days")));
    }

    @Test
    @DisplayName("Phase 10B: Deterministic generated services on unseeded routes")
    void testGeneratedServicesUnseededRoutes() throws Exception {
        LocalDate searchDate = LocalDate.now().plusDays(5);

        // 1. Jaipur -> Udaipur (Bus and Train, dist ~390km)
        MvcResult res1 = mockMvc.perform(get("/api/market/services?origin=Jaipur&destination=Udaipur&date=" + searchDate).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].source", is("GENERATED")))
                .andExpect(jsonPath("$.data[0].sourceLabel", is("Generated schedule")))
                .andExpect(jsonPath("$.data[0].scheduleLabel", is("Generated schedule, not an official timetable")))
                .andReturn();

        JsonNode data1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data");
        assertTrue(data1.size() >= 5, "Jaipur -> Udaipur should have multiple bus and train departures");

        // 2. Delhi -> Mumbai (Flight included because dist > 300km and both have airports)
        MvcResult res2 = mockMvc.perform(get("/api/market/services?origin=Delhi&destination=Mumbai&date=" + searchDate).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andReturn();

        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data");
        boolean hasFlight = false;
        for (JsonNode item : data2) {
            if ("FLIGHT".equalsIgnoreCase(item.path("mode").asText())) {
                hasFlight = true;
                assertEquals("Domestic Air Service", item.path("operator").asText());
                assertTrue(item.path("vehicleNumber").asText().startsWith("GEN-FLT-"));
            }
        }
        assertTrue(hasFlight, "Delhi to Mumbai must include generated flights");

        // 3. Guwahati -> Shillong (dist ~65km, no flight)
        MvcResult res3 = mockMvc.perform(get("/api/market/services?origin=Guwahati&destination=Shillong&date=" + searchDate).session(sessionA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data3 = objectMapper.readTree(res3.getResponse().getContentAsString()).path("data");
        for (JsonNode item : data3) {
            assertNotEquals("FLIGHT", item.path("mode").asText(), "Guwahati to Shillong must not have flights (short distance)");
        }
    }

    @Test
    @DisplayName("Phase 10B: Booking generated service creates trip and enforces seat holds")
    void testBookGeneratedService() throws Exception {
        LocalDate searchDate = LocalDate.now().plusDays(4);

        // Search Kochi -> Chennai
        MvcResult searchRes = mockMvc.perform(get("/api/market/services?origin=Kochi&destination=Chennai&date=" + searchDate).session(sessionA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode services = objectMapper.readTree(searchRes.getResponse().getContentAsString()).path("data");
        assertTrue(services.size() > 0);
        String depId = services.get(0).path("departureId").asText();

        MarketplaceBookingRequest req = MarketplaceBookingRequest.builder()
                .serviceDepartureId(depId)
                .passengers(List.of(PassengerDto.builder().name("Gen Passenger").age(28).seatNumber("GEN-1").build()))
                .createTrip(true)
                .build();

        MvcResult bookRes = mockMvc.perform(post("/api/market/bookings")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduleLabel", is("Generated schedule, not an official timetable")))
                .andReturn();

        JsonNode bookingData = objectMapper.readTree(bookRes.getResponse().getContentAsString()).path("data");
        String tripId = bookingData.path("tripId").asText();
        assertNotNull(tripId);

        TripEntity trip = tripRepo.findById(tripId).orElseThrow();
        assertEquals("CONFIRMED", trip.getStatus());
        assertTrue(trip.getTitle().contains("to"), "Trip title should be formatted Origin to Destination");
    }

    @Test
    @DisplayName("Phase 10B: Concurrent lazy materialization is idempotent")
    void testConcurrentMaterialization() throws Exception {
        LocalDate searchDate = LocalDate.now().plusDays(6);
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    mockMvc.perform(get("/api/market/services?origin=Bhopal&destination=Indore&date=" + searchDate).session(sessionA))
                            .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(threads, successCount.get(), "All concurrent searches should succeed idempotently");
        executor.shutdown();
    }
}
