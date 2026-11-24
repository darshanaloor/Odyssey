package com.odyssey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.ReadinessProperties;
import com.odyssey.dto.readiness.ReadinessBand;
import com.odyssey.dto.readiness.ReadinessFactorDto;
import com.odyssey.dto.readiness.TripReadinessDto;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.HardCommitmentEntity;
import com.odyssey.entity.TripConstraintsEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.repository.jpa.*;
import com.odyssey.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TripReadinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripJpaRepository tripJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private SeatHoldJpaRepository seatHoldJpaRepository;

    @Autowired
    private ServiceDepartureJpaRepository departureJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReadinessProperties readinessProperties;

    private UserEntity userA;
    private UserEntity userB;
    private TripEntity wellBufferedTrip;
    private TripEntity tightTrip;

    @BeforeEach
    public void setup() {
        bookingJpaRepository.deleteAll();
        tripJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        userA = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Arjun Hegde")
                .email("arjun.readiness@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .phone("+919876543210")
                .createdAt(LocalDateTime.now())
                .build());

        userB = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Meera Nair")
                .email("meera.readiness@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .phone("+919876543211")
                .createdAt(LocalDateTime.now())
                .build());

        // 1. Setup Well-buffered Trip
        LocalDateTime now = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        TripEntity wbTrip = TripEntity.builder()
                .id("TRIP-WELL-BUFFERED")
                .userId(userA.getId())
                .title("Well Buffered Business Trip")
                .travellerName("Arjun Hegde")
                .travellerPhone("+919876543210")
                .travellerEmail("arjun.readiness@example.com")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        TripConstraintsEntity goodConstraints = TripConstraintsEntity.builder()
                .id(IdGenerator.generate("tc"))
                .trip(wbTrip)
                .maxBudget(new BigDecimal("15000.00"))
                .mustArriveBy(now.plusHours(10)) // 10h deadline
                .build();
        wbTrip.setConstraints(goodConstraints);

        HardCommitmentEntity hc = HardCommitmentEntity.builder()
                .id(IdGenerator.generate("hc"))
                .trip(wbTrip)
                .name("Client Dinner")
                .commitmentTime(now.plusHours(9))
                .location("Hotel Royal")
                .build();
        wbTrip.setHardCommitments(new ArrayList<>(List.of(hc)));

        wellBufferedTrip = tripJpaRepository.save(wbTrip);

        // Leg 1: Flight 09:00 - 11:00
        BookingEntity b1 = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-WB-01")
                .trip(wellBufferedTrip)
                .type("FLIGHT")
                .provider("IndiGo 6E-201")
                .fromLocation("BLR")
                .toLocation("IXE")
                .startTime(now)
                .endTime(now.plusHours(2)) // 11:00
                .cost(new BigDecimal("3500.00"))
                .status("CONFIRMED")
                .bookingReference("IND-WB-991")
                .vehicleNumber("6E-201")
                .seatNumbers("12A")
                .passengerNames("Arjun Hegde")
                .source("MARKETPLACE")
                .lastStatusSource("LIVE")
                .lastStatusState("ON_TIME")
                .build());

        // Leg 2: Cab 12:30 - 13:30 (90 min buffer after flight arrival 11:00, required is 45)
        BookingEntity b2 = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-WB-02")
                .trip(wellBufferedTrip)
                .type("CAB")
                .provider("MegaCabs Airport")
                .fromLocation("IXE")
                .toLocation("Royal Hotel Mangaluru")
                .startTime(now.plusHours(3).plusMinutes(30)) // 12:30
                .endTime(now.plusHours(4).plusMinutes(30))   // 13:30
                .cost(new BigDecimal("800.00"))
                .status("CONFIRMED")
                .bookingReference("CAB-WB-882")
                .dependsOn(List.of(b1.getId()))
                .source("MARKETPLACE")
                .lastStatusSource("LIVE")
                .lastStatusState("CONFIRMED")
                .build());

        // Leg 3: Hotel 14:00 - Next Day
        BookingEntity b3 = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-WB-03")
                .trip(wellBufferedTrip)
                .type("HOTEL")
                .provider("Royal Hotel Mangaluru")
                .fromLocation("Royal Hotel Mangaluru")
                .toLocation("Royal Hotel Mangaluru")
                .startTime(now.plusHours(5)) // 14:00
                .endTime(now.plusDays(1))
                .cost(new BigDecimal("4000.00"))
                .status("CONFIRMED")
                .bookingReference("HTL-WB-773")
                .hotelContactEmail("frontdesk@royalhotel.com")
                .dependsOn(List.of(b2.getId()))
                .source("MARKETPLACE")
                .lastStatusSource("LIVE")
                .lastStatusState("CONFIRMED")
                .build());

        // 2. Setup Tight Trip
        TripEntity tTrip = TripEntity.builder()
                .id("TRIP-TIGHT")
                .userId(userA.getId())
                .title("Tight Timeline Trip")
                .travellerName("Arjun Hegde")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        TripConstraintsEntity tightConstraints = TripConstraintsEntity.builder()
                .id(IdGenerator.generate("tc"))
                .trip(tTrip)
                .maxBudget(new BigDecimal("5000.00"))
                .mustArriveBy(now.plusHours(2).plusMinutes(15)) // 11:15 deadline (only 15m after leg 1 arrival!)
                .build();
        tTrip.setConstraints(tightConstraints);

        tightTrip = tripJpaRepository.save(tTrip);

        // Leg 1: Bus 09:00 - 11:00
        BookingEntity tb1 = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-TT-01")
                .trip(tightTrip)
                .type("BUS")
                .provider("Express Bus")
                .fromLocation("Bengaluru")
                .toLocation("Mysuru")
                .startTime(now)
                .endTime(now.plusHours(2)) // 11:00
                .cost(new BigDecimal("450.00"))
                .status("CONFIRMED")
                .source("TYPED")
                .build());

        // Leg 2: Cab 11:10 - 11:40 (only 10 min buffer after bus arrival 11:00, required is 30)
        BookingEntity tb2 = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-TT-02")
                .trip(tightTrip)
                .type("CAB")
                .provider("City Cab")
                .fromLocation("Mysuru")
                .toLocation("Meeting Hall")
                .startTime(now.plusHours(2).plusMinutes(10)) // 11:10
                .endTime(now.plusHours(2).plusMinutes(40))   // 11:40
                .cost(new BigDecimal("300.00"))
                .status("CONFIRMED")
                .dependsOn(List.of(tb1.getId()))
                .source("TYPED")
                .build());
    }

    @Test
    @DisplayName("Well-buffered vs Tight Trips: Verifies distinct score and bands")
    public void testWellBufferedVsTightTrips() throws Exception {
        MvcResult resGood = mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.reliable", is(true)))
                .andExpect(jsonPath("$.data.band", is("HIGH")))
                .andReturn();

        TripReadinessDto goodDto = objectMapper.readValue(
                objectMapper.readTree(resGood.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        assertNotNull(goodDto.getScore());
        assertTrue(goodDto.getScore() >= 80.0, "Well-buffered trip should have score >= 80.0, was " + goodDto.getScore());

        MvcResult resTight = mockMvc.perform(get("/api/trips/" + tightTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn();

        TripReadinessDto tightDto = objectMapper.readValue(
                objectMapper.readTree(resTight.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        assertNotNull(tightDto.getScore());
        assertTrue(tightDto.getScore() < goodDto.getScore(),
                "Tight trip score (" + tightDto.getScore() + ") should be significantly lower than good trip score (" + goodDto.getScore() + ")");
    }

    @Test
    @DisplayName("Arithmetic Verification: Points earned over points possible matches overall score")
    public void testArithmeticWithFixtures() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        TripReadinessDto dto = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        double totalEarned = 0.0;
        double totalPossible = 0.0;
        for (ReadinessFactorDto factor : dto.getFactors()) {
            if ("KNOWN".equalsIgnoreCase(factor.getStatus()) && factor.getPointsEarned() != null && factor.getPointsPossible() != null) {
                totalEarned += factor.getPointsEarned();
                totalPossible += factor.getPointsPossible();
            }
        }

        assertTrue(totalPossible > 0);
        double expectedScore = Math.round(((totalEarned / totalPossible) * 100.0) * 10.0) / 10.0;
        assertEquals(expectedScore, dto.getScore(), 0.1, "Calculated overall score should match arithmetic sum");
    }

    @Test
    @DisplayName("UNKNOWN Factor Exclusion: Unknown factors are excluded from the denominator")
    public void testUnknownFactorExclusionFromDenominator() throws Exception {
        // Create trip with single booking, no dependencies, no constraints
        TripEntity isolatedTrip = tripJpaRepository.save(TripEntity.builder()
                .id("TRIP-ISOLATED")
                .userId(userA.getId())
                .title("Single Leg Trip")
                .travellerName("Arjun Hegde")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-ISO-01")
                .trip(isolatedTrip)
                .type("TRAIN")
                .provider("Vande Bharat")
                .fromLocation("Bengaluru")
                .toLocation("Chennai")
                .startTime(LocalDateTime.now().plusDays(3))
                .endTime(LocalDateTime.now().plusDays(3).plusHours(4))
                .cost(new BigDecimal("1200.00"))
                .bookingReference("VB-900")
                .status("CONFIRMED")
                .source("MARKETPLACE")
                .lastStatusSource("LIVE")
                .build());

        MvcResult result = mockMvc.perform(get("/api/trips/" + isolatedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        TripReadinessDto dto = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        // Connection buffers and hard commitment slack must be UNKNOWN
        assertTrue(dto.getUnknownFactors().stream().anyMatch(u -> "connection-buffers".equals(u.getId())));
        assertTrue(dto.getUnknownFactors().stream().anyMatch(u -> "hard-commitment-slack".equals(u.getId())));

        // Verify denominator does not include unknown factors
        double knownDenominator = dto.getFactors().stream()
                .filter(f -> "KNOWN".equalsIgnoreCase(f.getStatus()))
                .mapToDouble(ReadinessFactorDto::getPointsPossible)
                .sum();

        // 3 known factors (completeness 20, alternatives 20, monitoring 15) = 55 total possible
        assertEquals(55.0, knownDenominator, 0.01);
    }

    @Test
    @DisplayName("Fewer than 3 Factors: Returns reliable=false, score=null, band=null and status message")
    public void testFewerThanThreeFactorsHandling() throws Exception {
        // Create trip with zero bookings
        TripEntity emptyTrip = tripJpaRepository.save(TripEntity.builder()
                .id("TRIP-EMPTY")
                .userId(userA.getId())
                .title("Empty Trip")
                .travellerName("Arjun Hegde")
                .status("DRAFT")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/trips/" + emptyTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.reliable", is(false)))
                .andExpect(jsonPath("$.data.score", nullValue()))
                .andExpect(jsonPath("$.data.band", nullValue()))
                .andExpect(jsonPath("$.data.statusMessage", is("Not enough data for a reliable score")));
    }

    @Test
    @DisplayName("Deterministic Repeat: Running readiness check repeatedly yields identical output")
    public void testDeterministicRepeat() throws Exception {
        MvcResult res1 = mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult res2 = mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        TripReadinessDto dto1 = objectMapper.readValue(
                objectMapper.readTree(res1.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);
        TripReadinessDto dto2 = objectMapper.readValue(
                objectMapper.readTree(res2.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        assertEquals(dto1.getScore(), dto2.getScore());
        assertEquals(dto1.getBand(), dto2.getBand());
        assertEquals(dto1.getFactors().size(), dto2.getFactors().size());
        for (int i = 0; i < dto1.getFactors().size(); i++) {
            assertEquals(dto1.getFactors().get(i).getPointsEarned(), dto2.getFactors().get(i).getPointsEarned());
        }
    }

    @Test
    @DisplayName("Weights from Config: Configured weights are applied as factor max points")
    public void testWeightsFromConfig() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        TripReadinessDto dto = objectMapper.readValue(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        for (ReadinessFactorDto factor : dto.getFactors()) {
            if ("connection-buffers".equals(factor.getId())) {
                assertEquals(readinessProperties.getWeights().getConnectionBuffers(), factor.getPointsPossible());
            } else if ("hard-commitment-slack".equals(factor.getId())) {
                assertEquals(readinessProperties.getWeights().getHardCommitmentSlack(), factor.getPointsPossible());
            } else if ("booking-completeness".equals(factor.getId())) {
                assertEquals(readinessProperties.getWeights().getBookingCompleteness(), factor.getPointsPossible());
            } else if ("recovery-alternatives".equals(factor.getId())) {
                assertEquals(readinessProperties.getWeights().getRecoveryAlternatives(), factor.getPointsPossible());
            } else if ("monitoring-coverage".equals(factor.getId())) {
                assertEquals(readinessProperties.getWeights().getMonitoringCoverage(), factor.getPointsPossible());
            }
        }
    }

    @Test
    @DisplayName("Strict Read-Only: Database row counts are completely unchanged after readiness check")
    public void testStrictReadOnlyGuarantees() throws Exception {
        long tripsBefore = tripJpaRepository.count();
        long bookingsBefore = bookingJpaRepository.count();
        long seatHoldsBefore = seatHoldJpaRepository.count();
        long departuresBefore = departureJpaRepository.count();
        long notificationsBefore = notificationJpaRepository.count();

        // Perform readiness query
        mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk());

        long tripsAfter = tripJpaRepository.count();
        long bookingsAfter = bookingJpaRepository.count();
        long seatHoldsAfter = seatHoldJpaRepository.count();
        long departuresAfter = departureJpaRepository.count();
        long notificationsAfter = notificationJpaRepository.count();

        assertEquals(tripsBefore, tripsAfter, "trips count must remain unchanged");
        assertEquals(bookingsBefore, bookingsAfter, "bookings count must remain unchanged");
        assertEquals(seatHoldsBefore, seatHoldsAfter, "seat_holds count must remain unchanged");
        assertEquals(departuresBefore, departuresAfter, "service_departures count must remain unchanged");
        assertEquals(notificationsBefore, notificationsAfter, "notifications count must remain unchanged");
    }

    @Test
    @DisplayName("Ownership Enforcement: 403 Forbidden when accessing another user's trip")
    public void testOwnershipEnforcement() throws Exception {
        mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness")
                        .with(user(userB.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated: 401 Unauthorized when request has no credentials")
    public void testUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/trips/" + wellBufferedTrip.getId() + "/readiness"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Disclaimer & Recommendations: Checks disclaimer and plain text recommendation format")
    public void testDisclaimerAndRecommendations() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/trips/" + tightTrip.getId() + "/readiness")
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disclaimer", is("Readiness checks your trip data. It does not predict disruptions.")))
                .andReturn();

        TripReadinessDto dto = objectMapper.readValue(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("data").toString(),
                TripReadinessDto.class);

        // Tight trip should have recommendations identifying buffer deficit
        assertFalse(dto.getRecommendations().isEmpty());
        assertTrue(dto.getRecommendations().stream().anyMatch(r -> r.getMessage().contains("recommended minimum")));
    }
}
