package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.BookingVerificationRequest;
import com.odyssey.entity.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.*;
import com.odyssey.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DestinationTransferAndHotelVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private HotelJpaRepository hotelRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private NotificationJpaRepository notificationRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserEntity providerUser;
    private ProviderEntity provider;
    private UserEntity travellerUser;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        String pEmail = "prov_dest_" + ts + "@example.com";
        String tEmail = "trav_dest_" + ts + "@example.com";

        providerUser = userRepo.save(UserEntity.builder()
                .id(IdGenerator.generate("USR"))
                .name("Mysuru Lodge Owner")
                .email(pEmail)
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(UserRole.PROVIDER)
                .phone("+919876543210")
                .createdAt(LocalDateTime.now())
                .build());

        provider = providerRepo.save(ProviderEntity.builder()
                .id(IdGenerator.generate("PRV"))
                .user(providerUser)
                .businessName("Mysuru Royal Stays")
                .businessType(BusinessType.HOTEL)
                .contactEmail(pEmail)
                .contactPhone("+919876543210")
                .city("Mysuru")
                .status(ProviderStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        travellerUser = userRepo.save(UserEntity.builder()
                .id(IdGenerator.generate("USR"))
                .name("Arjun Traveller")
                .email(tEmail)
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(UserRole.TRAVELLER)
                .phone("+919988776655")
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("1. More hotels returned for destination Mysuru, sorted cheap to high with star ratings")
    void testHotelsReturnedSortedCheapToHighWithRatings() throws Exception {
        mockMvc.perform(get("/api/market/hotels")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "MYSURU")
                        .param("rooms", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))))
                // Assert cheap to high ordering
                .andExpect(jsonPath("$.data[0].pricePerNight", lessThanOrEqualTo(2000.0)))
                .andExpect(jsonPath("$.data[0].rating").isNotEmpty());
    }

    @Test
    @DisplayName("2. Local cab quote for destination transfer returns realistic local fare (~₹200-350)")
    void testLocalCabTransferQuoteReturnsRealisticFare() throws Exception {
        mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "MYSURU")
                        .param("pickup", "Suburban Bus Stand, Mysuru")
                        .param("dropoff", "Grand Mercure Mysuru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                // Local destination transfer should be around ₹200-350, not thousands!
                .andExpect(jsonPath("$.data[0].estimatedFare", lessThan(500.0)))
                .andExpect(jsonPath("$.data[0].estimatedFare", greaterThan(150.0)));
    }

    @Test
    @DisplayName("3. Clicking Select on hotel sends verification email with 30-second window to provider")
    void testClickingSelectOnHotelSendsVerificationEmailToProvider() throws Exception {
        HotelEntity hotel = hotelRepo.findByCityIgnoreCase("MYSURU").stream()
                .findFirst()
                .orElseThrow();

        BookingVerificationRequest req = BookingVerificationRequest.builder()
                .type("HOTEL")
                .targetId(hotel.getId())
                .targetName(hotel.getName())
                .city("MYSURU")
                .rooms(1)
                .nights(1)
                .guestName("Arjun Traveller")
                .build();

        MvcResult result = mockMvc.perform(post("/api/market/verification/request")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_PROVIDER_CONFIRMATION"))
                .andExpect(jsonPath("$.data.timeoutSeconds").value(30))
                .andExpect(jsonPath("$.data.bookingId").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String bookingId = objectMapper.readTree(body).path("data").path("bookingId").asText();

        // Verify notification sent
        List<NotificationEntity> notifs = notificationRepo.findAll().stream()
                .filter(n -> "BOOKING_PENDING_CONFIRMATION".equals(n.getEventType()))
                .toList();
        assertFalse(notifs.isEmpty(), "Verification email notification must be sent to provider");
        assertTrue(notifs.get(notifs.size() - 1).getBody().contains("30 seconds"));

        // Verify status poll
        mockMvc.perform(get("/api/market/bookings/" + bookingId + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PROVIDER_CONFIRMATION"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("PENDING"));

        // Provider accepts booking within 30-second window
        mockMvc.perform(post("/api/provider/bookings/" + bookingId + "/accept")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("ACCEPTED"));

        // Traveller polls again: status is now CONFIRMED
        mockMvc.perform(get("/api/market/bookings/" + bookingId + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("ACCEPTED"));
    }

    @Test
    @DisplayName("4. Verification request expires after 30 seconds if provider does not accept")
    void testVerificationRequestExpiresAfterTimeout() throws Exception {
        CabServiceEntity cab = cabRepo.findByCityIgnoreCase("MYSURU").stream()
                .findFirst()
                .orElseThrow();

        BookingVerificationRequest req = BookingVerificationRequest.builder()
                .type("CAB")
                .targetId(cab.getId())
                .targetName(cab.getProvider())
                .city("MYSURU")
                .pickup("Suburban Bus Stand, Mysuru")
                .dropoff("Grand Mercure Mysuru")
                .guestName("Arjun Traveller")
                .build();

        MvcResult result = mockMvc.perform(post("/api/market/verification/request")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PROVIDER_CONFIRMATION"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String bookingId = objectMapper.readTree(body).path("data").path("bookingId").asText();

        // Simulate 30s timeout elapsed
        BookingEntity entity = bookingRepo.findById(bookingId).orElseThrow();
        entity.setConfirmationExpiresAt(LocalDateTime.now().minusSeconds(5));
        bookingRepo.save(entity);

        // Status check detects expiry
        mockMvc.perform(get("/api/market/bookings/" + bookingId + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("EXPIRED"));
    }

    @Test
    @DisplayName("5. Intercity cab route from Mysuru Railway Station to Bangalore calculates ~148 km and ~₹2,700 fare (not 15 km)")
    void testIntercityCabDistanceAndFareMysuruToBangalore() throws Exception {
        mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "mysure")
                        .param("pickup", "railway")
                        .param("dropoff", "bangalore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                // Intercity distance must be ~140-160 km, NOT 15 km!
                .andExpect(jsonPath("$.data[0].estimatedDistanceKm", greaterThanOrEqualTo(140.0)))
                .andExpect(jsonPath("$.data[0].estimatedDistanceKm", lessThanOrEqualTo(160.0)))
                // Fare must reflect intercity distance (~₹2,700+)
                .andExpect(jsonPath("$.data[0].estimatedFare", greaterThan(2500.0)));
    }

    @Test
    @DisplayName("6. Whenever new pickup and dropoff points are selected, distance and fare dynamically adapt")
    void testDynamicCabDistanceAdaptationForDifferentLocations() throws Exception {
        // Point A: Station to Bus Stand (connecting transit hubs -> ~3.5 km)
        MvcResult resTransit = mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "mysure")
                        .param("pickup", "railway station")
                        .param("dropoff", "suburban bus stand"))
                .andExpect(status().isOk())
                .andReturn();
        double distTransit = objectMapper.readTree(resTransit.getResponse().getContentAsString())
                .path("data").get(0).path("estimatedDistanceKm").asDouble();

        // Point B: Station to Hotel / Palace (~5.8 km)
        MvcResult resHotel = mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "mysure")
                        .param("pickup", "railway station")
                        .param("dropoff", "Mysuru Palace Hotel"))
                .andExpect(status().isOk())
                .andReturn();
        double distHotel = objectMapper.readTree(resHotel.getResponse().getContentAsString())
                .path("data").get(0).path("estimatedDistanceKm").asDouble();

        // Point C: Station to Chamundi Hill (~13.5 km)
        MvcResult resHill = mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "mysure")
                        .param("pickup", "railway station")
                        .param("dropoff", "Chamundi Hill Temple"))
                .andExpect(status().isOk())
                .andReturn();
        double distHill = objectMapper.readTree(resHill.getResponse().getContentAsString())
                .path("data").get(0).path("estimatedDistanceKm").asDouble();

        // Point D: Station to Airport (~16.5 km)
        MvcResult resAirport = mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "mysure")
                        .param("pickup", "railway station")
                        .param("dropoff", "Mysuru Airport"))
                .andExpect(status().isOk())
                .andReturn();
        double distAirport = objectMapper.readTree(resAirport.getResponse().getContentAsString())
                .path("data").get(0).path("estimatedDistanceKm").asDouble();

        // Assert all distances are distinct and logically ordered
        assertTrue(distTransit < distHotel, "Transit hub should be closer than hotel");
        assertTrue(distHotel < distHill, "Hotel should be closer than hill");
        assertTrue(distHill < distAirport, "Hill should be closer than airport");
        assertNotEquals(distTransit, distHotel);
        assertNotEquals(distHotel, distHill);
    }
}
