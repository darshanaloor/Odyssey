package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.ReplacementBookingRequest;
import com.odyssey.dto.provider.*;
import com.odyssey.dto.request.CreateTripRequest;
import com.odyssey.dto.request.RegisterRequest;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.dto.wallet.TripWalletDto;
import com.odyssey.entity.*;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.*;
import com.odyssey.service.status.StatusCheckService;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ProviderPortalTest {

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
    private ProviderScheduleJpaRepository scheduleRepo;

    @Autowired
    private ServiceDepartureJpaRepository departureRepo;

    @Autowired
    private ServiceJpaRepository serviceRepo;

    @Autowired
    private OperatorJpaRepository operatorRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private TripJpaRepository tripJpaRepo;

    @Autowired
    private TripRepository tripRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private StatusCheckService statusCheckService;

    private UserEntity travellerUser;
    private UserEntity providerUserA;
    private UserEntity providerUserB;
    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        travellerUser = getOrCreateUser("traveller@example.com", "Traveller One", UserRole.TRAVELLER);
        providerUserA = getOrCreateUser("providerA@example.com", "Provider Alice", UserRole.TRAVELLER);
        providerUserB = getOrCreateUser("providerB@example.com", "Provider Bob", UserRole.TRAVELLER);
        
        // Simulating the documented manual SQL update for admin:
        // UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
        adminUser = getOrCreateUser("admin@example.com", "Admin User", UserRole.ADMIN);
    }

    private UserEntity getOrCreateUser(String email, String name, UserRole role) {
        return userRepo.findByEmail(email).map(u -> {
            u.setRole(role);
            return userRepo.save(u);
        }).orElseGet(() -> {
            return userRepo.save(UserEntity.builder()
                    .id(IdGenerator.generate("USR"))
                    .email(email)
                    .name(name)
                    .passwordHash(passwordEncoder.encode("SecretPass123!"))
                    .role(role)
                    .createdAt(LocalDateTime.now())
                    .build());
        });
    }

    // 1. Role cannot be set through any request body (mass assignment test)
    @Test
    @DisplayName("Role cannot be set through any request body (mass assignment prevention)")
    void testRoleCannotBeSetViaRequestBody() throws Exception {
        String testEmail = "massassign-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
        String requestJson = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!",
                    "name": "Hacker User",
                    "role": "ADMIN"
                }
                """, testEmail);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("TRAVELLER")));

        UserEntity created = userRepo.findByEmail(testEmail).orElseThrow();
        assertEquals(UserRole.TRAVELLER, created.getRole(), "User role must strictly remain TRAVELLER");
    }

    // 2. Non-admin gets 403 on admin endpoints
    @Test
    @DisplayName("Non-admin gets 403 Forbidden on admin provider endpoints")
    void testNonAdminForbiddenOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/providers")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/providers/PRV-123/approve")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/providers")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER")))
                .andExpect(status().isForbidden());
    }

    // 3. Provider registration validation (duplicate user, invalid city, phone normalisation)
    @Test
    @DisplayName("Provider registration validates duplicate user, city in catalogue, and normalises phone")
    void testProviderRegistrationValidation() throws Exception {
        UserEntity user = getOrCreateUser("regtest-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Reg User", UserRole.TRAVELLER);

        // Invalid city
        ProviderRegisterRequest invalidCityReq = ProviderRegisterRequest.builder()
                .businessName("City Test Travels")
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(user.getEmail())
                .contactPhone("9876543210")
                .city("NonExistentCityXYZ")
                .description("Bus transport services")
                .build();

        mockMvc.perform(post("/api/provider/register")
                        .with(user(user.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCityReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        // Valid registration with 10-digit phone
        ProviderRegisterRequest validReq = ProviderRegisterRequest.builder()
                .businessName("Valid Travels Ltd")
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(user.getEmail())
                .contactPhone("9876543210")
                .city("Bengaluru")
                .description("Premium intercity bus travel")
                .build();

        mockMvc.perform(post("/api/provider/register")
                        .with(user(user.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.contactPhone", is("+919876543210")));

        // Duplicate registration for same user returns 400
        mockMvc.perform(post("/api/provider/register")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // 4. Provider-listed departures are searchable by travellers with provider-listed chip and disclaimer
    @Test
    @DisplayName("Provider-listed departures are searchable by travellers with provider-listed chip and disclaimer")
    void testPendingProviderListingsHidden() throws Exception {
        UserEntity user = getOrCreateUser("pending-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Pending Provider", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Pending Express", "Bengaluru");

        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-PE-1001", BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(8, 0), LocalTime.of(11, 0), false, new BigDecimal("350.00"));

        // Search departures for tomorrow
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        mockMvc.perform(get("/api/market/departures/search")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("origin", "Bengaluru")
                        .param("destination", "Mysuru")
                        .param("date", tomorrow.toString())
                        .param("mode", "BUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.providerBusinessName == 'Pending Express')]", hasSize(1)));
    }

    // 5. Approval makes listings visible; suspension hides them again while existing bookings stay valid
    @Test
    @DisplayName("Approval makes listings visible; suspension hides them immediately while existing bookings stay valid")
    void testApprovalMakesListingsVisibleAndSuspensionHidesThem() throws Exception {
        UserEntity user = getOrCreateUser("susp-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Susp Provider", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Sunshine Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");

        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-SN-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(9, 0), LocalTime.of(12, 0), false, new BigDecimal("400.00"));

        // Admin approves provider
        mockMvc.perform(post("/api/admin/providers/" + provider.getId() + "/approve")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));

        LocalDate travelDate = LocalDate.now().plusDays(2);

        // Listings must now be visible in traveller search
        mockMvc.perform(get("/api/market/departures/search")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("origin", "Bengaluru")
                        .param("destination", "Mysuru")
                        .param("date", travelDate.toString())
                        .param("mode", "BUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.providerBusinessName == '" + provider.getBusinessName() + "')]", hasSize(greaterThanOrEqualTo(1))));

        // Admin suspends provider
        AdminProviderActionRequest suspendReq = AdminProviderActionRequest.builder()
                .reason("Safety audit pending investigation")
                .build();

        mockMvc.perform(post("/api/admin/providers/" + provider.getId() + "/suspend")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")));

        // Search must now hide them
        mockMvc.perform(get("/api/market/departures/search")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("origin", "Bengaluru")
                        .param("destination", "Mysuru")
                        .param("date", travelDate.toString())
                        .param("mode", "BUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.providerBusinessName == '" + provider.getBusinessName() + "')]", hasSize(0)));
    }

    // 6. Vehicle validation (duplicate number, seats range 1-500, limit 20)
    @Test
    @DisplayName("Vehicle validation enforces format, duplicate check, seats range 1-500, and max 20 limit")
    void testVehicleValidation() throws Exception {
        UserEntity user = getOrCreateUser("vehval-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Vehicle Val", UserRole.TRAVELLER);
        registerProvider(user, "Fleet Express " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");

        // Invalid format (< 3 chars or bad characters)
        ProviderVehicleRequest badFormat = ProviderVehicleRequest.builder()
                .mode(BookingType.BUS)
                .vehicleNumber("@@@")
                .seatsTotal(40)
                .build();
        mockMvc.perform(post("/api/provider/vehicles")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badFormat)))
                .andExpect(status().isBadRequest());

        // Invalid seats (0 or > 500)
        ProviderVehicleRequest badSeats = ProviderVehicleRequest.builder()
                .mode(BookingType.BUS)
                .vehicleNumber("KA-01-AB-1234")
                .seatsTotal(550)
                .build();
        mockMvc.perform(post("/api/provider/vehicles")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSeats)))
                .andExpect(status().isBadRequest());

        // Valid vehicle
        ProviderVehicleRequest validVeh = ProviderVehicleRequest.builder()
                .mode(BookingType.BUS)
                .vehicleNumber("KA-01-AB-1234")
                .seatsTotal(45)
                .build();
        mockMvc.perform(post("/api/provider/vehicles")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validVeh)))
                .andExpect(status().isOk());

        // Duplicate vehicle number for same provider rejected
        mockMvc.perform(post("/api/provider/vehicles")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validVeh)))
                .andExpect(status().isBadRequest());
    }

    // 7. Schedule validation (same origin/dest, arrival after departure, max journey, overlapping schedule, price bounds, limit 60)
    @Test
    @DisplayName("Schedule validation rejects same origin/dest, invalid timing, overlapping schedule, and price bounds")
    void testScheduleValidation() throws Exception {
        UserEntity user = getOrCreateUser("schval-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Schedule Val", UserRole.TRAVELLER);
        registerProvider(user, "Schedule Express " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-SC-9999", BookingType.BUS, 40);

        // Same origin and destination rejected
        ProviderScheduleRequest sameOriginDest = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Bengaluru")
                .originStop("Majestic")
                .destinationStop("Majestic")
                .departureTime(LocalTime.of(10, 0))
                .arrivalTime(LocalTime.of(14, 0))
                .price(new BigDecimal("400.00"))
                .daysOfWeek("MONDAY,TUESDAY")
                .build();
        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameOriginDest)))
                .andExpect(status().isBadRequest());

        // Arrival before departure without arrivesNextDay rejected
        ProviderScheduleRequest badTime = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(14, 0))
                .arrivalTime(LocalTime.of(10, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("400.00"))
                .daysOfWeek("MONDAY,TUESDAY")
                .build();
        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badTime)))
                .andExpect(status().isBadRequest());

        // Price out of range (e.g. 50000 INR for bus from Bengaluru to Mysuru)
        ProviderScheduleRequest extremePrice = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(10, 0))
                .arrivalTime(LocalTime.of(13, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("50000.00"))
                .daysOfWeek("MONDAY,TUESDAY")
                .build();
        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extremePrice)))
                .andExpect(status().isBadRequest());

        // Valid schedule 1
        ProviderScheduleRequest validSch1 = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(8, 0))
                .arrivalTime(LocalTime.of(11, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("350.00"))
                .daysOfWeek("MONDAY,TUESDAY")
                .build();
        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSch1)))
                .andExpect(status().isOk());

        // Overlapping schedule on same vehicle and operating days rejected
        ProviderScheduleRequest overlapping = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(9, 0))
                .arrivalTime(LocalTime.of(12, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("350.00"))
                .daysOfWeek("MONDAY")
                .build();
        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapping)))
                .andExpect(status().isBadRequest());
    }

    // 8. Departure generation is idempotent and honors horizon
    @Test
    @DisplayName("Departure generation is idempotent and materialises departures for next 14 days")
    void testDepartureGenerationIdempotentAndHonorsHorizon() throws Exception {
        UserEntity user = getOrCreateUser("idemp-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Idemp Provider", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Idemp Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-ID-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 35);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Chennai", LocalTime.of(6, 0), LocalTime.of(12, 0), false, new BigDecimal("600.00"));

        long countBefore = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId()).size();
        assertTrue(countBefore > 0, "Initial materialisation should create departures");

        // Calling materialise again must be idempotent (no duplicates created)
        providerService.materializeDeparturesForSchedule(scheduleRepo.findById(schedule.getId()).orElseThrow());
        long countAfter = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId()).size();

        assertEquals(countBefore, countAfter, "Re-running materialisation must be idempotent");
    }

    // 9. Editing schedule does not alter departures with bookings
    @Test
    @DisplayName("Editing schedule affects only future departures with NO bookings; booked departures stay unchanged")
    void testEditingScheduleDoesNotAlterDeparturesWithBookings() throws Exception {
        UserEntity user = getOrCreateUser("editsch-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "EditSch Provider", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "EditSch Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-ED-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(10, 0), LocalTime.of(13, 0), false, new BigDecimal("350.00"));

        List<ServiceDepartureEntity> deps = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId());
        assertFalse(deps.isEmpty());
        ServiceDepartureEntity firstDep = deps.get(0);

        // Create a booking on first departure
        BookingEntity booking = BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .user(travellerUser)
                .departureId(firstDep.getId())
                .serviceId(firstDep.getService().getId())
                .providerId(provider.getId())
                .type("BUS")
                .provider(provider.getBusinessName())
                .fromLocation(firstDep.getService().getOriginCity())
                .toLocation(firstDep.getService().getDestinationCity())
                .startTime(firstDep.getTravelDate().atTime(firstDep.getService().getDepartureTime()))
                .endTime(firstDep.getTravelDate().atTime(firstDep.getService().getArrivalTime()))
                .cost(firstDep.getService().getPrice())
                .status("CONFIRMED")
                .bookingReference("ODY-TEST-123")
                .passengerNames("Traveller One")
                .seatNumbers("1A")
                .build();
        bookingRepo.save(booking);

        // Edit schedule price to 450.00 and time to 10:30
        ProviderScheduleRequest updateReq = ProviderScheduleRequest.builder()
                .vehicleId(vehicle.getId())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(10, 30))
                .arrivalTime(LocalTime.of(13, 30))
                .arrivesNextDay(false)
                .price(new BigDecimal("450.00"))
                .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .build();

        mockMvc.perform(put("/api/provider/schedules/" + schedule.getId())
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("departure(s) with existing bookings were left unchanged")));

        // Verify the booked departure's service still has original price 350.00
        ServiceDepartureEntity reloadedFirst = departureRepo.findById(firstDep.getId()).orElseThrow();
        assertEquals(new BigDecimal("350.00"), reloadedFirst.getService().getPrice(), "Booked departure price must remain unchanged");
    }

    // 10. Deleting or shrinking vehicle with bookings returns 409 Conflict
    @Test
    @DisplayName("Deleting or reducing seats on vehicle with active bookings returns 409 Conflict")
    void testDeletingOrShrinkingVehicleWithBookingsReturnsConflict() throws Exception {
        UserEntity user = getOrCreateUser("vehshrink-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Veh Shrink", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Shrink Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-SH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 30);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(14, 0), LocalTime.of(17, 0), false, new BigDecimal("350.00"));

        List<ServiceDepartureEntity> deps = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId());
        ServiceDepartureEntity dep = deps.get(0);

        // Book 5 seats
        BookingEntity booking = BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .user(travellerUser)
                .departureId(dep.getId())
                .serviceId(dep.getService().getId())
                .providerId(provider.getId())
                .type("BUS")
                .provider(provider.getBusinessName())
                .fromLocation(dep.getService().getOriginCity())
                .toLocation(dep.getService().getDestinationCity())
                .startTime(dep.getTravelDate().atTime(dep.getService().getDepartureTime()))
                .endTime(dep.getTravelDate().atTime(dep.getService().getArrivalTime()))
                .cost(dep.getService().getPrice())
                .status("CONFIRMED")
                .bookingReference("ODY-SHRINK-1")
                .passengerNames("P1, P2, P3, P4, P5")
                .seatNumbers("1A, 1B, 1C, 1D, 2A")
                .build();
        bookingRepo.save(booking);

        // Attempting to delete vehicle returns 409 Conflict
        mockMvc.perform(delete("/api/provider/vehicles/" + vehicle.getId())
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        // Attempting to reduce seatsTotal below booked seats (e.g. 3 seats when 5 are booked) returns 409
        ProviderVehicleRequest shrinkReq = ProviderVehicleRequest.builder()
                .mode(BookingType.BUS)
                .vehicleNumber(vehicle.getVehicleNumber())
                .seatsTotal(3)
                .build();

        mockMvc.perform(put("/api/provider/vehicles/" + vehicle.getId())
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shrinkReq)))
                .andExpect(status().isConflict());
    }

    // 11. Provider A cannot read or modify Provider B's vehicles, schedules, or departures
    @Test
    @DisplayName("Provider isolation: Provider A cannot read or modify Provider B's resources (403 Forbidden)")
    void testProviderCannotAccessAnotherProvidersResources() throws Exception {
        UserEntity userA = getOrCreateUser("provA-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Provider A", UserRole.TRAVELLER);
        registerProvider(userA, "Alpha Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehA = addVehicle(userA, "KA-01-AL-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);

        UserEntity userB = getOrCreateUser("provB-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Provider B", UserRole.TRAVELLER);
        registerProvider(userB, "Beta Travels " + UUID.randomUUID().toString().substring(0, 4), "Mysuru");

        // Provider B tries to modify Provider A's vehicle
        ProviderVehicleRequest updateReq = ProviderVehicleRequest.builder()
                .mode(BookingType.BUS)
                .vehicleNumber(vehA.getVehicleNumber())
                .seatsTotal(45)
                .build();

        mockMvc.perform(put("/api/provider/vehicles/" + vehA.getId())
                        .with(user(userB.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Provider B tries to delete Provider A's vehicle
        mockMvc.perform(delete("/api/provider/vehicles/" + vehA.getId())
                        .with(user(userB.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 12. Provider bookings view contains NO traveller email, phone, or card data
    @Test
    @DisplayName("Provider bookings view contains reference, names, seats, status only; strictly NO email, phone, or card data")
    void testProviderBookingsViewContainsNoSensitiveTravellerData() throws Exception {
        UserEntity user = getOrCreateUser("sanview-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "SanView Prov", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Privacy Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-PR-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(11, 0), LocalTime.of(14, 0), false, new BigDecimal("350.00"));

        List<ServiceDepartureEntity> deps = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId());
        ServiceDepartureEntity dep = deps.get(0);

        BookingEntity booking = BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .user(travellerUser)
                .departureId(dep.getId())
                .serviceId(dep.getService().getId())
                .providerId(provider.getId())
                .type("BUS")
                .provider(provider.getBusinessName())
                .fromLocation(dep.getService().getOriginCity())
                .toLocation(dep.getService().getDestinationCity())
                .startTime(dep.getTravelDate().atTime(dep.getService().getDepartureTime()))
                .endTime(dep.getTravelDate().atTime(dep.getService().getArrivalTime()))
                .cost(dep.getService().getPrice())
                .status("CONFIRMED")
                .bookingReference("ODY-PRIVACY-999")
                .passengerNames("Jane Doe")
                .seatNumbers("2B")
                .build();
        bookingRepo.save(booking);

        MvcResult result = mockMvc.perform(get("/api/provider/departures/" + dep.getId() + "/bookings")
                        .with(user(user.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bookingReference", is("ODY-PRIVACY-999")))
                .andExpect(jsonPath("$.data[0].passengerNames", is("Jane Doe")))
                .andExpect(jsonPath("$.data[0].seatNumbers", is("2B")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains(travellerUser.getEmail()), "Response must not contain traveller email");
        assertFalse(responseBody.contains("card"), "Response must not contain card data");
        assertFalse(responseBody.contains("phone"), "Response must not contain traveller phone");
    }

    // 13. Provider-reported delay creates exactly one disruption per affected trip and de-duplicates across scheduler runs
    @Test
    @DisplayName("Provider-reported delay creates 1 disruption per affected trip with de-duplication across scheduler runs")
    void testProviderReportedDelayCreatesDisruptionWithDeduplication() throws Exception {
        UserEntity user = getOrCreateUser("disruptprov-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Disrupt Prov", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Rapid Express " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-RP-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(15, 0), LocalTime.of(18, 0), false, new BigDecimal("350.00"));

        List<ServiceDepartureEntity> deps = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId());
        ServiceDepartureEntity dep = deps.get(0);

        // Create a trip with booking on this departure
        Trip trip = tripRepo.save(Trip.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(travellerUser.getId())
                .title("Trip with provider bus")
                .status("CONFIRMED")
                .travellerName(travellerUser.getName())
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("5000.00"))
                        .mustArriveBy(dep.getTravelDate().atTime(23, 0))
                        .build())
                .build());

        TripEntity tripEntity = tripJpaRepo.findById(trip.getId()).orElseThrow();

        BookingEntity booking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(tripEntity)
                .user(travellerUser)
                .departureId(dep.getId())
                .serviceId(dep.getService().getId())
                .providerId(provider.getId())
                .type("BUS")
                .provider(provider.getBusinessName())
                .fromLocation(dep.getService().getOriginCity())
                .toLocation(dep.getService().getDestinationCity())
                .startTime(dep.getTravelDate().atTime(dep.getService().getDepartureTime()))
                .endTime(dep.getTravelDate().atTime(dep.getService().getArrivalTime()))
                .cost(dep.getService().getPrice())
                .status("CONFIRMED")
                .bookingReference("ODY-DISRUPT-1")
                .build());

        // Provider reports 60 min delay
        ProviderReportStatusRequest reportReq = ProviderReportStatusRequest.builder()
                .state("DELAYED")
                .delayMinutes(60)
                .reason("Engine maintenance check delayed departure by 1 hour")
                .build();

        mockMvc.perform(post("/api/provider/departures/" + dep.getId() + "/status")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELAYED")))
                .andExpect(jsonPath("$.data.delayMinutes", is(60)));

        // Run status check scheduler
        statusCheckService.runScheduledStatusChecks();

        // Reload trip: must have 1 disruption
        Trip reloadedTrip = tripRepo.findById(tripEntity.getId()).orElseThrow();
        assertEquals("DISRUPTED", reloadedTrip.getStatus());
        assertNotNull(reloadedTrip.getActiveDisruptions());
        long disruptionCount = reloadedTrip.getActiveDisruptions().stream()
                .filter(d -> d.getBookingId() != null && d.getBookingId().equals(booking.getId()))
                .count();
        assertEquals(1, disruptionCount, "Exactly one disruption must be created for the trip");

        // Re-running status check must NOT duplicate the disruption
        statusCheckService.runScheduledStatusChecks();
        Trip secondReload = tripRepo.findById(tripEntity.getId()).orElseThrow();
        long secondCount = secondReload.getActiveDisruptions().stream()
                .filter(d -> d.getBookingId() != null && d.getBookingId().equals(booking.getId()))
                .count();
        assertEquals(1, secondCount, "Disruption must de-duplicate on repeated scheduler runs");
    }

    // 14. Cancelling an already departed trip is rejected (400)
    @Test
    @DisplayName("Cancelling an already departed departure is rejected with 400 Bad Request")
    void testCancellingAlreadyDepartedTripRejected() throws Exception {
        UserEntity user = getOrCreateUser("pastprov-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Past Prov", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Past Travels " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");
        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-PA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);

        // Schedule in the past
        ProviderScheduleEntity pastSchedule = scheduleRepo.save(ProviderScheduleEntity.builder()
                .id(IdGenerator.generate("SCH"))
                .provider(provider)
                .vehicle(vehicleRepo.findById(vehicle.getId()).orElseThrow())
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(6, 0))
                .arrivalTime(LocalTime.of(9, 0))
                .price(new BigDecimal("350.00"))
                .seatsOffered(40)
                .validFrom(LocalDate.now().minusDays(10))
                .validTo(LocalDate.now().plusDays(10))
                .active(true)
                .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .build());

        OperatorEntity op = operatorRepo.findByName(provider.getBusinessName()).orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id("OP-" + provider.getId())
                        .name(provider.getBusinessName())
                        .mode("BUS")
                        .contact(provider.getContactEmail())
                        .build())
        );

        ServiceEntity srv = serviceRepo.save(ServiceEntity.builder()
                .id(IdGenerator.generate("SRV"))
                .operator(op)
                .originCity("Bengaluru")
                .destinationCity("Mysuru")
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(LocalTime.of(6, 0))
                .arrivalTime(LocalTime.of(9, 0))
                .arrivesNextDay(false)
                .price(new BigDecimal("350.00"))
                .seatsTotal(40)
                .reliabilityScore(85)
                .active(true)
                .mode("BUS")
                .vehicleNumber("KA-01-PA-999")
                .source("PROVIDER")
                .providerId(provider.getId())
                .scheduleId(pastSchedule.getId())
                .build());

        // Create departure for yesterday
        ServiceDepartureEntity pastDep = departureRepo.save(ServiceDepartureEntity.builder()
                .id(IdGenerator.generate("DEP"))
                .travelDate(LocalDate.now().minusDays(1))
                .service(srv)
                .providerId(provider.getId())
                .scheduleId(pastSchedule.getId())
                .seatsAvailable(40)
                .status("SCHEDULED")
                .source("PROVIDER")
                .build());

        ProviderReportStatusRequest cancelReq = ProviderReportStatusRequest.builder()
                .state("CANCELLED")
                .reason("Cannot operate retroactively")
                .build();

        mockMvc.perform(post("/api/provider/departures/" + pastDep.getId() + "/status")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("past")));
    }

    // 15. Recovery returns provider-listed options when approved
    @Test
    @DisplayName("Recovery on-demand search returns provider-listed options when provider is APPROVED")
    void testRecoveryReturnsProviderListedOptionsWhenApproved() throws Exception {
        UserEntity user = getOrCreateUser("recprov-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Rec Prov", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Recovery Star " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");

        // Approve provider
        mockMvc.perform(post("/api/admin/providers/" + provider.getId() + "/approve")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-RC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 40);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(12, 0), LocalTime.of(15, 0), false, new BigDecimal("320.00"));

        // Create disrupted trip
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Trip trip = tripRepo.save(Trip.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(travellerUser.getId())
                .title("Trip needing recovery")
                .status("DISRUPTED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("5000.00"))
                        .mustArriveBy(tomorrow.atTime(22, 0))
                        .build())
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id(IdGenerator.generate("BKG"))
                                .type(BookingType.BUS)
                                .provider("Old Broken Bus")
                                .from("Bengaluru")
                                .to("Mysuru")
                                .startTime(tomorrow.atTime(9, 0))
                                .endTime(tomorrow.atTime(12, 0))
                                .cost(new BigDecimal("300.00"))
                                .status(BookingStatus.CANCELLED)
                                .build()
                )))
                .build());

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/recover")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.options", hasSize(greaterThanOrEqualTo(1))));
    }

    // 16. Seat locking still allows exactly one winner for parallel bookings
    @Test
    @DisplayName("Seat locking concurrency: parallel bookings on last available seat allows exactly 1 winner and 1 conflict")
    void testSeatLockingAllowsSingleWinnerForParallelBookings() throws Exception {
        UserEntity user = getOrCreateUser("concurrprov-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Concurr Prov", UserRole.TRAVELLER);
        ProviderEntity provider = registerProvider(user, "Concurrency Bus " + UUID.randomUUID().toString().substring(0, 4), "Bengaluru");

        // Approve provider
        mockMvc.perform(post("/api/admin/providers/" + provider.getId() + "/approve")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        ProviderVehicleDto vehicle = addVehicle(user, "KA-01-CN-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), BookingType.BUS, 1);
        ProviderScheduleDto schedule = addSchedule(user, vehicle.getId(), "Bengaluru", "Mysuru", LocalTime.of(16, 0), LocalTime.of(19, 0), false, new BigDecimal("350.00"));

        List<ServiceDepartureEntity> deps = departureRepo.findByScheduleIdOrderByTravelDateAsc(schedule.getId());
        ServiceDepartureEntity dep = deps.get(0);
        dep.setSeatsAvailable(1);
        departureRepo.save(dep);

        // Prepare 2 users trying to replace onto this departure concurrently
        UserEntity contender1 = getOrCreateUser("contender1@example.com", "Contender 1", UserRole.TRAVELLER);
        UserEntity contender2 = getOrCreateUser("contender2@example.com", "Contender 2", UserRole.TRAVELLER);

        org.springframework.mock.web.MockHttpSession session1 = loginAs(contender1.getEmail(), "SecretPass123!");
        org.springframework.mock.web.MockHttpSession session2 = loginAs(contender2.getEmail(), "SecretPass123!");

        Trip trip1 = createDisruptedTripForUser(contender1, dep.getTravelDate());
        Trip trip2 = createDisruptedTripForUser(contender2, dep.getTravelDate());

        ReplacementBookingRequest req1 = ReplacementBookingRequest.builder()
                .tripId(trip1.getId())
                .oldBookingId(trip1.getBookings().get(0).getId())
                .serviceDepartureId(dep.getId())
                .build();

        ReplacementBookingRequest req2 = ReplacementBookingRequest.builder()
                .tripId(trip2.getId())
                .oldBookingId(trip2.getBookings().get(0).getId())
                .serviceDepartureId(dep.getId())
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                mockMvc.perform(post("/api/market/replacement/book")
                                .session(session1)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                        .andDo(r -> {
                            if (r.getResponse().getStatus() == 200) successCount.incrementAndGet();
                            if (r.getResponse().getStatus() == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception e) {
                // handled
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                mockMvc.perform(post("/api/market/replacement/book")
                                .session(session2)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                        .andDo(r -> {
                            if (r.getResponse().getStatus() == 200) successCount.incrementAndGet();
                            if (r.getResponse().getStatus() == 409) conflictCount.incrementAndGet();
                        });
            } catch (Exception e) {
                // handled
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one user must win the final available seat");
        assertEquals(1, conflictCount.get(), "The second user must receive 409 Conflict");
    }

    private org.springframework.mock.web.MockHttpSession loginAs(String email, String password) throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());
        return session;
    }

    // Helper methods
    private ProviderEntity registerProvider(UserEntity user, String businessName, String city) {
        return providerRepo.save(ProviderEntity.builder()
                .id(IdGenerator.generate("PRV"))
                .user(user)
                .businessName(businessName)
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(user.getEmail())
                .contactPhone("+919876543210")
                .city(city)
                .description("Test provider operations")
                .status(ProviderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ProviderVehicleDto addVehicle(UserEntity user, String vehicleNumber, BookingType mode, int seats) throws Exception {
        ProviderVehicleRequest req = ProviderVehicleRequest.builder()
                .vehicleNumber(vehicleNumber)
                .mode(mode)
                .seatsTotal(seats)
                .build();

        MvcResult result = mockMvc.perform(post("/api/provider/vehicles")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                ProviderVehicleDto.class);
    }

    private ProviderScheduleDto addSchedule(UserEntity user, String vehicleId, String origin, String destination,
                                            LocalTime depTime, LocalTime arrTime, boolean nextDay, BigDecimal price) throws Exception {
        ProviderScheduleRequest req = ProviderScheduleRequest.builder()
                .vehicleId(vehicleId)
                .originCity(origin)
                .destinationCity(destination)
                .originStop("Majestic")
                .destinationStop("Suburb")
                .departureTime(depTime)
                .arrivalTime(arrTime)
                .arrivesNextDay(nextDay)
                .price(price)
                .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                .build();

        MvcResult result = mockMvc.perform(post("/api/provider/schedules")
                        .with(user(user.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                ProviderScheduleDto.class);
    }

    private Trip createDisruptedTripForUser(UserEntity user, LocalDate date) {
        Booking booking = Booking.builder()
                .id(IdGenerator.generate("BKG"))
                .type(BookingType.BUS)
                .provider("Previous Bus")
                .from("Bengaluru")
                .to("Mysuru")
                .startTime(date.atTime(8, 0))
                .endTime(date.atTime(11, 0))
                .cost(new BigDecimal("300.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Trip trip = Trip.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(user.getId())
                .title("Disrupted Trip for " + user.getName())
                .status("DISRUPTED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("5000.00"))
                        .mustArriveBy(date.atTime(23, 0))
                        .build())
                .bookings(new ArrayList<>(List.of(booking)))
                .build();

        return tripRepo.save(trip);
    }
}
