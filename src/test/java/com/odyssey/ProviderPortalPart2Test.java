package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.CabBookingRequest;
import com.odyssey.dto.market.HotelBookingRequest;
import com.odyssey.dto.market.MockPaymentRequest;
import com.odyssey.dto.provider.*;
import com.odyssey.entity.*;
import com.odyssey.model.Booking;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BusinessType;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.model.enums.VehicleCategory;
import com.odyssey.repository.TripRepository;
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
public class ProviderPortalPart2Test {

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
    private HotelRoomTypeJpaRepository roomTypeRepo;

    @Autowired
    private HotelRoomAvailabilityJpaRepository availabilityRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Autowired
    private TripJpaRepository tripJpaRepo;

    @Autowired
    private TripRepository tripRepo;

    @Autowired
    private DisruptionJpaRepository disruptionRepo;

    @Autowired
    private AuditEntryJpaRepository auditRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private MarketplaceService marketplaceService;

    private UserEntity travellerUser;
    private UserEntity providerUserA;
    private UserEntity providerUserB;
    private UserEntity adminUser;

    private ProviderEntity approvedProviderA;
    private ProviderEntity approvedProviderB;

    @BeforeEach
    void setUp() {
        travellerUser = getOrCreateUser("part2_traveller@example.com", "Traveller Two", UserRole.TRAVELLER);
        providerUserA = getOrCreateUser("part2_providerA@example.com", "Provider Hotelier A", UserRole.TRAVELLER);
        providerUserB = getOrCreateUser("part2_providerB@example.com", "Provider Hotelier B", UserRole.TRAVELLER);
        adminUser = getOrCreateUser("part2_admin@example.com", "Admin Boss", UserRole.ADMIN);

        approvedProviderA = getOrCreateProvider(providerUserA, "Grand Palace Hotels", BusinessType.BOTH, ProviderStatus.APPROVED);
        approvedProviderB = getOrCreateProvider(providerUserB, "Bob Cabs & Suites", BusinessType.BOTH, ProviderStatus.APPROVED);

        approvedProviderA.setStatus(ProviderStatus.APPROVED);
        providerRepo.save(approvedProviderA);
        approvedProviderB.setStatus(ProviderStatus.APPROVED);
        providerRepo.save(approvedProviderB);
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
                    .description("Premium hospitality and transport services")
                    .status(status)
                    .reviewedBy(status == ProviderStatus.APPROVED ? "admin@example.com" : null)
                    .reviewedAt(status == ProviderStatus.APPROVED ? LocalDateTime.now() : null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        });
    }

    @Test
    @DisplayName("Hotel and Room Type Registration & Validation Limits")
    void testHotelAndRoomTypeRegistrationAndLimits() throws Exception {
        // Register hotel
        ProviderHotelRequest hotelReq = ProviderHotelRequest.builder()
                .name("The Imperial Palace")
                .city("Bengaluru")
                .address("10 Palace Road, Vasanth Nagar")
                .description("Luxury boutique hotel in central Bengaluru")
                .contactEmail("concierge@imperial.com")
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .build();

        MvcResult result = mockMvc.perform(post("/api/provider/hotels")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("The Imperial Palace"))
                .andExpect(jsonPath("$.data.city").value(equalToIgnoringCase("Bengaluru")))
                .andExpect(jsonPath("$.data.rating").isEmpty()) // Rating is not provider-editable (shows null / Not rated)
                .andReturn();

        ProviderHotelResponse createdHotel = objectMapper.readValue(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").toString(),
                ProviderHotelResponse.class);

        // Add Room Type 1 (Deluxe King)
        ProviderRoomTypeRequest roomReq1 = ProviderRoomTypeRequest.builder()
                .name("Deluxe King Suite")
                .maxGuests(3)
                .pricePerNight(new BigDecimal("3500.00"))
                .roomsTotal(5)
                .amenities("WiFi, AC, Breakfast, King Bed")
                .build();

        mockMvc.perform(post("/api/provider/hotels/" + createdHotel.getId() + "/rooms")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Deluxe King Suite"))
                .andExpect(jsonPath("$.data.roomsTotal").value(5));

        // Add Room Type 2 (Standard Twin)
        ProviderRoomTypeRequest roomReq2 = ProviderRoomTypeRequest.builder()
                .name("Standard Twin")
                .maxGuests(2)
                .pricePerNight(new BigDecimal("2200.00"))
                .roomsTotal(10)
                .amenities("WiFi, Twin Beds")
                .build();

        mockMvc.perform(post("/api/provider/hotels/" + createdHotel.getId() + "/rooms")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomReq2)))
                .andExpect(status().isCreated());

        // Validate bad price rejected
        ProviderRoomTypeRequest badPriceReq = ProviderRoomTypeRequest.builder()
                .name("Free Suite")
                .maxGuests(2)
                .pricePerNight(BigDecimal.ZERO) // Must be > 0
                .roomsTotal(5)
                .build();

        mockMvc.perform(post("/api/provider/hotels/" + createdHotel.getId() + "/rooms")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPriceReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("14-Day Date-based Availability Materialisation & Date Closing")
    void testAvailabilityMaterialisationAndClosing() throws Exception {
        // Register hotel & room type
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderA.getId())
                .name("Mysuru Garden Villa")
                .city("Mysuru")
                .address("15 Chamundi Hill Road")
                .contactEmail("villa@mysuru.com")
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        ProviderRoomTypeRequest roomReq = ProviderRoomTypeRequest.builder()
                .name("Garden Cottage")
                .maxGuests(2)
                .pricePerNight(new BigDecimal("2800.00"))
                .roomsTotal(3)
                .amenities("Lawn, Coffee Maker")
                .build();

        MvcResult roomRes = mockMvc.perform(post("/api/provider/hotels/" + hotel.getId() + "/rooms")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProviderRoomTypeResponse roomType = objectMapper.readValue(
                objectMapper.readTree(roomRes.getResponse().getContentAsString()).get("data").toString(),
                ProviderRoomTypeResponse.class);

        // Verify 14 days of availability are created
        MvcResult availRes = mockMvc.perform(get("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId() + "/availability")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(14))))
                .andReturn();

        LocalDate targetDate = LocalDate.now().plusDays(2);

        // Provider closes the date
        ProviderAvailabilityUpdateDto closeReq = ProviderAvailabilityUpdateDto.builder()
                .stayDate(targetDate)
                .isClosed(true)
                .build();

        mockMvc.perform(put("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId() + "/availability")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Traveller tries to book on closed date -> 409 Conflict
        HotelBookingRequest bookReq = HotelBookingRequest.builder()
                .hotelId(hotel.getId())
                .roomTypeId(roomType.getId())
                .checkIn(targetDate)
                .checkOut(targetDate.plusDays(1))
                .rooms(1)
                .guestName("Priya Sharma")
                .createTrip(true)
                .build();

        mockMvc.perform(post("/api/market/hotel-bookings")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isConflict());

        // Provider re-opens the date
        closeReq.setIsClosed(false);
        mockMvc.perform(put("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId() + "/availability")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk());

        // Traveller books on open date -> 201 Created
        mockMvc.perform(post("/api/market/hotel-bookings")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hotelContactEmail").value("villa@mysuru.com")); // Revealed after booking
    }

    @Test
    @DisplayName("Pessimistic Locking Concurrency: Parallel Bookings for Last Room Yield 1 OK and 1 Conflict (409)")
    void testPessimisticLockingConcurrencyForLastRoom() throws Exception {
        // Setup hotel with exactly 1 room
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderA.getId())
                .name("Single Key Haveli")
                .city("Bengaluru")
                .address("1 Residency Road")
                .contactEmail("single@haveli.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        HotelRoomTypeEntity roomType = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("HRT"))
                .hotelId(hotel.getId())
                .name("Solo Studio")
                .maxGuests(1)
                .pricePerNight(new BigDecimal("1800.00"))
                .roomsTotal(1) // Exactly 1 room!
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        LocalDate stayDate = LocalDate.now().plusDays(4);

        // Materialize availability for the stay date
        availabilityRepo.save(HotelRoomAvailabilityEntity.builder()
                .id(IdGenerator.generate("AVA"))
                .roomTypeId(roomType.getId())
                .hotelId(hotel.getId())
                .stayDate(stayDate)
                .roomsTotal(1)
                .roomsBooked(0)
                .isClosed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        HotelBookingRequest bookReq = HotelBookingRequest.builder()
                .hotelId(hotel.getId())
                .roomTypeId(roomType.getId())
                .checkIn(stayDate)
                .checkOut(stayDate.plusDays(1))
                .rooms(1)
                .guestName("Racer Traveller")
                .createTrip(true)
                .build();

        int parallelCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(parallelCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(parallelCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < parallelCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    marketplaceService.bookHotel(bookReq, travellerUser);
                    successCount.incrementAndGet();
                } catch (com.odyssey.exception.ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("CONCURRENCY UNEXPECTED ERROR: " + e);
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire both threads simultaneously
        startGate.countDown();
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed, exactly one should receive 409 Conflict
        assertEquals(1, successCount.get(), "Exactly one booking must succeed for the last room");
        assertEquals(1, conflictCount.get(), "The second booking must receive 409 Conflict (sold out)");
    }

    @Test
    @DisplayName("Deleting or Shrinking Room Total Below Future Bookings Returns 409 Conflict")
    void testDeleteOrShrinkRoomsBelowBookedReturns409() throws Exception {
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderA.getId())
                .name("Heritage Fort")
                .city("Bengaluru")
                .address("2 Fort Road")
                .contactEmail("fort@heritage.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        HotelRoomTypeEntity roomType = roomTypeRepo.save(HotelRoomTypeEntity.builder()
                .id(IdGenerator.generate("HRT"))
                .hotelId(hotel.getId())
                .name("Fort Suite")
                .maxGuests(2)
                .pricePerNight(new BigDecimal("4000.00"))
                .roomsTotal(5)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        LocalDate stayDate = LocalDate.now().plusDays(5);
        availabilityRepo.save(HotelRoomAvailabilityEntity.builder()
                .id(IdGenerator.generate("AVA"))
                .roomTypeId(roomType.getId())
                .hotelId(hotel.getId())
                .stayDate(stayDate)
                .roomsTotal(5)
                .roomsBooked(3) // 3 rooms booked in future
                .isClosed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Attempt to shrink capacity to 2 (below 3 booked) -> 409 Conflict
        ProviderRoomTypeRequest shrinkReq = ProviderRoomTypeRequest.builder()
                .name("Fort Suite")
                .maxGuests(2)
                .pricePerNight(new BigDecimal("4000.00"))
                .roomsTotal(2) // Reduced below 3!
                .build();

        mockMvc.perform(put("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId())
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shrinkReq)))
                .andExpect(status().isConflict());

        // Attempt to delete room type with future bookings -> 409 Conflict
        mockMvc.perform(delete("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId())
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isConflict());

        // Deactivation IS allowed
        ProviderRoomTypeRequest deactivateReq = ProviderRoomTypeRequest.builder()
                .name("Fort Suite")
                .maxGuests(2)
                .pricePerNight(new BigDecimal("4000.00"))
                .roomsTotal(5)
                .active(false)
                .build();

        mockMvc.perform(put("/api/provider/hotels/" + hotel.getId() + "/rooms/" + roomType.getId())
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("Provider Isolation: Provider A Cannot Access or Modify Provider B's Entities")
    void testProviderIsolation() throws Exception {
        // Create hotel under Provider B
        HotelEntity hotelB = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderB.getId())
                .name("Bob's Riverside Retreat")
                .city("Bengaluru")
                .address("5 Riverbank")
                .contactEmail("bob@river.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // Provider A attempts to delete Provider B's hotel -> 404 or 403
        mockMvc.perform(delete("/api/provider/hotels/" + hotelB.getId())
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Provider A attempts to view Provider B's hotel bookings manifest -> 404
        mockMvc.perform(get("/api/provider/hotels/" + hotelB.getId() + "/bookings")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sanitized Hotel Booking Manifest Excludes Sensitive Traveller Information")
    void testSanitizedHotelManifest() throws Exception {
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderA.getId())
                .name("Sanitized Grand")
                .city("Bengaluru")
                .address("7 Sanitized St")
                .contactEmail("sanitized@grand.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TripEntity trip = tripJpaRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(travellerUser.getId())
                .title("Trip to Bengaluru")
                .travellerName("Confidential Traveller")
                .travellerEmail("confidential@traveller.com")
                .travellerPhone("+919999988888")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity booking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("HOTEL")
                .provider(hotel.getName())
                .providerId(approvedProviderA.getId())
                .serviceId(hotel.getId())
                .bookingReference("ODY-SANI01")
                .fromLocation("Bengaluru")
                .toLocation("7 Sanitized St")
                .passengerNames("Confidential Traveller (1 rooms, 2 nights)")
                .cost(new BigDecimal("5000.00"))
                .startTime(LocalDate.now().plusDays(2).atTime(14, 0))
                .endTime(LocalDate.now().plusDays(4).atTime(11, 0))
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .source("PROVIDER")
                .cannotHonour(false)
                .build());

        MvcResult res = mockMvc.perform(get("/api/provider/hotels/" + hotel.getId() + "/bookings")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bookingReference == 'ODY-SANI01')].guestName").value(hasItem("Confidential Traveller")))
                .andReturn();

        String json = res.getResponse().getContentAsString();
        // Asserts sensitive fields NEVER appear in provider manifest
        assertFalse(json.contains("confidential@traveller.com"), "Traveller email must NEVER be shown in provider manifest");
        assertFalse(json.contains("9999988888"), "Traveller phone must NEVER be shown in provider manifest");
        assertFalse(json.contains("4111"), "Credit card info must NEVER be shown in provider manifest");
        assertFalse(json.contains("Trip to Bengaluru"), "Full trip details must not leak in provider manifest");
    }

    @Test
    @DisplayName("Cannot Honour Workflow: Flags Booking, Creates Disruption Alert, Does Not Cancel")
    void testCannotHonourWorkflow() throws Exception {
        HotelEntity hotel = hotelRepo.save(HotelEntity.builder()
                .id(IdGenerator.generate("HTL"))
                .providerId(approvedProviderA.getId())
                .name("Breakdown Hotel")
                .city("Bengaluru")
                .address("9 Broken Way")
                .contactEmail("breakdown@hotel.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        TripEntity trip = tripJpaRepo.save(TripEntity.builder()
                .id(IdGenerator.generate("TRP"))
                .userId(travellerUser.getId())
                .title("Trip with Hotel Breakdown")
                .travellerName("Test Traveller")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity booking = bookingRepo.save(BookingEntity.builder()
                .id(IdGenerator.generate("BKG"))
                .trip(trip)
                .user(travellerUser)
                .type("HOTEL")
                .provider(hotel.getName())
                .providerId(approvedProviderA.getId())
                .serviceId(hotel.getId())
                .bookingReference("ODY-BRK001")
                .fromLocation("Bengaluru")
                .toLocation("9 Broken Way")
                .cost(new BigDecimal("3000.00"))
                .startTime(LocalDate.now().plusDays(3).atTime(14, 0))
                .endTime(LocalDate.now().plusDays(4).atTime(11, 0))
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .cannotHonour(false)
                .build());

        CannotHonourRequest req = CannotHonourRequest.builder()
                .reason("Major plumbing pipe burst in hotel block, facility offline")
                .build();

        mockMvc.perform(post("/api/provider/bookings/" + booking.getId() + "/cannot-honour")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Check booking: status remains CONFIRMED (not auto-cancelled!), cannotHonour = true
        BookingEntity updatedBooking = bookingRepo.findById(booking.getId()).orElseThrow();
        assertEquals("CONFIRMED", updatedBooking.getStatus(), "Booking must NOT be auto-cancelled");
        assertTrue(updatedBooking.getCannotHonour(), "cannotHonour flag must be true");
        assertTrue(updatedBooking.getCannotHonourReason().contains("Major plumbing pipe burst"));

        // Check disruption alert was created on trip with actor SYSTEM
        List<DisruptionEntity> disruptions = disruptionRepo.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertFalse(disruptions.isEmpty(), "Disruption alert must be created");
        DisruptionEntity dis = disruptions.get(0);
        assertEquals("HOTEL_CANNOT_HONOUR", dis.getType());
        assertEquals("SYSTEM", dis.getActor());
        assertTrue(dis.getReason().contains("Provider-reported, not independently verified: Hotel cannot honour booking: Major plumbing pipe burst"));
    }

    @Test
    @DisplayName("Cab Service Registration, Estimated Quotes & Search Integration")
    void testCabServiceRegistrationAndQuotes() throws Exception {
        ProviderCabServiceRequest cabReq = ProviderCabServiceRequest.builder()
                .city("Bengaluru")
                .vehicleCategory(VehicleCategory.SUV)
                .baseFare(new BigDecimal("150.00"))
                .perKmRate(new BigDecimal("22.00"))
                .seats(6)
                .operatingHours("06:00 - 23:00")
                .vehicleNumber("KA-05-MM-9999")
                .build();

        mockMvc.perform(post("/api/provider/cabs")
                        .with(user(providerUserA.getEmail()).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cabReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vehicleCategory").value("SUV"))
                .andExpect(jsonPath("$.data.seats").value(6));

        // Traveller requests cab quote
        mockMvc.perform(get("/api/market/cabs/quote")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "Bengaluru")
                        .param("pickup", "Indiranagar")
                        .param("dropoff", "Electronic City"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.vehicleCategory == 'SUV')]").exists())
                .andExpect(jsonPath("$.data[?(@.vehicleCategory == 'SUV')].estimated").value(hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.vehicleCategory == 'SUV')].listingLabel").value(hasItem(containsString("Provider-listed"))));
    }

    @Test
    @DisplayName("Admin Queue Shows Counts and Suspending Provider Hides Listings & Flags Bookings")
    void testAdminCountsAndSuspensionWorkflow() throws Exception {
        // Admin gets providers list with counts
        mockMvc.perform(get("/api/admin/providers")
                        .with(user(adminUser.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleCount").exists())
                .andExpect(jsonPath("$.data[0].hotelCount").exists())
                .andExpect(jsonPath("$.data[0].cabServiceCount").exists())
                .andExpect(jsonPath("$.data[0].bookingCount").exists());

        // Admin suspends Provider A
        AdminProviderActionRequest suspendReq = AdminProviderActionRequest.builder()
                .reason("Regulatory compliance audit pending")
                .build();

        mockMvc.perform(post("/api/admin/providers/" + approvedProviderA.getId() + "/suspend")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        // Suspended provider's hotels are hidden from traveller search
        mockMvc.perform(get("/api/market/hotels")
                        .with(user(travellerUser.getEmail()).roles("TRAVELLER"))
                        .param("city", "Bengaluru")
                        .param("checkIn", LocalDate.now().plusDays(2).toString())
                        .param("checkOut", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.providerName == '" + approvedProviderA.getBusinessName() + "')]").doesNotExist());
    }
}
