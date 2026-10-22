package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.CreateTripRequest;
import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.model.Constraints;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OdysseyApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private com.odyssey.repository.jpa.UserJpaRepository userJpaRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private com.odyssey.entity.UserEntity testUser;

    @BeforeEach
    void setup() {
        testUser = userJpaRepository.findByEmail("apptest@example.com").orElseGet(() -> {
            return userJpaRepository.save(com.odyssey.entity.UserEntity.builder()
                    .id("USR-APPTEST")
                    .email("apptest@example.com")
                    .passwordHash(passwordEncoder.encode("Password123"))
                    .name("App Test User")
                    .createdAt(LocalDateTime.now())
                    .build());
        });
        tripRepository.resetDemoTrip();
    }

    @Test
    @DisplayName("Application Context Loads Successfully")
    void contextLoads() {
        assertNotNull(tripRepository);
    }

    @Test
    @DisplayName("GET /api/trips returns pre-seeded Bengaluru to Mangaluru trip")
    void testGetAllTrips() throws Exception {
        mockMvc.perform(get("/api/trips").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[*].id", hasItem("TRIP-DEMO-001")))
                .andExpect(jsonPath("$.data[*].title", hasItem(containsString("Bengaluru to Mangaluru"))));
    }

    @Test
    @DisplayName("POST /api/trips/{id}/disruptions cascades AT_RISK to dependent bookings and dispatches alert")
    void testReportDisruptionAndCascade() throws Exception {
        Trip trip = tripRepository.findById("TRIP-DEMO-001").orElseThrow();
        trip.setUserId(testUser.getId());
        tripRepository.save(trip);

        ReportDisruptionRequest disruptionReq = ReportDisruptionRequest.builder()
                .type(DisruptionType.FLIGHT_DELAY)
                .bookingId("BKG-101")
                .delayMinutes(180)
                .reason("Severe crosswind weather delay at departure runway")
                .actor(AuditActor.SYSTEM)
                .build();

        mockMvc.perform(post("/api/trips/TRIP-DEMO-001/disruptions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.trip.status", is("DISRUPTED")))
                .andExpect(jsonPath("$.data.impact.summary", notNullValue()))
                .andExpect(jsonPath("$.data.notification.message", notNullValue()));

        // Verify downstream dependency cascade
        Trip updatedTrip = tripRepository.findById("TRIP-DEMO-001").orElseThrow();
        assertEquals(BookingStatus.DELAYED, updatedTrip.getBookings().get(0).getStatus());
        assertEquals(BookingStatus.AT_RISK, updatedTrip.getBookings().get(1).getStatus());
        assertEquals(BookingStatus.AT_RISK, updatedTrip.getBookings().get(2).getStatus());
        assertEquals(BookingStatus.SAFE, updatedTrip.getBookings().get(3).getStatus());
    }

    @Test
    @DisplayName("GET /api/trips/{id}/impact returns impact report")
    void testGetImpactReport() throws Exception {
        Trip trip = tripRepository.findById("TRIP-DEMO-001").orElseThrow();
        trip.setUserId(testUser.getId());
        tripRepository.save(trip);

        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/impact")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tripId", is("TRIP-DEMO-001")));
    }

    @Test
    @DisplayName("GET /api/trips/{id}/audit returns audit entries")
    void testGetTripAudit() throws Exception {
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/audit")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].action", is("TRIP_INITIALIZED")));
    }

    @Test
    @DisplayName("POST /api/demo/reset restores clean demo state")
    void testDemoReset() throws Exception {
        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is("TRIP-DEMO-001")))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    @DisplayName("Trip creation fails when maxBudget or mustArriveBy are null")
    void testTripConstraintValidation() throws Exception {
        CreateTripRequest invalidTrip = CreateTripRequest.builder()
                .title("Trip without budget constraints")
                .constraints(Constraints.builder().build())
                .build();

        mockMvc.perform(post("/api/trips")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTrip)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.details", hasItems("maxBudget is required", "mustArriveBy is required")));
    }

    @Test
    @DisplayName("Trip creation requires at least one booking and mustArriveBy after earliest departure")
    void testBookingRequirementAndDeadlineValidation() throws Exception {
        LocalDateTime baseTime = LocalDateTime.of(2026, 10, 25, 10, 0);

        // 1. Rejects 0 bookings
        CreateTripRequest zeroBookings = CreateTripRequest.builder()
                .title("Empty Itinerary Trip")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(baseTime.plusHours(5))
                        .build())
                .build();

        mockMvc.perform(post("/api/trips")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroBookings)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("At least one booking is required")));

        // 2. Rejects mustArriveBy earlier than booking start
        com.odyssey.model.Booking bkg = com.odyssey.model.Booking.builder()
                .id("BKG-NEW-1")
                .type(com.odyssey.model.enums.BookingType.BUS)
                .provider("KSRTC Sarige")
                .from("Hubballi")
                .to("Goa")
                .startTime(baseTime)
                .endTime(baseTime.plusHours(4))
                .cost(new BigDecimal("650.00"))
                .build();

        CreateTripRequest invalidDeadline = CreateTripRequest.builder()
                .title("Impossible Deadline Trip")
                .travellerPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(baseTime.minusHours(1)) // before start!
                        .build())
                .bookings(java.util.List.of(bkg))
                .build();

        mockMvc.perform(post("/api/trips")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDeadline)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Must arrive by time")));

        // 3. Successful trip with 10-digit phone normalized, optional email, and structured commitments
        CreateTripRequest validTrip = CreateTripRequest.builder()
                .title("Hubballi to Goa Holiday")
                .travellerName("Ramesh Hegde")
                .travellerPhone("9876543210")
                .travellerEmail("ramesh@example.com")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(baseTime.plusHours(8))
                        .build())
                .bookings(java.util.List.of(bkg))
                .hardCommitments(java.util.List.of(com.odyssey.model.HardCommitment.builder()
                        .name("Sunset Dinner")
                        .commitmentTime(baseTime.plusHours(7))
                        .location("Calangute Beach")
                        .build()))
                .build();

        mockMvc.perform(post("/api/trips")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(testUser.getEmail()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTrip)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.travellerPhone", is("+919876543210")))
                .andExpect(jsonPath("$.data.travellerEmail", is("ramesh@example.com")))
                .andExpect(jsonPath("$.data.hardCommitments[0].name", is("Sunset Dinner")))
                .andExpect(jsonPath("$.data.hardCommitments[0].location", is("Calangute Beach")));
    }
}
