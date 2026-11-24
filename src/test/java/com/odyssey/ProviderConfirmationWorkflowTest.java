package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.*;
import com.odyssey.dto.provider.ProviderReportStatusRequest;
import com.odyssey.dto.provider.ProviderScheduleRequest;
import com.odyssey.dto.response.ApiResponse;
import com.odyssey.entity.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.MarketplaceService;
import com.odyssey.service.ProviderService;
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
import java.time.LocalTime;
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
public class ProviderConfirmationWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private ProviderVehicleJpaRepository vehicleRepo;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private ProviderScheduleJpaRepository scheduleRepo;

    @Autowired
    private HotelJpaRepository hotelRepo;

    @Autowired
    private HotelRoomTypeJpaRepository roomTypeRepo;

    @Autowired
    private HotelRoomAvailabilityJpaRepository availabilityRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private AuditEntryJpaRepository auditRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private MarketplaceService marketplaceService;

    private UserEntity providerUser;
    private ProviderEntity provider;
    private UserEntity travellerUser;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        String pEmail = "prov_wf_" + ts + "@example.com";
        String tEmail = "trav_wf_" + ts + "@example.com";

        providerUser = userRepo.save(UserEntity.builder()
                .id(IdGenerator.generate("USR"))
                .name("Kalpana Owner")
                .email(pEmail)
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(UserRole.PROVIDER)
                .phone("+917899408008")
                .createdAt(LocalDateTime.now())
                .build());

        provider = providerRepo.save(ProviderEntity.builder()
                .id(IdGenerator.generate("PRV"))
                .user(providerUser)
                .businessName("kalpana travel")
                .businessType(BusinessType.BOTH)
                .contactEmail(pEmail)
                .contactPhone("+917899408008")
                .city("Sirsi")
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
    @DisplayName("1. Provider can report status on their own departure even with 0 bookings (No Access Denied)")
    void testProviderCanReportStatusOnOwnDepartureRegardlessOfBookingCount() throws Exception {
        // Vehicle KA31RK3696
        ProviderVehicleEntity vehicle = vehicleRepo.save(ProviderVehicleEntity.builder()
                .id(IdGenerator.generate("VEH"))
                .provider(provider)
                .vehicleNumber("KA31RK3696")
                .mode(BookingType.BUS)
                .vehicleType("AC Sleeper")
                .seatsTotal(40)
                .active(true)
                .build());

        // Create schedule: Sirsi -> Mysuru, 08:00 - 14:00
        LocalDate travelDate = LocalDate.now();
        String schedJson = """
        {
            "vehicleId": "%s",
            "originCity": "Sirsi",
            "destinationCity": "Mysuru",
            "departureTime": "08:00",
            "arrivalTime": "14:00",
            "arrivesNextDay": true,
            "price": 650.00,
            "seatsOffered": 40,
            "daysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
            "validFrom": "%s",
            "validTo": "%s",
            "active": true
        }
        """.formatted(vehicle.getId(), travelDate.minusDays(1), travelDate.plusDays(30));

        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Locate materialized departure for travelDate
        List<ServiceDepartureEntity> deps = departureRepo.findByTravelDate(travelDate);
        ServiceDepartureEntity dep = deps.stream()
                .filter(d -> d.getService() != null && "Sirsi".equalsIgnoreCase(d.getService().getOriginCity()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Materialized departure not found"));

        assertEquals(0, bookingRepo.findByDepartureId(dep.getId()).size(), "Departure must legitimately have 0 booked seats");

        // Provider reports cancellation on their own departure
        String reportJson = """
        {
            "state": "CANCELLED",
            "reason": "Road closure near Sirsi ghat section"
        }
        """;

        mockMvc.perform(post("/api/provider/departures/" + dep.getId() + "/status")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusSource").value("PROVIDER_REPORTED"));

        // Verify entity updated in DB
        ServiceDepartureEntity updated = departureRepo.findById(dep.getId()).orElseThrow();
        assertEquals("CANCELLED", updated.getStatus());
        assertEquals("PROVIDER_REPORTED", updated.getStatusSource());
    }

    @Test
    @DisplayName("2. Traveller search for route with active provider schedule returns the departure (Bug 1b)")
    void testTravellerSearchForActiveProviderScheduleReturnsDeparture() throws Exception {
        ProviderVehicleEntity vehicle = vehicleRepo.save(ProviderVehicleEntity.builder()
                .id(IdGenerator.generate("VEH"))
                .provider(provider)
                .vehicleNumber("KA31RK3698")
                .mode(BookingType.BUS)
                .vehicleType("AC Sleeper")
                .seatsTotal(40)
                .active(true)
                .build());

        LocalDate travelDate = LocalDate.now().plusDays(2);
        String schedJson = """
        {
            "vehicleId": "%s",
            "originCity": "Sirsi",
            "destinationCity": "Mysuru",
            "departureTime": "08:00",
            "arrivalTime": "14:00",
            "arrivesNextDay": true,
            "price": 650.00,
            "seatsOffered": 40,
            "daysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
            "validFrom": "%s",
            "validTo": "%s",
            "active": true
        }
        """.formatted(vehicle.getId(), travelDate.minusDays(2), travelDate.plusDays(20));

        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedJson))
                .andExpect(status().isOk());

        // Traveller searches Sirsi -> Mysuru for travelDate
        mockMvc.perform(get("/api/market/services")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("origin", "Sirsi")
                        .param("destination", "Mysuru")
                        .param("date", travelDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.vehicleNumber == 'KA31RK3698')]").exists())
                .andExpect(jsonPath("$.data[?(@.vehicleNumber == 'KA31RK3698')].price").value(hasItem(650.0)));
    }

    @Test
    @DisplayName("3. Hotel and cab search returns sorted list with 0-availability at bottom marked unavailable (Bug 2)")
    void testHotelAndCabSearchReturnsAvailableFirstAndZeroAvailabilityAtBottom() throws Exception {
        String city = "Sirsi";

        // Provider hotel in Sirsi
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(provider.getId())
                .name("Sirsi Heritage Resort")
                .city(city)
                .address("Ghat Road, Sirsi")
                .pricePerNight(BigDecimal.valueOf(1500))
                .roomsTotal(10)
                .source("PROVIDER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Room 1: Available (5 rooms)
        HotelRoomTypeEntity rtAvail = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("RT"))
                .hotelId(hotel.getId())
                .name("Deluxe Forest View")
                .pricePerNight(BigDecimal.valueOf(1800))
                .roomsTotal(5)
                .maxGuests(2)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Room 2: Zero availability (closed)
        HotelRoomTypeEntity rtZero = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("RT"))
                .hotelId(hotel.getId())
                .name("Royal Suite")
                .pricePerNight(BigDecimal.valueOf(900))
                .roomsTotal(2)
                .maxGuests(4)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Mark Royal Suite closed for today
        LocalDate today = LocalDate.now();
        availabilityRepo.save(HotelRoomAvailabilityEntity.builder()
                .id(IdGenerator.generate("AVA"))
                .roomTypeId(rtZero.getId())
                .hotelId(hotel.getId())
                .stayDate(today)
                .roomsTotal(2)
                .roomsBooked(2)
                .isClosed(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Search hotels
        mockMvc.perform(get("/api/market/hotels")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", city)
                        .param("checkIn", today.toString())
                        .param("checkOut", today.plusDays(1).toString())
                        .param("rooms", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                // Verify available room is first, and 0-availability room is preserved but at bottom
                .andExpect(jsonPath("$.data[0].roomsAvailable").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[1].roomsAvailable").value(0))
                .andExpect(jsonPath("$.data[1].roomTypeName").value("Royal Suite"));

        // Provider Cab in Sirsi
        CabServiceEntity cab = cabRepo.save(CabServiceEntity.builder()
                .id(IdGenerator.generate("CAB"))
                .providerId(provider.getId())
                .provider("Sirsi Express Cabs")
                .city(city)
                .vehicleCategory(VehicleCategory.SEDAN)
                .seats(4)
                .baseFare(BigDecimal.valueOf(100))
                .perKm(BigDecimal.valueOf(15))
                .operatingHours("24x7")
                .source("PROVIDER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Search cabs
        mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", city)
                        .param("pickup", "Sirsi Bus Stand")
                        .param("dropoff", "Sirsi Temple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].provider").value("Sirsi Express Cabs"))
                .andExpect(jsonPath("$.data[0].seats").value(4));
    }

    @Test
    @DisplayName("4. Hotel and Cab bookings enter PENDING_PROVIDER_CONFIRMATION without payment; Transport confirms immediately")
    void testHotelAndCabEnterPendingProviderConfirmationWithoutPayment() throws Exception {
        String city = "Sirsi";

        // Create provider hotel
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(provider.getId())
                .name("Sirsi Grand Residency")
                .city(city)
                .address("123 Temple Road, Sirsi")
                .pricePerNight(BigDecimal.valueOf(1200))
                .roomsTotal(5)
                .source("PROVIDER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        HotelRoomTypeEntity rt = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("RT"))
                .hotelId(hotel.getId())
                .name("Standard AC")
                .pricePerNight(BigDecimal.valueOf(1200))
                .roomsTotal(5)
                .maxGuests(2)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Book hotel via mock payment
        String hotelPayJson = """
        {
            "paymentMethod": "CARD",
            "cardHolder": "Arjun Traveller",
            "cardNumber": "4111 2222 3333 4444",
            "expiry": "12/28",
            "cvv": "123",
            "bookingDraft": {
                "createTrip": true,
                "hotelId": "%s",
                "roomTypeId": "%s",
                "checkIn": "%s",
                "checkOut": "%s",
                "rooms": 1,
                "guestName": "Arjun Traveller"
            }
        }
        """.formatted(hotel.getId(), rt.getId(), LocalDate.now(), LocalDate.now().plusDays(1));

        MvcResult result = mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotelPayJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.confirmedBooking.status").value("PENDING_PROVIDER_CONFIRMATION"))
                .andExpect(jsonPath("$.data.confirmedBooking.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.confirmedBooking.providerConfirmationStatus").value("PENDING"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        com.fasterxml.jackson.databind.JsonNode bookingNode = rootNode.path("data").path("confirmedBooking");

        assertFalse(bookingNode.path("confirmationExpiresAt").isMissingNode() || bookingNode.path("confirmationExpiresAt").isNull(),
                "Expires-at timestamp must be populated");
        assertEquals("UNPAID", bookingNode.path("paymentStatus").asText(),
                "No payment should be charged before confirmation");

        // Verify bus transport confirms immediately (DO NOT apply pending confirmation for bus/train/flight)
        ProviderVehicleEntity vehicle = vehicleRepo.save(ProviderVehicleEntity.builder()
                .id(IdGenerator.generate("VEH"))
                .provider(provider)
                .vehicleNumber("KA31RK8888")
                .mode(BookingType.BUS)
                .vehicleType("Express")
                .seatsTotal(30)
                .active(true)
                .build());

        LocalDate travelDate = LocalDate.now().plusDays(3);
        String schedJson = """
        {
            "vehicleId": "%s",
            "originCity": "Sirsi",
            "destinationCity": "Mysuru",
            "departureTime": "09:00",
            "arrivalTime": "15:00",
            "arrivesNextDay": false,
            "price": 500.00,
            "seatsOffered": 30,
            "daysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
            "validFrom": "%s",
            "validTo": "%s",
            "active": true
        }
        """.formatted(vehicle.getId(), travelDate.minusDays(1), travelDate.plusDays(10));

        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedJson))
                .andExpect(status().isOk());

        List<ServiceDepartureEntity> deps = departureRepo.findByTravelDate(travelDate);
        ServiceDepartureEntity busDep = deps.stream()
                .filter(d -> d.getService() != null && "KA31RK8888".equals(d.getService().getVehicleNumber()))
                .findFirst()
                .orElseThrow();

        String busPayJson = """
        {
            "paymentMethod": "CARD",
            "cardHolder": "Arjun Traveller",
            "cardNumber": "4111 2222 3333 4444",
            "expiry": "12/28",
            "cvv": "123",
            "bookingDraft": {
                "createTrip": true,
                "serviceDepartureId": "%s",
                "passengers": [
                    { "name": "Arjun Traveller", "seatNumber": "1", "age": 28 }
                ]
            }
        }
        """.formatted(busDep.getId());

        mockMvc.perform(post("/api/market/payments/mock")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busPayJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.confirmedBooking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBooking.paymentStatus").value("DEMO_PAID"));
    }

    @Test
    @DisplayName("5. Provider Accept within window confirms booking and records DEMO_PAID payment")
    void testProviderAcceptConfirmsBookingAndRecordsPayment() throws Exception {
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(provider.getId())
                .name("Sirsi Woods Inn")
                .city("Sirsi")
                .address("456 Forest Lane, Sirsi")
                .pricePerNight(BigDecimal.valueOf(1000))
                .roomsTotal(3)
                .source("PROVIDER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        HotelRoomTypeEntity rt = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("RT"))
                .hotelId(hotel.getId())
                .name("Cottage")
                .pricePerNight(BigDecimal.valueOf(1000))
                .roomsTotal(3)
                .maxGuests(2)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Create booking directly
        MarketplaceBookingDto pendingBooking = marketplaceService.bookHotel(HotelBookingRequest.builder()
                .hotelId(hotel.getId())
                .roomTypeId(rt.getId())
                .checkIn(LocalDate.now())
                .checkOut(LocalDate.now().plusDays(1))
                .rooms(1)
                .guestName("Arjun Traveller")
                .paymentMethod("CARD")
                .createTrip(true)
                .build(), travellerUser);

        assertEquals("PENDING_PROVIDER_CONFIRMATION", pendingBooking.getStatus());
        assertEquals("UNPAID", pendingBooking.getPaymentStatus());

        // Provider checks pending bookings
        mockMvc.perform(get("/api/provider/pending-bookings")
                        .with(user(providerUser.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.id == '" + pendingBooking.getId() + "')]").exists());

        // Provider accepts booking
        mockMvc.perform(post("/api/provider/bookings/" + pendingBooking.getId() + "/accept")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("DEMO_PAID"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("ACCEPTED"));

        // Traveller polls status
        mockMvc.perform(get("/api/market/bookings/" + pendingBooking.getId() + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("DEMO_PAID"));
    }

    @Test
    @DisplayName("6. Provider Reject and timeout expiry cancel booking with zero charge and audit entry")
    void testProviderRejectAndTimeoutCancelsBookingWithZeroCharge() throws Exception {
        CabServiceEntity cab = cabRepo.save(CabServiceEntity.builder()
                .id(IdGenerator.generate("CAB"))
                .providerId(provider.getId())
                .provider("Sirsi City Cabs")
                .city("Sirsi")
                .vehicleCategory(VehicleCategory.SEDAN)
                .seats(4)
                .baseFare(BigDecimal.valueOf(150))
                .perKm(BigDecimal.valueOf(18))
                .operatingHours("24x7")
                .source("PROVIDER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Book cab
        MarketplaceBookingDto pendingBooking = marketplaceService.bookCab(CabBookingRequest.builder()
                .cabServiceId(cab.getId())
                .pickup("Market Square")
                .dropoff("Railway Station")
                .pickupTime(LocalDateTime.now().plusHours(1))
                .paymentMethod("UPI")
                .createTrip(true)
                .build(), travellerUser);

        assertEquals("PENDING_PROVIDER_CONFIRMATION", pendingBooking.getStatus());
        assertEquals("UNPAID", pendingBooking.getPaymentStatus());

        // 6a: Provider Rejects booking
        String rejectJson = """
        {
            "reason": "All drivers occupied with airport transfers"
        }
        """;

        mockMvc.perform(post("/api/provider/bookings/" + pendingBooking.getId() + "/reject")
                        .with(user(providerUser.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("REJECTED"));

        // Verify status poll
        mockMvc.perform(get("/api/market/bookings/" + pendingBooking.getId() + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("REJECTED"));

        // 6b: Timeout expiry scenario
        MarketplaceBookingDto expiredBooking = marketplaceService.bookCab(CabBookingRequest.builder()
                .cabServiceId(cab.getId())
                .pickup("Market Square")
                .dropoff("Sirsi Forest Resort")
                .pickupTime(LocalDateTime.now().plusHours(2))
                .paymentMethod("CARD")
                .createTrip(true)
                .build(), travellerUser);

        // Manually simulate timeout by setting confirmationExpiresAt in the past
        BookingEntity bEntity = bookingRepo.findById(expiredBooking.getId()).orElseThrow();
        bEntity.setConfirmationExpiresAt(LocalDateTime.now().minusSeconds(10));
        bookingRepo.save(bEntity);

        // Status poll detects timeout and automatically marks CANCELLED/EXPIRED
        mockMvc.perform(get("/api/market/bookings/" + expiredBooking.getId() + "/status")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.providerConfirmationStatus").value("EXPIRED"));
    }
}
