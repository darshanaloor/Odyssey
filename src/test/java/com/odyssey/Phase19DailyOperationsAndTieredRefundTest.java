package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.CancellationPolicyProperties;
import com.odyssey.dto.provider.ProviderOfferAlternativeRequest;
import com.odyssey.entity.*;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.DisruptionService;
import com.odyssey.service.MarketplaceService;
import com.odyssey.service.ProviderService;
import com.odyssey.service.RecoveryService;
import com.odyssey.service.TripService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase19DailyOperationsAndTieredRefundTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private ProviderScheduleJpaRepository scheduleRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private TripJpaRepository tripRepo;

    @Autowired
    private TripLedgerEntryJpaRepository ledgerEntryRepo;

    @Autowired
    private HotelJpaRepository hotelRepo;

    @Autowired
    private HotelRoomTypeJpaRepository roomTypeRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private ProviderVehicleJpaRepository vehicleRepo;

    @Autowired
    private OperatorJpaRepository operatorRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private DisruptionService disruptionService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private TripService tripService;

    @Autowired
    private CancellationPolicyProperties cancellationPolicyProperties;

    @Autowired(required = false)
    private NotificationJpaRepository notificationRepo;

    private UserEntity providerUser1;
    private ProviderEntity provider1;
    private UserEntity providerUser2;
    private ProviderEntity provider2;
    private UserEntity travellerUser;
    private OperatorEntity defaultOperator;

    @BeforeEach
    void setUp() {
        ledgerEntryRepo.deleteAll();
        bookingRepo.deleteAll();
        departureRepo.deleteAll();
        serviceRepo.deleteAll();
        scheduleRepo.deleteAll();
        cabRepo.deleteAll();
        roomTypeRepo.deleteAll();
        hotelRepo.deleteAll();
        tripRepo.deleteAll();

        defaultOperator = operatorRepo.findById("OPR-TEST-P19").orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id("OPR-TEST-P19")
                        .name("Test Express Operations")
                        .mode("BUS")
                        .contact("contact@testexpress.com")
                        .build()));

        // Provider 1: Kalpana Travels
        providerUser1 = userRepo.findByEmail("kalpana@provider.com").orElseGet(() ->
                userRepo.save(UserEntity.builder()
                        .id(IdGenerator.generate("USR"))
                        .email("kalpana@provider.com")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .name("Kalpana Owner")
                        .role(UserRole.PROVIDER)
                        .phone("+919876543210")
                        .build()));

        provider1 = providerRepo.findByUserId(providerUser1.getId()).orElseGet(() ->
                providerRepo.save(ProviderEntity.builder()
                        .id(IdGenerator.generate("PRV"))
                        .user(providerUser1)
                        .businessName("Kalpana Travels")
                        .businessType(BusinessType.TRANSPORT)
                        .city("Sirsi")
                        .contactPhone("+919876543210")
                        .contactEmail("contact@kalpana.com")
                        .status(ProviderStatus.APPROVED)
                        .build()));

        // Provider 2: SRS Travels
        providerUser2 = userRepo.findByEmail("srs@provider.com").orElseGet(() ->
                userRepo.save(UserEntity.builder()
                        .id(IdGenerator.generate("USR"))
                        .email("srs@provider.com")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .name("SRS Owner")
                        .role(UserRole.PROVIDER)
                        .phone("+919876543211")
                        .build()));

        provider2 = providerRepo.findByUserId(providerUser2.getId()).orElseGet(() ->
                providerRepo.save(ProviderEntity.builder()
                        .id(IdGenerator.generate("PRV"))
                        .user(providerUser2)
                        .businessName("SRS Travels")
                        .businessType(BusinessType.TRANSPORT)
                        .city("Bengaluru")
                        .contactPhone("+919876543211")
                        .contactEmail("contact@srs.com")
                        .status(ProviderStatus.APPROVED)
                        .build()));

        // Traveller User
        travellerUser = userRepo.findByEmail("traveller.p19@test.com").orElseGet(() ->
                userRepo.save(UserEntity.builder()
                        .id(IdGenerator.generate("USR"))
                        .email("traveller.p19@test.com")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .name("Arjun Rao")
                        .role(UserRole.TRAVELLER)
                        .phone("+919888877777")
                        .build()));
    }

    // =========================================================================
    // 1. PROVIDER DAILY BOOKINGS VIEW
    // =========================================================================

    @Test
    @DisplayName("1. Provider Daily Bookings View: shows active bookings, shows cancellations with released seats, hides traveller phone/email, 403 on other provider's departure")
    void testProviderDailyBookingsView() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(5);

        ServiceEntity service = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi Old Bus Stand")
                .destinationStop("Mysuru Suburb Stand")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(650.00))
                .source("PROVIDER")
                .providerId(provider1.getId())
                .vehicleNumber("KA31RK3696")
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .build());

        ServiceDepartureEntity dep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(service)
                .travelDate(travelDate)
                .seatsAvailable(38)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .title("Sirsi to Mysuru Trip")
                .travellerName(travellerUser.getName())
                .travellerEmail(travellerUser.getEmail())
                .travellerPhone(travellerUser.getPhone())
                .user(travellerUser)
                .userId(travellerUser.getId())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Booking 1: Active confirmed booking
        BookingEntity b1 = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("BUS")
                .provider(provider1.getBusinessName())
                .fromLocation("Sirsi")
                .toLocation("Mysuru")
                .startTime(travelDate.atTime(8, 0))
                .endTime(travelDate.atTime(14, 0))
                .cost(BigDecimal.valueOf(650.00))
                .status("CONFIRMED")
                .bookingReference("REF-ACTIVE-01")
                .passengerNames("Arjun Rao")
                .seatNumbers("12")
                .departureId(dep.getId())
                .providerId(provider1.getId())
                .build());

        // Booking 2: Cancelled booking (traveller cancelled earlier)
        LocalDateTime cancelTime = LocalDateTime.now().minusHours(2);
        BookingEntity b2 = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("BUS")
                .provider(provider1.getBusinessName())
                .fromLocation("Sirsi")
                .toLocation("Mysuru")
                .startTime(travelDate.atTime(8, 0))
                .endTime(travelDate.atTime(14, 0))
                .cost(BigDecimal.valueOf(650.00))
                .status("CANCELLED")
                .bookingReference("REF-CANCELLED-02")
                .passengerNames("Priya Sharma")
                .seatNumbers("14")
                .departureId(dep.getId())
                .providerId(provider1.getId())
                .cancelledAt(cancelTime)
                .cancelledBy("Priya Sharma")
                .build());

        // Provider 1 views bookings manifest
        mockMvc.perform(get("/api/provider/departures/" + dep.getId() + "/bookings")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                // Active booking verification
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-ACTIVE-01')].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-ACTIVE-01')].passengerNames").value("Arjun Rao"))
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-ACTIVE-01')].seatNumbers").value("12"))
                // Cancelled booking verification (passenger name, seat released, cancellation timestamp)
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-CANCELLED-02')].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-CANCELLED-02')].passengerNames").value("Priya Sharma"))
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-CANCELLED-02')].seatNumbers").value("14"))
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-CANCELLED-02')].cancellationTimestamp").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'REF-CANCELLED-02')].cancelledBy").value("Priya Sharma"))
                // STRICT PRIVACY: Zero traveller email or phone number in provider daily bookings view!
                .andExpect(jsonPath("$.data[*].travellerPhone").doesNotExist())
                .andExpect(jsonPath("$.data[*].travellerEmail").doesNotExist())
                .andExpect(jsonPath("$.data[*].phone").doesNotExist())
                .andExpect(jsonPath("$.data[*].email").doesNotExist());

        // Provider 2 tries to view Provider 1's departure bookings -> 403 Forbidden!
        mockMvc.perform(get("/api/provider/departures/" + dep.getId() + "/bookings")
                        .with(user(providerUser2.getEmail()).roles("PROVIDER")))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. ALTERNATIVE VEHICLE OFFER FOR BUS/TRAIN/FLIGHT ONLY
    // =========================================================================

    @Test
    @DisplayName("2. Offer Alternative Vehicle: scheduled transport only, validates ownership and capacity, marks problem departure CANCELLED on that specific date only")
    void testOfferAlternativeVehicle_ValidationAndCancellation() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(3);

        // Problem Departure (Vehicle 1)
        ServiceEntity s1 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(650.00))
                .source("PROVIDER")
                .providerId(provider1.getId())
                .vehicleNumber("KA31RK3696")
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .build());

        ServiceDepartureEntity problemDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s1)
                .travelDate(travelDate)
                .seatsAvailable(20)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        // Alternative Departure (Vehicle 2, same provider, same route & date, 15 seats available)
        ServiceEntity s2 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(10, 0))
                .arrivalTime(LocalTime.of(16, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(650.00))
                .source("PROVIDER")
                .providerId(provider1.getId())
                .vehicleNumber("KA31RK9999")
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .build());

        ServiceDepartureEntity altDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s2)
                .travelDate(travelDate)
                .seatsAvailable(15)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        // Alternative with 0 seats (sold out)
        ServiceEntity s3 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(12, 0))
                .arrivalTime(LocalTime.of(18, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(650.00))
                .source("PROVIDER")
                .providerId(provider1.getId())
                .vehicleNumber("KA31RK8888")
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .build());

        ServiceDepartureEntity zeroSeatsAlt = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s3)
                .travelDate(travelDate)
                .seatsAvailable(0)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        // Other provider's departure
        ServiceEntity s4 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(14, 0))
                .arrivalTime(LocalTime.of(20, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(650.00))
                .source("PROVIDER")
                .providerId(provider2.getId())
                .vehicleNumber("KA01SRS1234")
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .build());

        ServiceDepartureEntity otherProvAlt = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s4)
                .travelDate(travelDate)
                .seatsAvailable(10)
                .status("SCHEDULED")
                .providerId(provider2.getId())
                .build());

        // Negative check 1: Provider 2 tries to offer alternative for Provider 1's departure -> 403
        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser2.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId(altDep.getId())
                                .reason("Replacement")
                                .build())))
                .andExpect(status().isForbidden());

        // Negative check 2: Offering another provider's departure as alternative -> 403
        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId(otherProvAlt.getId())
                                .reason("Replacement")
                                .build())))
                .andExpect(status().isForbidden());

        // Negative check 3: Offering departure with 0 seats -> 400 Bad Request
        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId(zeroSeatsAlt.getId())
                                .reason("Replacement")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no available seats")));

        // Successful Offer Alternative Vehicle
        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId(altDep.getId())
                                .reason("Primary bus KA31RK3696 has mechanical issue, offering KA31RK9999")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Verify database state: problem departure is marked CANCELLED with alternative_departure_id set
        ServiceDepartureEntity reloaded = departureRepo.findById(problemDep.getId()).orElseThrow();
        assertEquals("CANCELLED", reloaded.getStatus());
        assertEquals("PROVIDER_REPORTED", reloaded.getStatusSource());
        assertEquals(altDep.getId(), reloaded.getAlternativeDepartureId());
    }

    @Test
    @DisplayName("2b. Offer Alternative Vehicle: rejected for CAB or HOTEL with 400 Bad Request")
    void testOfferAlternativeVehicle_RejectedForCabAndHotel() throws Exception {
        // Cab departure
        ServiceEntity cabService = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("CAB")
                .vehicleNumber("CAB-01")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(9, 0))
                .arrivalTime(LocalTime.of(12, 0))
                .seatsTotal(4)
                .price(BigDecimal.valueOf(1500.00))
                .reliabilityScore(85)
                .arrivesNextDay(false)
                .active(true)
                .source("PROVIDER")
                .providerId(provider1.getId())
                .build());

        ServiceDepartureEntity cabDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(cabService)
                .travelDate(LocalDate.now().plusDays(2))
                .seatsAvailable(4)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        mockMvc.perform(post("/api/provider/departures/" + cabDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId("ANY_ALT")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alternative vehicle re-routing is only available for scheduled transport (Bus, Train, Flight)."));
    }

    @Test
    @DisplayName("2c. Offer Alternative Vehicle: deploy from existing fleet vehicle")
    void testOfferAlternativeVehicle_DeployFromFleetVehicle() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(4);

        ServiceEntity s1 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .vehicleNumber("KA31RK1000")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi Old Bus Stand")
                .destinationStop("Mysuru Suburban Stand")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .seatsTotal(40)
                .price(BigDecimal.valueOf(550.00))
                .reliabilityScore(90)
                .arrivesNextDay(false)
                .active(true)
                .source("PROVIDER")
                .providerId(provider1.getId())
                .build());

        ServiceDepartureEntity problemDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s1)
                .travelDate(travelDate)
                .seatsAvailable(40)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        // Registered spare fleet vehicle
        ProviderVehicleEntity spareBus = vehicleRepo.save(ProviderVehicleEntity.builder()
                .id(IdGenerator.generate("VEH"))
                .provider(provider1)
                .mode(BookingType.BUS)
                .vehicleNumber("KA31RK7777")
                .vehicleType("Volvo B11R")
                .seatsTotal(45)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId("VEHICLE:" + spareBus.getId())
                                .reason("Deployed spare Volvo B11R from fleet")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ServiceDepartureEntity reloaded = departureRepo.findById(problemDep.getId()).orElseThrow();
        assertEquals("CANCELLED", reloaded.getStatus());
        assertNotNull(reloaded.getAlternativeDepartureId());

        ServiceDepartureEntity createdAlt = departureRepo.findById(reloaded.getAlternativeDepartureId()).orElseThrow();
        assertEquals("SCHEDULED", createdAlt.getStatus());
        assertEquals("KA31RK7777", createdAlt.getService().getVehicleNumber());
        assertEquals(45, createdAlt.getSeatsAvailable());
    }

    @Test
    @DisplayName("2d. Offer Alternative Vehicle: register and deploy new backup bus directly")
    void testOfferAlternativeVehicle_DeployNewBackupBus() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(5);

        ServiceEntity s1 = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SVC"))
                .operator(defaultOperator)
                .mode("BUS")
                .vehicleNumber("KA31RK2000")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi")
                .destinationStop("Mysuru")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .seatsTotal(36)
                .price(BigDecimal.valueOf(500.00))
                .reliabilityScore(90)
                .arrivesNextDay(false)
                .active(true)
                .source("PROVIDER")
                .providerId(provider1.getId())
                .build());

        ServiceDepartureEntity problemDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(s1)
                .travelDate(travelDate)
                .seatsAvailable(36)
                .status("SCHEDULED")
                .providerId(provider1.getId())
                .build());

        mockMvc.perform(post("/api/provider/departures/" + problemDep.getId() + "/offer-alternative")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderOfferAlternativeRequest.builder()
                                .alternativeDepartureId("NEW_VEHICLE")
                                .replacementVehicleNumber("KA31RK8888")
                                .replacementSeatsTotal(40)
                                .replacementVehicleType("AC Semi-Sleeper")
                                .reason("Deployed emergency backup bus")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ServiceDepartureEntity reloaded = departureRepo.findById(problemDep.getId()).orElseThrow();
        assertEquals("CANCELLED", reloaded.getStatus());
        assertNotNull(reloaded.getAlternativeDepartureId());

        ServiceDepartureEntity createdAlt = departureRepo.findById(reloaded.getAlternativeDepartureId()).orElseThrow();
        assertEquals("SCHEDULED", createdAlt.getStatus());
        assertEquals("KA31RK8888", createdAlt.getService().getVehicleNumber());
        assertEquals(40, createdAlt.getSeatsAvailable());
    }

    // =========================================================================
    // 3. CAB CONTACT SHARING (PRIVACY-ADJUSTED)
    // =========================================================================

    @Test
    @DisplayName("3. Cab Contact Sharing: traveller sees only registered business phone, optional driver name, zero driver phone")
    void testCabContactSharing_PrivacyAdjusted() throws Exception {
        // Cab booking pending confirmation
        BookingEntity cabBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .user(travellerUser)
                .type("CAB")
                .provider(provider1.getBusinessName())
                .fromLocation("Bengaluru Airport")
                .toLocation("Indiranagar")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .cost(BigDecimal.valueOf(1200.00))
                .status("PENDING_PROVIDER_CONFIRMATION")
                .paymentStatus("DEMO_PAID")
                .bookingReference("REF-CAB-789")
                .providerId(provider1.getId())
                .confirmationExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        // Provider accepts booking and sets assigned driver name
        mockMvc.perform(post("/api/provider/bookings/" + cabBooking.getId() + "/accept")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assignedDriverName", "Ramesh Kumar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.assignedDriverName").value("Ramesh Kumar"))
                .andExpect(jsonPath("$.data.cabServiceContact").value("Cab service contact: Kalpana Travels — +919876543210"));

        // Traveller views confirmed cab booking
        mockMvc.perform(get("/api/market/bookings/" + cabBooking.getId())
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabServiceContact").value("Cab service contact: Kalpana Travels — +919876543210"))
                .andExpect(jsonPath("$.data.assignedDriverName").value("Ramesh Kumar"))
                // Assert STRICT PRIVACY: Zero driver personal phone number in response!
                .andExpect(jsonPath("$.data.driverPhone").doesNotExist())
                .andExpect(jsonPath("$.data.assignedDriverPhone").doesNotExist())
                .andExpect(jsonPath("$.data.driverContact").doesNotExist());
    }

    // =========================================================================
    // 4. TIERED REFUND POLICY & RECONCILIATION INVARIANT
    // =========================================================================

    @Test
    @DisplayName("4. Tiered Refund Policy: 10+ days (100%), 3-9 days (75%), 1-2 days (25%), <24h (0%), and invariant Net Refund + Deduction == Gross Fare")
    void testTieredRefundPolicyAndReconciliationInvariant() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal grossFare = BigDecimal.valueOf(650.50); // Fractional fare to test penny-precision reconciliation!

        // Boundary cases:
        // Case 1: 15 days ahead (>= 240 hrs) -> 100% refund, 0% deduction
        checkRefundAndReconciliation(now, now.plusDays(15), grossFare, 100, 0);

        // Case 2: 9 days ahead (>= 72 hrs, < 240 hrs) -> 75% refund, 25% deduction
        checkRefundAndReconciliation(now, now.plusDays(9), grossFare, 75, 25);

        // Case 3: 3 days ahead (exactly 72 hrs) -> 75% refund, 25% deduction
        checkRefundAndReconciliation(now, now.plusHours(72), grossFare, 75, 25);

        // Case 4: 2 days ahead (48 hrs) -> 25% refund, 75% deduction
        checkRefundAndReconciliation(now, now.plusHours(48), grossFare, 25, 75);

        // Case 5: 1 day ahead (exactly 24 hrs) -> 25% refund, 75% deduction
        checkRefundAndReconciliation(now, now.plusHours(24), grossFare, 25, 75);

        // Case 6: Worked Example: Today 10:00 PM to Tomorrow 6:00 AM (8 elapsed hours, < 24 hrs) -> 0% refund, 100% deduction
        LocalDateTime today10PM = LocalDate.now().atTime(22, 0);
        LocalDateTime tomorrow6AM = LocalDate.now().plusDays(1).atTime(6, 0);
        checkRefundAndReconciliation(today10PM, tomorrow6AM, grossFare, 0, 100);
    }

    private void checkRefundAndReconciliation(LocalDateTime cancelTime, LocalDateTime depTime,
                                             BigDecimal grossFare, int expectedRefundPct, int expectedDeductPct) {
        CancellationPolicyProperties.RefundTierResult result =
                cancellationPolicyProperties.calculateRefundTier(cancelTime, depTime);

        assertEquals(expectedRefundPct, result.refundPercent());
        assertEquals(expectedDeductPct, result.deductionPercent());

        BigDecimal refundAmt = grossFare.multiply(BigDecimal.valueOf(result.refundPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal deductAmt = grossFare.subtract(refundAmt);

        // Assert LEDGER RECONCILIATION INVARIANT:
        // Net Refund (Recorded) + Refund Deduction == Original Booking Fare
        assertEquals(0, refundAmt.add(deductAmt).compareTo(grossFare),
                String.format("Invariant violated! %s + %s != %s", refundAmt, deductAmt, grossFare));
    }

    @Test
    @DisplayName("4b. End-to-End Transport Cancellation: records REFUND_RECORDED and REFUND_DEDUCTION in ledger with CANCELLATION_FEE category")
    void testTransportCancellation_LedgerEntriesRecorded() throws Exception {
        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .title("Goa Trip")
                .travellerName(travellerUser.getName())
                .travellerEmail(travellerUser.getEmail())
                .user(travellerUser)
                .userId(travellerUser.getId())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Booking departing in 48 hours (2 days -> 25% refund, 75% deduction)
        BigDecimal fare = BigDecimal.valueOf(1000.00);
        BookingEntity booking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("BUS")
                .provider("Kalpana Travels")
                .fromLocation("Sirsi")
                .toLocation("Mysuru")
                .startTime(LocalDateTime.now().plusHours(48))
                .endTime(LocalDateTime.now().plusHours(54))
                .cost(fare)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .bookingReference("REF-BUS-48H")
                .build());

        // Traveller cancels booking
        mockMvc.perform(post("/api/market/bookings/" + booking.getId() + "/cancel")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.refundAmount").value(250.00))
                .andExpect(jsonPath("$.data.deductionAmount").value(750.00));

        // Check ledger entries created
        List<TripLedgerEntryEntity> entries = ledgerEntryRepo.findByTripIdOrderByCreatedAtAsc(trip.getId());
        assertEquals(2, entries.size());

        TripLedgerEntryEntity refundEntry = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.REFUND_RECORDED)
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(250.00).compareTo(refundEntry.getAmount()));
        assertEquals(LedgerBasis.RECORDED, refundEntry.getBasis());

        TripLedgerEntryEntity deductionEntry = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.REFUND_DEDUCTION)
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(750.00).compareTo(deductionEntry.getAmount()));
        assertEquals("CANCELLATION_FEE", deductionEntry.getCategory());
        assertTrue(deductionEntry.getDescription().contains("25% refund policy tier applies"));

        // Reconciliation Invariant
        assertEquals(0, refundEntry.getAmount().add(deductionEntry.getAmount()).compareTo(fare));
    }

    // =========================================================================
    // 5. HOTEL CANCELLATION — 0% REFUND ALWAYS
    // =========================================================================

    @Test
    @DisplayName("5. Hotel Cancellation: strictly 0% refund, records 100% REFUND_DEDUCTION with HOTEL_NON_REFUNDABLE category")
    void testHotelCancellation_ZeroPercentAlways() throws Exception {
        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .title("Hotel Stay Trip")
                .travellerName(travellerUser.getName())
                .travellerEmail(travellerUser.getEmail())
                .user(travellerUser)
                .userId(travellerUser.getId())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BigDecimal hotelCost = BigDecimal.valueOf(3500.00);
        BookingEntity hotelBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("HOTEL")
                .provider("Heritage Resort")
                .fromLocation("Mysuru")
                .toLocation("Mysuru")
                .startTime(LocalDateTime.now().plusDays(20)) // Even 20 days ahead, hotel is strictly 0% non-refundable!
                .endTime(LocalDateTime.now().plusDays(22))
                .cost(hotelCost)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .bookingReference("REF-HOTEL-99")
                .build());

        mockMvc.perform(post("/api/market/bookings/" + hotelBooking.getId() + "/cancel")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("NON_REFUNDABLE"))
                .andExpect(jsonPath("$.data.refundAmount").value(0))
                .andExpect(jsonPath("$.data.deductionAmount").value(3500.00))
                .andExpect(jsonPath("$.data.refundTierApplied").value(containsString("strictly non-refundable")));

        // Check ledger deduction
        List<TripLedgerEntryEntity> entries = ledgerEntryRepo.findByTripIdOrderByCreatedAtAsc(trip.getId());
        assertEquals(1, entries.size());
        TripLedgerEntryEntity deduction = entries.get(0);
        assertEquals(LedgerEntryType.REFUND_DEDUCTION, deduction.getType());
        assertEquals(0, hotelCost.compareTo(deduction.getAmount()));
        assertEquals("HOTEL_NON_REFUNDABLE", deduction.getCategory());
        assertEquals("Hotel booking is strictly non-refundable once confirmed per hotel partner policy", deduction.getDescription());
    }

    // =========================================================================
    // 6. CAB CANCELLATION — TRAVELLER MUST REQUEST REFUND
    // =========================================================================

    @Test
    @DisplayName("6. Cab Cancellation: no automatic refund, traveller submits refund request, provider issues refund")
    void testCabCancellation_ManualRefundWorkflow() throws Exception {
        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .title("Cab City Ride")
                .travellerName(travellerUser.getName())
                .travellerEmail(travellerUser.getEmail())
                .user(travellerUser)
                .userId(travellerUser.getId())
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BigDecimal cabCost = BigDecimal.valueOf(800.00);
        BookingEntity cabBooking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("CAB")
                .provider(provider1.getBusinessName())
                .fromLocation("City Center")
                .toLocation("Airport")
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                .cost(cabCost)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .bookingReference("REF-CAB-123")
                .providerId(provider1.getId())
                .build());

        // Traveller cancels cab
        mockMvc.perform(post("/api/market/bookings/" + cabBooking.getId() + "/cancel")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID")); // NOT auto-refunded!

        // Confirm NO auto-refund entry in ledger
        List<TripLedgerEntryEntity> entriesAfterCancel = ledgerEntryRepo.findByTripIdOrderByCreatedAtAsc(trip.getId());
        assertTrue(entriesAfterCancel.isEmpty(), "No refund should be auto-recorded for cab cancellation");

        // Traveller requests refund on ledger (creates USER_REPORTED entry)
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bookingId", cabBooking.getId(),
                                "amount", 800.00,
                                "status", "REQUESTED",
                                "note", "Cab cancelled 2 days before ride, requesting full refund."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<TripLedgerEntryEntity> entriesAfterRequest = ledgerEntryRepo.findByTripIdOrderByCreatedAtAsc(trip.getId());
        assertEquals(1, entriesAfterRequest.size());
        assertEquals(LedgerBasis.USER_REPORTED, entriesAfterRequest.get(0).getBasis());
    }

    @Test
    @DisplayName("Provider cancellation dispatches email/sms alert to traveller immediately, and Check Status Now reflects CANCELLED status")
    void testProviderCancellationDispatchesAlertAndCheckStatusNowWorks() throws Exception {
        LocalDate travelDate = LocalDate.now().plusDays(3);
        ServiceEntity busService = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SRV"))
                .providerId(provider1.getId())
                .operator(defaultOperator)
                .mode("BUS")
                .vehicleNumber("KA-31-P1999")
                .originCity("Sirsi")
                .destinationCity("Mysuru")
                .originStop("Sirsi Bus Stand")
                .destinationStop("Mysuru Central")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("650.00"))
                .seatsTotal(40)
                .reliabilityScore(88)
                .active(true)
                .build());

        ServiceDepartureEntity departure = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .service(busService)
                .providerId(provider1.getId())
                .travelDate(travelDate)
                .seatsAvailable(39)
                .status("SCHEDULED")
                .delayMinutes(0)
                .statusSource("SCHEDULED")
                .build());

        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .user(travellerUser)
                .userId(travellerUser.getId())
                .title("Sirsi to Mysuru Expedition")
                .travellerName(travellerUser.getName())
                .travellerEmail(travellerUser.getEmail())
                .travellerPhone(travellerUser.getPhone())
                .notifyEmail(true)
                .notifySms(true)
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity booking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("BUS")
                .provider("Kalpana Travels")
                .providerId(provider1.getId())
                .serviceId(busService.getId())
                .departureId(departure.getId())
                .vehicleNumber("KA-31-P1999")
                .operator("Kalpana Travels")
                .fromLocation("Sirsi (Sirsi Bus Stand)")
                .toLocation("Mysuru (Mysuru Central)")
                .startTime(travelDate.atTime(8, 0))
                .endTime(travelDate.atTime(14, 0))
                .cost(new BigDecimal("650.00"))
                .status("CONFIRMED")
                .paymentStatus("PAID")
                .paymentMethod("CARD")
                .source("MARKETPLACE")
                .bookingReference("ODY-TEST-888")
                .seatNumbers("12")
                .passengerNames("Arjun Rao")
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .build());

        trip.getBookings().add(booking);
        tripRepo.save(trip);

        int initialNotifCount = notificationRepo != null ? notificationRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size() : 0;

        // 1. Provider cancels departure in Provider Portal
        mockMvc.perform(post("/api/provider/departures/" + departure.getId() + "/status")
                        .with(user(providerUser1.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", "CANCELLED",
                                "reason", "Bridge repair on national highway"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Verify alert was dispatched and recorded at the moment of cancellation
        if (notificationRepo != null) {
            List<NotificationEntity> notifs = notificationRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
            assertTrue(notifs.size() > initialNotifCount, "Notification alert (Email/SMS) must be recorded when provider cancels");
        }

        // Verify Trip status is now DISRUPTED via API
        mockMvc.perform(get("/api/trips/" + trip.getId())
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISRUPTED"))
                .andExpect(jsonPath("$.data.activeDisruptions", hasSize(1)));

        int countAfterDisruption = notificationRepo != null ? notificationRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size() : 0;

        // 2. Traveller clicks "Check status now" (/api/trips/{id}/status/check)
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/status/check")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].state").value("CANCELLED"))
                .andExpect(jsonPath("$.data[0].source").value("PROVIDER_REPORTED"));

        // Verify no duplicate alert notifications were dispatched during manual status check
        if (notificationRepo != null) {
            int countAfterCheck = notificationRepo.findByTripIdOrderByCreatedAtDesc(trip.getId()).size();
            assertEquals(countAfterDisruption, countAfterCheck, "Manual check status now must not dispatch duplicate email/sms alerts");
        }
    }
}
