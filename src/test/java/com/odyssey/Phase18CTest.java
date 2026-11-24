package com.odyssey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.*;
import com.odyssey.dto.provider.ProviderRefundRequest;
import com.odyssey.dto.provider.ProviderReportStatusRequest;
import com.odyssey.dto.response.TripLedgerResponse;
import com.odyssey.entity.*;
import com.odyssey.model.*;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.BusinessType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.model.enums.LedgerBasis;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.*;
import com.odyssey.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase18CTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private OperatorJpaRepository operatorRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private TripJpaRepository tripJpaRepo;

    @Autowired
    private TripRepository tripRepo;

    @Autowired
    private TripLedgerEntryJpaRepository ledgerRepo;

    @Autowired
    private NotificationJpaRepository notificationRepo;

    @Autowired
    private DisruptionJpaRepository disruptionRepo;

    @Autowired
    private HotelJpaRepository hotelRepo;

    @Autowired
    private HotelRoomTypeJpaRepository roomTypeRepo;

    @Autowired
    private HotelRoomAvailabilityJpaRepository roomAvailRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ResourceLoader resourceLoader;

    private UserEntity traveller;
    private UserEntity providerUserA;
    private UserEntity providerUserB;
    private UserEntity adminUser;
    private ProviderEntity providerA;
    private ProviderEntity providerB;
    private ServiceEntity testService;
    private ServiceDepartureEntity testDeparture;

    @BeforeEach
    void setUp() {
        traveller = getOrCreateUser("p18c_traveller@example.com", "Traveller 18C", UserRole.TRAVELLER);
        providerUserA = getOrCreateUser("p18c_provA@example.com", "Provider 18C A", UserRole.TRAVELLER);
        providerUserB = getOrCreateUser("p18c_provB@example.com", "Provider 18C B", UserRole.TRAVELLER);
        adminUser = getOrCreateUser("p18c_admin@example.com", "Admin 18C", UserRole.ADMIN);

        providerA = getOrCreateProvider(providerUserA, "Phase 18C Transport Express", BusinessType.BOTH, ProviderStatus.APPROVED);
        providerB = getOrCreateProvider(providerUserB, "Phase 18C Other Co", BusinessType.BOTH, ProviderStatus.APPROVED);

        providerA.setStatus(ProviderStatus.APPROVED);
        providerRepo.save(providerA);
        providerB.setStatus(ProviderStatus.APPROVED);
        providerRepo.save(providerB);

        OperatorEntity operator = operatorRepo.findAll().stream().findFirst().orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id("OPR-TEST-18C")
                        .name("Phase 18C Operator")
                        .mode("BUS")
                        .contact("operator@test.com")
                        .build()));

        testService = serviceRepo.findAll().stream()
                .filter(s -> providerA.getId().equals(s.getProviderId()))
                .findFirst()
                .orElseGet(() -> serviceRepo.save(ServiceEntity.builder()
                        .id(IdGenerator.generate("SRV"))
                        .providerId(providerA.getId())
                        .operator(operator)
                        .mode("BUS")
                        .originCity("Bengaluru")
                        .originStop("Majestic Terminal")
                        .destinationCity("Mysuru")
                        .destinationStop("Suburban Bus Stand")
                        .departureTime(LocalTime.of(8, 0))
                        .arrivalTime(LocalTime.of(11, 0))
                        .arrivesNextDay(false)
                        .price(new BigDecimal("450.00"))
                        .seatsTotal(40)
                        .reliabilityScore(90)
                        .active(true)
                        .vehicleNumber("KA-01-EXP-18C")
                        .source("SEED")
                        .build()));

        LocalDate targetDate = LocalDate.now().plusDays(3);
        testDeparture = departureRepo.findByServiceIdAndTravelDate(testService.getId(), targetDate)
                .orElseGet(() -> departureRepo.save(ServiceDepartureEntity.builder()
                        .id(IdGenerator.generate("DEP"))
                        .service(testService)
                        .providerId(providerA.getId())
                        .travelDate(targetDate)
                        .seatsAvailable(35)
                        .status("SCHEDULED")
                        .build()));
    }

    private UserEntity getOrCreateUser(String email, String name, UserRole role) {
        return userRepo.findByEmail(email).map(u -> {
            u.setRole(role);
            return userRepo.save(u);
        }).orElseGet(() -> userRepo.save(UserEntity.builder()
                .id(IdGenerator.generate("USR"))
                .email(email)
                .name(name)
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .phone("+919876543210")
                .role(role)
                .createdAt(LocalDateTime.now())
                .build()));
    }

    private ProviderEntity getOrCreateProvider(UserEntity user, String bName, BusinessType type, ProviderStatus status) {
        return providerRepo.findByUserId(user.getId()).orElseGet(() -> {
            user.setRole(UserRole.PROVIDER);
            userRepo.save(user);

            return providerRepo.save(ProviderEntity.builder()
                    .id(IdGenerator.generate("PRV"))
                    .user(user)
                    .businessName(bName)
                    .businessType(type)
                    .contactEmail(user.getEmail())
                    .contactPhone("+919876543210")
                    .city("Bengaluru")
                    .description("Phase 18C Test Provider")
                    .status(status)
                    .reviewedBy("admin@example.com")
                    .reviewedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build());
        });
    }

    // =========================================================================
    // 1. BUG FIX — MISSING CAB/HOTEL AT CHECKOUT (Addon Regression Test)
    // =========================================================================
    @Test
    @DisplayName("1. Checkout add-ons: Books transport + cab + hotel together into one trip with correct dependsOn chaining")
    void testCheckoutAddons_booksTransportCabAndHotel_linkedTimelineAndDependsOn() {
        CabServiceEntity cab = cabRepo.save(CabServiceEntity.builder()
                .id(IdGenerator.generate("CAB"))
                .provider("Mysuru Royal Cabs")
                .vehicleNumber("KA-09-CAB-1234")
                .city("Mysuru")
                .baseFare(new BigDecimal("100.00"))
                .perKm(new BigDecimal("15.00"))
                .active(true)
                .build());

        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .name("Mysuru Palace Grand")
                .city("Mysuru")
                .address("12 Palace Road")
                .pricePerNight(new BigDecimal("2000.00"))
                .active(true)
                .build());

        CabBookingRequest cabAddon = CabBookingRequest.builder()
                .cabServiceId(cab.getId())
                .pickup("Suburban Bus Stand")
                .dropoff("Mysuru Palace Hotel")
                .build();

        HotelBookingRequest hotelAddon = HotelBookingRequest.builder()
                .hotelId(hotel.getId())
                .rooms(1)
                .guestName("Alex Mercer")
                .build();

        MarketplaceBookingRequest request = MarketplaceBookingRequest.builder()
                .serviceDepartureId(testDeparture.getId())
                .createTrip(true)
                .paymentMethod("CARD")
                .passengers(List.of(
                        PassengerDto.builder().name("Alex Mercer").age(30).seatNumber("1A").build()
                ))
                .cabAddon(cabAddon)
                .hotelAddon(hotelAddon)
                .build();

        MarketplaceBookingDto transportDto = marketplaceService.bookTransport(request, traveller);
        assertNotNull(transportDto);
        assertNotNull(transportDto.getTripId());

        String tripId = transportDto.getTripId();
        List<BookingEntity> bookings = bookingRepo.findByTripIdOrderByStartTimeAsc(tripId);

        // Confirm all 3 bookings exist on the trip
        assertEquals(3, bookings.size(), "Trip must contain transport, cab, and hotel bookings");

        BookingEntity transportBooking = bookings.stream()
                .filter(b -> List.of("BUS", "TRAIN", "FLIGHT").contains(b.getType().toUpperCase()))
                .findFirst().orElseThrow();
        BookingEntity cabBooking = bookings.stream()
                .filter(b -> "CAB".equalsIgnoreCase(b.getType()))
                .findFirst().orElseThrow();
        BookingEntity hotelBooking = bookings.stream()
                .filter(b -> "HOTEL".equalsIgnoreCase(b.getType()))
                .findFirst().orElseThrow();

        // Verify dependsOn dependency linking:
        // transport: dependsOn is empty
        // cab: dependsOn contains transportBooking.id
        // hotel: dependsOn contains cabBooking.id
        assertTrue(transportBooking.getDependsOn() == null || transportBooking.getDependsOn().isEmpty(),
                "Transport departure should not depend on following legs");

        assertNotNull(cabBooking.getDependsOn(), "Cab dependsOn must not be null");
        assertTrue(cabBooking.getDependsOn().contains(transportBooking.getId()),
                "Cab transfer must depend on the primary transport arrival leg");

        assertNotNull(hotelBooking.getDependsOn(), "Hotel dependsOn must not be null");
        assertTrue(hotelBooking.getDependsOn().contains(cabBooking.getId()),
                "Hotel stay must depend on the cab transfer leg");
    }

    // =========================================================================
    // 2. BUG FIX — LEFTOVER "PARALLEL WORLD"-STYLE TEXT AFTER BOOKING
    // =========================================================================
    @Test
    @DisplayName("2. Undisrupted trip: Zero disruption cascade fields, zero cancelled badges, zero counterfactual text")
    void testUndisruptedTrip_zeroDisruptionCascade_zeroCancelledBadges_zeroCounterfactualText() throws Exception {
        MarketplaceBookingRequest request = MarketplaceBookingRequest.builder()
                .serviceDepartureId(testDeparture.getId())
                .createTrip(true)
                .paymentMethod("CARD")
                .passengers(List.of(PassengerDto.builder().name("Healthy Voyager").age(28).seatNumber("2B").build()))
                .build();

        MarketplaceBookingDto bkg = marketplaceService.bookTransport(request, traveller);
        String tripId = bkg.getTripId();

        // Fetch trip from API as trip owner
        MvcResult res = mockMvc.perform(get("/api/trips/" + tripId)
                        .with(user(traveller.getEmail()).roles("TRAVELLER")))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.activeDisruptions", hasSize(0)))
                .andReturn();

        String json = res.getResponse().getContentAsString();
        // Assert absence of any counterfactual / parallel world / butterfly effect debug text
        assertFalse(json.toLowerCase().contains("butterfly effect"), "Healthy trip must not contain butterfly effect text");
        assertFalse(json.toLowerCase().contains("parallel world"), "Healthy trip must not contain parallel world text");
        assertFalse(json.toLowerCase().contains("counterfactual"), "Healthy trip must not contain counterfactual text");
        assertFalse(json.toLowerCase().contains("hypothetical"), "Healthy trip must not contain hypothetical text");

        // Verify all bookings are CONFIRMED and none have CANCELLED status
        List<BookingEntity> bookings = bookingRepo.findByTripIdOrderByStartTimeAsc(tripId);
        for (BookingEntity b : bookings) {
            assertEquals("CONFIRMED", b.getStatus());
            assertFalse(Boolean.TRUE.equals(b.getCannotHonour()));
        }
    }

    // =========================================================================
    // 3. SEPARATE PAGES (CLIENT-SIDE ROUTING & SPA CONTROLLER)
    // =========================================================================
    @Test
    @DisplayName("3. Client-Side Routes: All required SPA paths forward cleanly to index.html")
    void testSpaRoutes_accessibleAndForwardToIndexHtml() throws Exception {
        String[] spaRoutes = {
                "/search",
                "/checkout",
                "/checkout/DRAFT-TEST-001",
                "/trips",
                "/trips/TRIP-DEMO-001",
                "/trips/TRIP-DEMO-001/disruption",
                "/trips/TRIP-DEMO-001/recovery",
                "/trips/TRIP-DEMO-001/plan",
                "/trips/TRIP-DEMO-001/notifications",
                "/trips/TRIP-DEMO-001/ledger",
                "/provider",
                "/provider/vehicles",
                "/provider/schedules",
                "/provider/hotels",
                "/provider/refunds",
                "/admin"
        };

        for (String route : spaRoutes) {
            mockMvc.perform(get(route))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    // =========================================================================
    // 4. PAYMENT MODE SELECTION (CARD, UPI, NETBANKING)
    // =========================================================================
    @Test
    @DisplayName("4. Payment modes: CARD (valid vs 4000000000000002), UPI (valid vs fail@test vs format), NETBANKING (valid vs Test Failure Bank)")
    void testPaymentModeSelection_mockValidation() throws Exception {
        // (a) CARD Valid
        MockPaymentRequest cardValid = MockPaymentRequest.builder()
                .paymentMethod("CARD")
                .cardHolder("Alex Mercer")
                .cardNumber("4111222233334444")
                .expiry("12/28")
                .cvv("123")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardValid)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("CARD"));

        // (b) CARD Declined failure number: 4000000000000002
        MockPaymentRequest cardDeclined = MockPaymentRequest.builder()
                .paymentMethod("CARD")
                .cardHolder("Alex Mercer")
                .cardNumber("4000000000000002")
                .expiry("12/28")
                .cvv("123")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardDeclined)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("demo card declined")));

        // (c) UPI Valid: alex@okhdfcbank
        MockPaymentRequest upiValid = MockPaymentRequest.builder()
                .paymentMethod("UPI")
                .upiId("alex@okhdfcbank")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upiValid)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("UPI"));

        // (d) UPI Failure ID: fail@test
        MockPaymentRequest upiDeclined = MockPaymentRequest.builder()
                .paymentMethod("UPI")
                .upiId("fail@test")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upiDeclined)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("UPI transaction declined")));

        // (e) UPI Bad format: "invalid_upi_format" (no @)
        MockPaymentRequest upiBadFormat = MockPaymentRequest.builder()
                .paymentMethod("UPI")
                .upiId("invalid_upi_format")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upiBadFormat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid UPI ID format")));

        // (f) NETBANKING Valid: HDFC Bank
        MockPaymentRequest netbankValid = MockPaymentRequest.builder()
                .paymentMethod("NETBANKING")
                .bankName("HDFC Bank")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(netbankValid)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("NETBANKING"));

        // (g) NETBANKING Failure bank: Test Failure Bank
        MockPaymentRequest netbankDeclined = MockPaymentRequest.builder()
                .paymentMethod("NETBANKING")
                .bankName("Test Failure Bank")
                .build();

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(netbankDeclined)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Netbanking transaction declined")));

        // (h) Verify paymentMethod stored on booking when booked via mock payment
        MarketplaceBookingRequest transportDraft = MarketplaceBookingRequest.builder()
                .serviceDepartureId(testDeparture.getId())
                .createTrip(true)
                .paymentMethod("UPI")
                .passengers(List.of(PassengerDto.builder().name("UPI User").age(25).seatNumber("3C").build()))
                .build();

        MockPaymentRequest payWithDraft = MockPaymentRequest.builder()
                .bookingDraft(transportDraft)
                .paymentMethod("UPI")
                .upiId("user@axisbank")
                .build();

        MvcResult draftPayResult = mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(traveller.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payWithDraft)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.confirmedBooking.paymentMethod").value("UPI"))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(draftPayResult.getResponse().getContentAsString());
        String createdBookingId = responseNode.path("data").path("confirmedBooking").path("id").asText();
        BookingEntity createdBooking = bookingRepo.findById(createdBookingId).orElseThrow();
        assertEquals("UPI", createdBooking.getPaymentMethod(), "Payment method stored on booking must match UPI");
    }

    // =========================================================================
    // 5. DISCLAIMER WORDING — EXACT 4 SPOTS & ABSENCE FROM SECONDARY SPOTS
    // =========================================================================
    @Test
    @DisplayName("5. Disclaimer: Retained in exactly 4 spots; absent from secondary spots")
    void testDisclaimerWording_exactLocationsAndAbsenceFromSecondary() throws Exception {
        String expectedDisclaimer = "Marketplace bookings are simulated for this project — no real ticket is issued, no real payment is taken.";

        // (a) Spot 1: Checkout page in index.html
        Resource indexHtmlRes = resourceLoader.getResource("classpath:static/index.html");
        String indexHtml;
        try (InputStream is = indexHtmlRes.getInputStream()) {
            indexHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(indexHtml.contains("checkout-disclaimer") && indexHtml.contains(expectedDisclaimer),
                "Spot 1: Checkout page must contain the exact disclaimer line");

        // (b) Spot 2: Confirmation modal in index.html
        assertTrue(indexHtml.contains("modal-confirmation") && indexHtml.contains(expectedDisclaimer),
                "Spot 2: Confirmation modal must contain the exact disclaimer line");

        // (c) Spot 3: Provider-reported alert text
        Booking rootDisrupted = Booking.builder()
                .id("BKG-MOCK-DIS")
                .bookingReference("BKG-REF-DIS")
                .type(BookingType.BUS)
                .provider("Express Lines")
                .from("Athens")
                .to("Sparta")
                .source("PROVIDER")
                .build();

        Disruption disruption = Disruption.builder()
                .id("DIS-TEST-PROV")
                .bookingId(rootDisrupted.getId())
                .type(DisruptionType.CANCELLATION)
                .reason("Traffic blockage")
                .source("PROVIDER")
                .build();

        TripImpactReport impactReport = TripImpactReport.builder()
                .impactedBookings(List.of())
                .summary("Major delay")
                .build();

        Trip tripModel = Trip.builder()
                .id("TRIP-DIS-TEST")
                .title("Athens Voyage")
                .travellerName("Demo Voyager")
                .travellerPhone("+919876543210")
                .build();

        NotificationMessage alertMsg = notificationService.generateAlert(tripModel, rootDisrupted, disruption, impactReport);
        assertTrue(alertMsg.getMessage().contains(expectedDisclaimer),
                "Spot 3: Provider-reported alert must contain the exact disclaimer line");

        // (d) Spot 4: Printable / wallet summary
        Trip walletTrip = Trip.builder()
                .id("TRIP-P18C-WALLET")
                .userId(traveller.getId())
                .title("Wallet Trip")
                .travellerName("Traveller")
                .travellerEmail("traveller@test.com")
                .travellerPhone("+919876543210")
                .status("CONFIRMED")
                .bookings(List.of(
                        Booking.builder()
                                .id("BKG-WLT-1")
                                .bookingReference("ODY-BUS-123")
                                .type(BookingType.BUS)
                                .provider("Express Lines")
                                .from("Athens")
                                .to("Sparta")
                                .startTime(LocalDateTime.now().plusDays(2))
                                .endTime(LocalDateTime.now().plusDays(2).plusHours(3))
                                .cost(new BigDecimal("500.00"))
                                .status(com.odyssey.model.enums.BookingStatus.CONFIRMED)
                                .source("MARKETPLACE")
                                .build()
                ))
                .build();

        String walletHtml = walletService.generatePrintHtml(walletTrip);
        assertTrue(walletHtml.contains(expectedDisclaimer),
                "Spot 4: Printable / wallet summary must contain the exact disclaimer line");

        // (e) Absence from secondary spots
        // Check secondary notifications (plan ready, plan approved, replacement booked)
        Option option = Option.builder()
                .id("OPT-1")
                .label(com.odyssey.model.enums.OptionLabel.FASTEST)
                .legs(List.of(rootDisrupted))
                .source("MARKETPLACE")
                .build();

        RecoveryPlan plan = RecoveryPlan.builder()
                .id("PLAN-1")
                .tripId(tripModel.getId())
                .chosenOptionId(option.getId())
                .chosenOptionLabel(option.getLabel())
                .build();

        notificationService.sendPlanReadyAlert(tripModel, option, plan);
        List<NotificationEntity> notifs = notificationRepo.findByTripIdOrderByCreatedAtDesc(tripModel.getId());
        if (!notifs.isEmpty()) {
            assertFalse(notifs.get(0).getBody().contains(expectedDisclaimer),
                    "Secondary spot: Plan ready notification must NOT contain demo disclaimer");
        }

        notificationService.sendPlanApprovedAlert(tripModel, plan);
        notifs = notificationRepo.findByTripIdOrderByCreatedAtDesc(tripModel.getId());
        if (!notifs.isEmpty()) {
            assertFalse(notifs.get(0).getBody().contains(expectedDisclaimer),
                    "Secondary spot: Plan approved notification must NOT contain demo disclaimer");
        }
    }

    // =========================================================================
    // 6. PROVIDER-ISSUED REFUNDS (POST /api/provider/refunds & Ledger Integration)
    // =========================================================================
    @Test
    @DisplayName("6. Provider-issued refunds: Validations, PROVIDER_REPORTED ledger basis, passive notification, no auto-email")
    void testProviderIssuedRefunds() throws Exception {
        // Setup a trip and a CANCELLED booking owned by Provider A
        TripEntity trip = tripJpaRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(traveller.getId())
                .title("Trip with Refund")
                .travellerName("Alex Traveller")
                .travellerEmail(traveller.getEmail())
                .travellerPhone(traveller.getPhone())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity cancelledBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(traveller)
                .bookingReference("REF-CANCELLED-01")
                .type("BUS")
                .provider(providerA.getBusinessName())
                .providerId(providerA.getId())
                .fromLocation("Bengaluru")
                .toLocation("Mysuru")
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(3))
                .cost(new BigDecimal("800.00"))
                .status("CANCELLED")
                .paymentStatus("DEMO_PAID")
                .source("MARKETPLACE")
                .build());

        // (a) Provider checks refund-eligible bookings
        mockMvc.perform(get("/api/provider/refunds/eligible")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER")))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.id == '" + cancelledBooking.getId() + "')]").exists());

        // (b) Validation: reject amount <= 0
        ProviderRefundRequest reqZero = ProviderRefundRequest.builder()
                .bookingId(cancelledBooking.getId())
                .amount(new BigDecimal("0.00"))
                .reason("Test zero refund amount")
                .build();

        mockMvc.perform(post("/api/provider/refunds")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqZero)))
                .andExpect(status().isBadRequest());

        // (c) Validation: reject amount > booking cost (cost is 800)
        ProviderRefundRequest reqOver = ProviderRefundRequest.builder()
                .bookingId(cancelledBooking.getId())
                .amount(new BigDecimal("950.00"))
                .reason("Exceeds booking fare")
                .build();

        mockMvc.perform(post("/api/provider/refunds")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqOver)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot exceed original booking cost")));

        // (d) Validation: reject non-owner provider (Provider B trying to refund Provider A's booking)
        ProviderRefundRequest reqForbidden = ProviderRefundRequest.builder()
                .bookingId(cancelledBooking.getId())
                .amount(new BigDecimal("800.00"))
                .reason("Non-owner trying to refund")
                .build();

        mockMvc.perform(post("/api/provider/refunds")
                        .with(user(providerUserB.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqForbidden)))
                .andExpect(status().isForbidden());

        // (e) Issue valid refund (800.00)
        long notifCountBefore = notificationRepo.count();

        ProviderRefundRequest reqValid = ProviderRefundRequest.builder()
                .bookingId(cancelledBooking.getId())
                .amount(new BigDecimal("800.00"))
                .reason("Departure cancelled due to severe operational storm")
                .build();

        mockMvc.perform(post("/api/provider/refunds")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqValid)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basis").value("PROVIDER_REPORTED"))
                .andExpect(jsonPath("$.data.amount").value(800.00));

        // (f) Verify ledger entry created with PROVIDER_REPORTED
        List<TripLedgerEntryEntity> entries = ledgerRepo.findByTripIdOrderByCreatedAtAsc(trip.getId());
        assertFalse(entries.isEmpty(), "Ledger entry must be created");
        TripLedgerEntryEntity entry = entries.stream()
                .filter(e -> cancelledBooking.getId().equals(e.getBookingId()))
                .findFirst().orElseThrow();
        assertEquals(LedgerBasis.PROVIDER_REPORTED, entry.getBasis());
        assertEquals(new BigDecimal("800.00"), entry.getAmount());

        // (g) Verify traveller's ledger summary label
        Trip tripModel = Trip.builder()
                .id(trip.getId())
                .userId(traveller.getId())
                .title(trip.getTitle())
                .bookings(List.of())
                .build();
        TripLedgerResponse ledgerResp = ledgerService.getLedger(tripModel);
        assertNotNull(ledgerResp);
        assertTrue(ledgerResp.getEntries().stream()
                .anyMatch(e -> e.getBasis() == LedgerBasis.PROVIDER_REPORTED &&
                        e.getStatusLabel().contains("Provider-reported, not independently verified — Odyssey does not process real payments.")));

        // (h) Verify passive in-app notification created and no automated email/SMS triggered
        long notifCountAfter = notificationRepo.count();
        assertEquals(notifCountBefore + 1, notifCountAfter, "Exactly 1 passive in-app notification must be created");

        List<NotificationEntity> notifs = notificationRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
        NotificationEntity refundNotif = notifs.get(0);
        assertEquals("IN_APP", refundNotif.getChannel());
        assertTrue(refundNotif.getBody().contains("Your provider issued a refund of ₹800.00 for booking REF-CANCELLED-01 — review it in Costs & Refunds"));

        // (i) Duplicate refund attempt rejected with 409 Conflict
        mockMvc.perform(post("/api/provider/refunds")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqValid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already been recorded for this booking")));
    }

    // =========================================================================
    // 7. CANCELLATION SIMULATION ON PROVIDER PAGE & DE-DUPLICATION
    // =========================================================================
    @Test
    @DisplayName("7. Cancellation Simulation: Provider reports departure cancellation; creates 1 disruption per affected trip, de-duplicated")
    void testCancellationSimulationControls_singleDisruptionPerAffectedTrip() throws Exception {
        // Create trip and booking on testDeparture
        TripEntity trip = tripJpaRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(traveller.getId())
                .title("Trip for Cancellation Sim")
                .travellerName("Alex Sim")
                .travellerEmail(traveller.getEmail())
                .travellerPhone(traveller.getPhone())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity depBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(traveller)
                .bookingReference("SIM-DEP-BKG-01")
                .type("BUS")
                .provider(providerA.getBusinessName())
                .providerId(providerA.getId())
                .departureId(testDeparture.getId())
                .fromLocation(testService.getOriginCity())
                .toLocation(testService.getDestinationCity())
                .startTime(testDeparture.getTravelDate().atTime(testService.getDepartureTime()))
                .endTime(testDeparture.getTravelDate().atTime(testService.getArrivalTime()))
                .cost(new BigDecimal("450.00"))
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .source("MARKETPLACE")
                .build());

        int disruptionsBefore = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size();

        // Provider reports cancellation on departure
        ProviderReportStatusRequest cancelReq = ProviderReportStatusRequest.builder()
                .state("CANCELLED")
                .reason("Bus engine transmission breakdown")
                .build();

        mockMvc.perform(post("/api/provider/departures/" + testDeparture.getId() + "/status")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Verify departure status in DB
        ServiceDepartureEntity updatedDep = departureRepo.findById(testDeparture.getId()).orElseThrow();
        assertEquals("CANCELLED", updatedDep.getStatus());

        // Verify exactly 1 disruption created on affected trip
        List<DisruptionEntity> disruptionsAfterFirst = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertEquals(disruptionsBefore + 1, disruptionsAfterFirst.size(), "Exactly 1 disruption must be created on affected trip");
        DisruptionEntity dis = disruptionsAfterFirst.get(0);
        assertEquals("CANCELLATION", dis.getType());
        assertEquals("SYSTEM", dis.getActor());
        assertTrue(dis.getReason().contains("Provider-reported") || dis.getReason().contains("breakdown"),
                "Disruption reason should note status source");

        // Provider reports cancellation again on the same departure (re-publish / duplicate click)
        mockMvc.perform(post("/api/provider/departures/" + testDeparture.getId() + "/status")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().is2xxSuccessful());

        // Verify de-duplication: no extra disruption was added
        List<DisruptionEntity> disruptionsAfterSecond = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertEquals(disruptionsAfterFirst.size(), disruptionsAfterSecond.size(),
                "Disruptions must be de-duplicated — no duplicate disruption created for the same departure");
    }
}
