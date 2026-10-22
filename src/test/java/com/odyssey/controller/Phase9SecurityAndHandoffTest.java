package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.*;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase9SecurityAndHandoffTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripJpaRepository tripJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        tripRepository.resetDemoTrip();
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

    private UserEntity createTestUser(String email, String rawPassword, String name, String phone) {
        return userJpaRepository.findByEmail(email).orElseGet(() -> {
            UserEntity user = UserEntity.builder()
                    .id("USR-" + UUID.randomUUID().toString().substring(0, 8))
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .name(name)
                    .phone(phone)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userJpaRepository.save(user);
        });
    }

    @Test
    @DisplayName("Register, login, profile update and password change flow")
    void testAuthLifecycle() throws Exception {
        String email = "lifecycle_" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPassword123";

        // 1. Register
        RegisterRequest registerReq = RegisterRequest.builder()
                .name("Alex Traveler")
                .email(email)
                .password(password)
                .phone("+919876543210")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.name", is("Alex Traveler")));

        // 2. Login with bad password fails with generic message
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "WrongPass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));

        // 3. Login with correct password succeeds and creates session
        MockHttpSession session = loginAs(email, password);
        assertNotNull(session);

        // 4. GET /api/auth/me returns current profile
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.name", is("Alex Traveler")));

        // 5. Update profile
        UpdateProfileRequest updateProfileReq = new UpdateProfileRequest();
        updateProfileReq.setName("Alex T.");
        updateProfileReq.setPhone("+919999888877");

        mockMvc.perform(put("/api/auth/profile")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProfileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Alex T.")));

        // 6. Change password
        ChangePasswordRequest changePasswordReq = new ChangePasswordRequest();
        changePasswordReq.setOldPassword(password);
        changePasswordReq.setNewPassword("BrandNewPass456");

        mockMvc.perform(put("/api/auth/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordReq)))
                .andExpect(status().isOk());

        // 7. Verify login with new password succeeds
        MockHttpSession newSession = loginAs(email, "BrandNewPass456");
        assertNotNull(newSession);
    }

    @Test
    @DisplayName("Registration rejects duplicate emails and weak passwords")
    void testRegistrationValidation() throws Exception {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        createTestUser(email, "Pass123456", "Initial User", "9876543210");

        // Duplicate email -> 409
        RegisterRequest duplicateReq = RegisterRequest.builder()
                .name("Duplicate User")
                .email(email)
                .password("NewPass123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));

        // Weak password (<8 chars) -> 400
        RegisterRequest shortPassReq = RegisterRequest.builder()
                .name("Short Pass")
                .email("short_" + System.currentTimeMillis() + "@example.com")
                .password("short1")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPassReq)))
                .andExpect(status().isBadRequest());

        // Weak password (no digits) -> 400
        RegisterRequest noDigitPassReq = RegisterRequest.builder()
                .name("No Digits")
                .email("nodigit_" + System.currentTimeMillis() + "@example.com")
                .password("OnlyLettersHere")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDigitPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("digit"))));
    }

    @Test
    @DisplayName("Login rate limiting: 5 failed attempts per 15 min per email triggers 429 on 6th attempt")
    void testLoginRateLimiting() throws Exception {
        String email = "ratelimit_" + System.currentTimeMillis() + "@example.com";
        createTestUser(email, "CorrectPass123", "Rate Limit User", "9876543210");

        // First 5 failed attempts -> 401
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "BadPass123"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Invalid email or password")));
        }

        // 6th attempt -> 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "BadPass123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Too many failed login attempts")));
    }

    @Test
    @DisplayName("Unauthenticated requests to protected endpoints return 401")
    void testUnauthenticatedProtection() throws Exception {
        mockMvc.perform(get("/api/trips"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/trips/TRIP-DEMO-001"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/trips")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF protection rejects mutating calls without valid token")
    void testCsrfProtection() throws Exception {
        UserEntity user = createTestUser("csrf_" + System.currentTimeMillis() + "@example.com", "Password123", "CSRF User", "9876543210");
        MockHttpSession session = loginAs(user.getEmail(), "Password123");

        // POST without CSRF token -> 403 Forbidden
        mockMvc.perform(post("/api/trips")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TRIP-DEMO-001 is readable by any logged-in user, but mutating calls return 403 (only reset permitted)")
    void testDemoTripImmutability() throws Exception {
        UserEntity user = createTestUser("demotester_" + System.currentTimeMillis() + "@example.com", "Password123", "Demo Tester", "9876543210");
        MockHttpSession session = loginAs(user.getEmail(), "Password123");

        // 1. Readable
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is("TRIP-DEMO-001")));

        // 2. Mutating calls on TRIP-DEMO-001 return 403
        CreateTripRequest validUpdateReq = CreateTripRequest.builder()
                .title("Updated Demo Trip")
                .travellerName("Demo Traveller")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("20000.00"))
                        .mustArriveBy(LocalDateTime.now().plusDays(2))
                        .build())
                .bookings(List.of(Booking.builder()
                        .type(BookingType.BUS)
                        .provider("KSRTC")
                        .from("City A")
                        .to("City B")
                        .startTime(LocalDateTime.now().plusHours(1))
                        .endTime(LocalDateTime.now().plusHours(3))
                        .cost(new BigDecimal("300.00"))
                        .status(BookingStatus.CONFIRMED)
                        .build()))
                .build();

        mockMvc.perform(put("/api/trips/TRIP-DEMO-001")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/trips/TRIP-DEMO-001/disruptions")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ReportDisruptionRequest.builder()
                                .bookingId("BKG-101")
                                .type(DisruptionType.CANCELLATION)
                                .reason("Cancelled by airline")
                                .build())))
                .andExpect(status().isForbidden());

        // 3. /api/demo/reset is permitted
        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is("TRIP-DEMO-001")));
    }

    @Test
    @DisplayName("Requirement 1: Ownership enforced in one shared place. User B accessing User A trip fails across ALL endpoints with 403 or 404")
    void testOwnershipEnforcementLoopAcrossAllEndpoints() throws Exception {
        UserEntity userA = createTestUser("usera_" + System.currentTimeMillis() + "@example.com", "Password123", "User A", "9876543210");
        UserEntity userB = createTestUser("userb_" + System.currentTimeMillis() + "@example.com", "Password123", "User B", "9876543211");

        MockHttpSession sessionA = loginAs(userA.getEmail(), "Password123");
        MockHttpSession sessionB = loginAs(userB.getEmail(), "Password123");

        // User A creates a trip with 1 booking
        LocalDateTime now = LocalDateTime.now().plusDays(2);
        CreateTripRequest createTripReq = CreateTripRequest.builder()
                .title("Trip Owned By A")
                .travellerName("User A")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(now.plusHours(12))
                        .build())
                .bookings(List.of(
                        Booking.builder()
                                .type(BookingType.TRAIN)
                                .provider("IRCTC Express")
                                .from("Bengaluru")
                                .to("Chennai")
                                .startTime(now)
                                .endTime(now.plusHours(5))
                                .cost(new BigDecimal("750.00"))
                                .status(BookingStatus.CONFIRMED)
                                .build()
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/trips")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTripReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String tripIdA = createdNode.path("data").path("id").asText();
        String bookingIdA = createdNode.path("data").path("bookings").get(0).path("id").asText();
        assertNotNull(tripIdA);
        assertNotNull(bookingIdA);

        AddBookingRequest validBookingReq = AddBookingRequest.builder()
                .type(BookingType.BUS)
                .provider("Test Bus")
                .from("City A")
                .to("City B")
                .startTime(now)
                .endTime(now.plusHours(2))
                .cost(new BigDecimal("500.00"))
                .build();

        // All mutating and protected endpoints that take tripId
        List<MockHttpServletRequestBuilder> userBAttackingRequests = List.of(
                get("/api/trips/" + tripIdA),
                put("/api/trips/" + tripIdA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createTripReq)),
                delete("/api/trips/" + tripIdA),
                post("/api/trips/" + tripIdA + "/bookings").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(validBookingReq)),
                put("/api/trips/" + tripIdA + "/bookings/" + bookingIdA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(validBookingReq)),
                delete("/api/trips/" + tripIdA + "/bookings/" + bookingIdA),
                post("/api/trips/" + tripIdA + "/bookings/" + bookingIdA + "/book").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/trips/" + tripIdA + "/bookings/" + bookingIdA + "/unbook"),
                put("/api/trips/" + tripIdA + "/constraints").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(UpdateConstraintsRequest.builder().constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(now.plusHours(12)).build()).build())),
                post("/api/trips/" + tripIdA + "/disruptions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(ReportDisruptionRequest.builder().bookingId(bookingIdA).type(DisruptionType.FLIGHT_DELAY).delayMinutes(30).reason("Flight delayed due to fog").build())),
                post("/api/trips/" + tripIdA + "/bookings/" + bookingIdA + "/report-problem").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new ReportProblemRequest("DELAYED", 30, "Test note"))),
                get("/api/trips/" + tripIdA + "/impact"),
                post("/api/trips/" + tripIdA + "/recover"),
                post("/api/trips/" + tripIdA + "/plan"),
                post("/api/trips/" + tripIdA + "/approve"),
                post("/api/trips/" + tripIdA + "/reject"),
                post("/api/trips/" + tripIdA + "/status/check"),
                get("/api/trips/" + tripIdA + "/status"),
                post("/api/simulate/status").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(SimulateStatusRequest.builder().bookingId(bookingIdA).state("DELAYED").delayMinutes(30).build())),
                get("/api/trips/" + tripIdA + "/notifications"),
                post("/api/trips/" + tripIdA + "/notifications/test"),
                post("/api/trips/" + tripIdA + "/plan/hotel-message/send")
        );

        for (MockHttpServletRequestBuilder req : userBAttackingRequests) {
            MvcResult res = mockMvc.perform(req.session(sessionB).with(csrf())).andReturn();
            int statusCode = res.getResponse().getStatus();
            assertTrue(statusCode == 403 || statusCode == 404,
                    "Endpoint " + req + " should have returned 403 Forbidden or 404 Not Found for non-owner User B, but returned: " + statusCode);
        }
    }

    @Test
    @DisplayName("Booking handoff: templates, booking confirmation and COMPLETED_BOOKINGS <-> RESOLVED status transition on unbook")
    void testHandoffBookingAndStatusTransitions() throws Exception {
        UserEntity user = createTestUser("handoff_" + System.currentTimeMillis() + "@example.com", "Password123", "Handoff User", "9876543210");
        MockHttpSession session = loginAs(user.getEmail(), "Password123");

        LocalDateTime now = LocalDateTime.now().plusDays(1);
        // Create a trip with a REBOOKED bus leg
        CreateTripRequest createReq = CreateTripRequest.builder()
                .title("Handoff Transition Trip")
                .travellerName("Handoff User")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(now.plusHours(10))
                        .build())
                .bookings(List.of(
                        Booking.builder()
                                .type(BookingType.BUS)
                                .provider("KSRTC Sarige")
                                .from("Bengaluru")
                                .to("Mysuru")
                                .startTime(now)
                                .endTime(now.plusHours(4))
                                .cost(new BigDecimal("350.00"))
                                .status(BookingStatus.REBOOKED)
                                .build()
                ))
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/trips")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String tripId = createdJson.path("data").path("id").asText();
        String bookingId = createdJson.path("data").path("bookings").get(0).path("id").asText();

        // 1. Get Handoff legs
        MvcResult handoffRes = mockMvc.perform(get("/api/trips/" + tripId + "/handoff").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type", is("BUS")))
                .andExpect(jsonPath("$.data[0].booked", is(false)))
                .andReturn();

        JsonNode handoffJson = objectMapper.readTree(handoffRes.getResponse().getContentAsString());
        JsonNode links = handoffJson.path("data").get(0).path("links");
        assertTrue(links.size() >= 1, "Should contain provider links");

        // 2. Mark booking as booked
        BookBookingRequest bookReq = new BookBookingRequest("PNR-KSRTC-998877");
        mockMvc.perform(post("/api/trips/" + tripId + "/bookings/" + bookingId + "/book")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED_BOOKINGS")))
                .andExpect(jsonPath("$.data.bookings[0].status", is("BOOKED")))
                .andExpect(jsonPath("$.data.bookings[0].bookingReference", is("PNR-KSRTC-998877")));

        // 3. Unmark / unbook moves trip back from COMPLETED_BOOKINGS to RESOLVED
        mockMvc.perform(post("/api/trips/" + tripId + "/bookings/" + bookingId + "/unbook")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("RESOLVED")))
                .andExpect(jsonPath("$.data.bookings[0].status", is("REBOOKED")))
                .andExpect(jsonPath("$.data.bookings[0].bookingReference", nullValue()));
    }

    @Test
    @DisplayName("Trips Directory Isolation: User A only sees their own trips + demo trip; other users' trips stay hidden")
    void testTripsDirectoryIsolation() throws Exception {
        UserEntity userA = createTestUser("iso_a_" + System.currentTimeMillis() + "@example.com", "Password123", "User A", "9876543210");
        UserEntity userB = createTestUser("iso_b_" + System.currentTimeMillis() + "@example.com", "Password123", "User B", "9876543211");

        MockHttpSession sessionA = loginAs(userA.getEmail(), "Password123");
        MockHttpSession sessionB = loginAs(userB.getEmail(), "Password123");

        LocalDateTime now = LocalDateTime.now().plusDays(2);
        // User A creates Trip A
        CreateTripRequest tripAReq = CreateTripRequest.builder()
                .title("Trip For A Only")
                .travellerName("User A")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(now.plusHours(6)).build())
                .bookings(List.of(Booking.builder()
                        .type(BookingType.BUS)
                        .provider("Operator A")
                        .from("City 1")
                        .to("City 2")
                        .startTime(now)
                        .endTime(now.plusHours(3))
                        .cost(new BigDecimal("500.00"))
                        .status(BookingStatus.CONFIRMED)
                        .build()))
                .build();

        mockMvc.perform(post("/api/trips")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripAReq)))
                .andExpect(status().isCreated());

        // User B creates Trip B
        CreateTripRequest tripBReq = CreateTripRequest.builder()
                .title("Trip For B Only")
                .travellerName("User B")
                .travellerPhone("9876543211")
                .constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(now.plusHours(6)).build())
                .bookings(List.of(Booking.builder()
                        .type(BookingType.BUS)
                        .provider("Operator B")
                        .from("City 3")
                        .to("City 4")
                        .startTime(now)
                        .endTime(now.plusHours(3))
                        .cost(new BigDecimal("500.00"))
                        .status(BookingStatus.CONFIRMED)
                        .build()))
                .build();

        mockMvc.perform(post("/api/trips")
                        .session(sessionB)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripBReq)))
                .andExpect(status().isCreated());

        // User A sees Trip For A Only and demo trip, but NOT Trip For B Only
        mockMvc.perform(get("/api/trips").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("Trip For A Only")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Trip For B Only"))));

        // User B sees Trip For B Only and demo trip, but NOT Trip For A Only
        mockMvc.perform(get("/api/trips").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("Trip For B Only")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Trip For A Only"))));
    }
}
