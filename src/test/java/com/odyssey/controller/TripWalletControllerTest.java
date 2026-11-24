package com.odyssey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.repository.jpa.TripLedgerEntryJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TripWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripJpaRepository tripJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private TripLedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity userA;
    private UserEntity userB;
    private TripEntity tripA;

    @BeforeEach
    public void setup() {
        ledgerEntryJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        tripJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        userA = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Arjun Hegde")
                .email("arjun@example.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .phone("+919876543210")
                .createdAt(LocalDateTime.now())
                .build());

        userB = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Deepa Bhat")
                .email("deepa@example.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .phone("+919123456780")
                .createdAt(LocalDateTime.now())
                .build());

        tripA = tripJpaRepository.save(TripEntity.builder()
                .id("TRIP-WALLET-001")
                .userId(userA.getId())
                .user(userA)
                .title("Goa & Coastal Karnataka Expedition")
                .travellerName("Arjun Hegde")
                .travellerPhone("+919876543210")
                .travellerEmail("arjun@example.com")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("1. Sort order: Chronological by startTime ASC, ties by id ASC, including next-day and multi-day arrivals")
    public void testWalletSortOrderMultiDayAndNextDay() throws Exception {
        LocalDateTime day1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime day2 = LocalDateTime.of(2026, 10, 21, 6, 0);
        LocalDateTime day3Arrival = LocalDateTime.of(2026, 10, 22, 14, 0);

        // Booking 1: Day 2 morning departure arriving Day 3 (multi-day)
        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-002")
                .trip(tripA)
                .user(userA)
                .type("TRAIN")
                .provider("Konkan Kanya Express")
                .fromLocation("Madgaon")
                .toLocation("Mangaluru")
                .startTime(day2)
                .endTime(day3Arrival)
                .cost(new BigDecimal("1200.00"))
                .status("CONFIRMED")
                .build());

        // Booking 2: Day 1 evening departure (tie with Booking 3 on time)
        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-001B")
                .trip(tripA)
                .user(userA)
                .type("CAB")
                .provider("Goa Prepaid Taxi")
                .fromLocation("Airport")
                .toLocation("Panaji")
                .startTime(day1.plusHours(8))
                .endTime(day1.plusHours(9))
                .cost(new BigDecimal("950.00"))
                .status("CONFIRMED")
                .build());

        // Booking 3: Day 1 evening departure (tie with Booking 2, id BKG-001A should sort first)
        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-001A")
                .trip(tripA)
                .user(userA)
                .type("HOTEL")
                .provider("Vivanta Panaji")
                .fromLocation("Panaji")
                .toLocation("Panaji")
                .startTime(day1.plusHours(8))
                .endTime(day2)
                .cost(new BigDecimal("5500.00"))
                .status("CONFIRMED")
                .build());

        // Booking 4: Day 1 morning departure (earliest)
        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-000")
                .trip(tripA)
                .user(userA)
                .type("FLIGHT")
                .provider("IndiGo 6E-202")
                .fromLocation("Bengaluru")
                .toLocation("Goa")
                .startTime(day1)
                .endTime(day1.plusHours(1).plusMinutes(30))
                .cost(new BigDecimal("3400.00"))
                .status("CONFIRMED")
                .build());

        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.bookings", hasSize(4)))
                .andExpect(jsonPath("$.data.bookings[0].id", is("BKG-000")))
                .andExpect(jsonPath("$.data.bookings[1].id", is("BKG-001A")))
                .andExpect(jsonPath("$.data.bookings[2].id", is("BKG-001B")))
                .andExpect(jsonPath("$.data.bookings[3].id", is("BKG-002")));
    }

    @Test
    @DisplayName("2. Stored versus status separation: lastKnownStatus block is strictly separated from stored details")
    public void testStoredVsStatusSeparation() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 14, 0);
        LocalDateTime checked = LocalDateTime.of(2026, 10, 21, 13, 30);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-SEP-1")
                .trip(tripA)
                .user(userA)
                .type("BUS")
                .provider("KSRTC Airavat Club Class")
                .vehicleNumber("KA-01-F-9999")
                .operator("KSRTC")
                .seatNumbers("12A, 12B")
                .passengerNames("Arjun Hegde, Sneha Hegde")
                .bookingReference("KSRTC-998877")
                .fromLocation("Bengaluru Majestic")
                .toLocation("Hubballi Old Bus Stand")
                .startTime(start)
                .endTime(start.plusHours(6))
                .cost(new BigDecimal("1850.00"))
                .status("DELAYED")
                .source("MARKETPLACE")
                .lastStatusState("DELAYED")
                .lastDelayMinutes(45)
                .lastCheckedAt(checked)
                .lastStatusSource("LIVE")
                .build());

        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings[0].provider", is("KSRTC Airavat Club Class")))
                .andExpect(jsonPath("$.data.bookings[0].vehicleNumber", is("KA-01-F-9999")))
                .andExpect(jsonPath("$.data.bookings[0].seats", is("12A, 12B")))
                .andExpect(jsonPath("$.data.bookings[0].passengerNames", is("Arjun Hegde, Sneha Hegde")))
                .andExpect(jsonPath("$.data.bookings[0].bookingReference", is("KSRTC-998877")))
                .andExpect(jsonPath("$.data.bookings[0].status", is("DELAYED")))
                .andExpect(jsonPath("$.data.bookings[0].cost", is(1850.00)))
                .andExpect(jsonPath("$.data.bookings[0].lastKnownStatus.state", is("DELAYED")))
                .andExpect(jsonPath("$.data.bookings[0].lastKnownStatus.delayMinutes", is(45)))
                .andExpect(jsonPath("$.data.bookings[0].lastKnownStatus.source", is("LIVE")));
    }

    @Test
    @DisplayName("3. ICS calendar validity: RFC 5545 format, UTC times with Z, stable UIDs, 75-octet folding, character escaping")
    public void testIcsCalendarValidityAndEscaping() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 9, 30);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-ICS-01")
                .trip(tripA)
                .user(userA)
                .type("BUS")
                .provider("VRL Travels; Super Luxury, AC Sleeper")
                .vehicleNumber("KA-25-A-1234")
                .bookingReference("VRL\\REF\\12345,678")
                .fromLocation("Hubballi Central, Court Circle")
                .toLocation("Panaji, Kadamba Bus Stand; Platform 4")
                .startTime(start)
                .endTime(start.plusHours(5))
                .cost(new BigDecimal("900.00"))
                .status("CONFIRMED")
                .build());

        MvcResult result = mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(header().string("Content-Disposition", is("attachment; filename=\"trip-TRIP-WALLET-001.ics\"")))
                .andReturn();

        String ics = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("VERSION:2.0\r\n"));
        assertTrue(ics.contains("PRODID:-//Odyssey//Trip Wallet//EN\r\n"));
        assertTrue(ics.contains("CALSCALE:GREGORIAN\r\n"));
        assertTrue(ics.contains("METHOD:PUBLISH\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));

        // Stable UID check
        assertTrue(ics.contains("UID:TRIP-WALLET-001-BKG-ICS-01@odyssey.local"));

        // UTC timestamp check with Z suffix
        assertTrue(ics.contains("DTSTART:20261025T040000Z")); // 09:30 IST is 04:00 UTC
        assertTrue(ics.contains("DTEND:20261025T090000Z"));   // 14:30 IST is 09:00 UTC

        // Escaping check: semicolon, comma, backslash must be escaped in description/location/summary
        assertTrue(ics.contains("VRL Travels\\; Super Luxury\\, AC Sleeper"));
        assertTrue(ics.contains("Hubballi Central\\, Court Circle"));

        // Line folding check: No unfolded line must exceed 75 octets
        String[] lines = ics.split("\r\n");
        for (String line : lines) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75,
                    "Line exceeds 75 octets: " + line);
        }
    }

    @Test
    @DisplayName("4. ICS cancelled booking handling: STATUS:CANCELLED and SUMMARY:CANCELLED:")
    public void testIcsCancelledBookingMarking() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 11, 0);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-CNL-01")
                .trip(tripA)
                .user(userA)
                .type("FLIGHT")
                .provider("SpiceJet SG-101")
                .fromLocation("Bengaluru")
                .toLocation("Goa")
                .startTime(start)
                .endTime(start.plusHours(1).plusMinutes(20))
                .cost(new BigDecimal("2800.00"))
                .status("CANCELLED")
                .build());

        MvcResult result = mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        String ics = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(ics.contains("STATUS:CANCELLED"));
        assertTrue(ics.contains("SUMMARY:CANCELLED: FLIGHT - SpiceJet SG-101"));
    }

    @Test
    @DisplayName("5. Printable summary: Strict CSP (no scripts), print CSS, disclaimers, printed at note")
    public void testPrintableHtmlStrictCspAndDisclaimers() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 8, 0);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-MKT-1")
                .trip(tripA)
                .user(userA)
                .type("BUS")
                .provider("State Transport Express")
                .fromLocation("Hubballi")
                .toLocation("Panaji")
                .startTime(start)
                .endTime(start.plusHours(4))
                .cost(new BigDecimal("450.00"))
                .status("CONFIRMED")
                .departureId("DEP-MKT-999") // Triggers demo marketplace disclaimer
                .source("GENERATED")        // Triggers generated schedule disclaimer
                .build());

        MvcResult result = mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Strict CSP: Zero <script> tags and no inline event handlers
        assertFalse(html.contains("<script"), "Print HTML must not contain <script tags");
        assertFalse(html.contains("javascript:"), "Print HTML must not contain javascript: URLs");
        assertFalse(html.contains("onclick="), "Print HTML must not contain onclick handlers");
        assertFalse(html.contains("onload="), "Print HTML must not contain onload handlers");

        // Print CSS present
        assertTrue(html.contains("@media print"));

        // Required disclaimers present
        assertTrue(html.contains("Demo marketplace. No real ticket is issued and no money was charged."));
        assertTrue(html.contains("Generated schedule, not an official timetable"));

        // Header note
        assertTrue(html.contains("Printed at "));
        assertTrue(html.contains(". Status may be out of date."));

        // Separation of stored booking details vs last known status
        assertTrue(html.contains("Stored booking details"));
        assertTrue(html.contains("Last known status (may be out of date)"));
    }

    @Test
    @DisplayName("6. Offline trip pack: Self-contained, inline CSS only, no external resources, no scripts, honesty notice")
    public void testOfflineTripPackSelfContained() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 12, 0);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-PACK-1")
                .trip(tripA)
                .user(userA)
                .type("CAB")
                .provider("Coastal Rides Cab")
                .fromLocation("Panaji")
                .toLocation("Calangute")
                .startTime(start)
                .endTime(start.plusMinutes(45))
                .cost(new BigDecimal("600.00"))
                .status("CONFIRMED")
                .source("ESTIMATED") // Triggers estimated schedule disclaimer
                .build());

        MvcResult result = mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Content-Disposition", is("attachment; filename=\"trip-TRIP-WALLET-001-pack.html\"")))
                .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Zero <script> tags, zero inline event handlers
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("onclick="));
        assertFalse(html.contains("onload="));

        // No external resources (no external CDNs, fonts, or images)
        assertFalse(html.contains("<link rel=\"stylesheet\""));
        assertFalse(html.contains("https://"));
        assertFalse(html.contains("http://"));

        // Required notes
        assertTrue(html.contains("Saved at "));
        assertTrue(html.contains(". Status may be out of date."));
        assertTrue(html.contains("Self-contained offline document. This is a saved file to keep on your device, not an installed offline app."));

        // Estimated disclaimer
        assertTrue(html.contains("Estimated, please verify"));
    }

    @Test
    @DisplayName("7. XSS defense: Provider named <script>alert(1)</script> renders strictly as escaped text")
    public void testXssEscapingInPrintAndPack() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 9, 0);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-XSS-1")
                .trip(tripA)
                .user(userA)
                .type("BUS")
                .provider("<script>alert(1)</script>")
                .passengerNames("<img src=x onerror=alert('xss')>")
                .fromLocation("<b>SafeCity</b>")
                .toLocation("Panaji")
                .startTime(start)
                .endTime(start.plusHours(2))
                .cost(new BigDecimal("350.00"))
                .status("CONFIRMED")
                .build());

        // Verify Print HTML escaping
        MvcResult printRes = mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String printHtml = printRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(printHtml.contains("<script>alert(1)</script>"));
        assertTrue(printHtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(printHtml.contains("<img src=x"));
        assertTrue(printHtml.contains("&lt;img src=x onerror=alert(&#39;xss&#39;)&gt;"));

        // Verify Pack HTML escaping
        MvcResult packRes = mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String packHtml = packRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(packHtml.contains("<script>alert(1)</script>"));
        assertTrue(packHtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    @DisplayName("8. Sensitive data exclusion: No emails, phone numbers, passwords or tokens in ICS, print, or pack")
    public void testNoSensitiveFieldsExposed() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 10, 0);

        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-SAFE-1")
                .trip(tripA)
                .user(userA)
                .type("CAB")
                .provider("Private Taxi")
                .fromLocation("Panaji")
                .toLocation("Airport")
                .startTime(start)
                .endTime(start.plusHours(1))
                .cost(new BigDecimal("800.00"))
                .status("CONFIRMED")
                .build());

        // 1. ICS check
        MvcResult icsRes = mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String ics = icsRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(ics.contains("arjun@example.com"));
        assertFalse(ics.contains("9876543210"));
        assertFalse(ics.contains("Secret123!"));
        assertFalse(ics.contains("password"));

        // 2. Print HTML check
        MvcResult printRes = mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String printHtml = printRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(printHtml.contains("arjun@example.com"));
        assertFalse(printHtml.contains("9876543210"));
        assertFalse(printHtml.contains("Secret123!"));
        assertFalse(printHtml.contains("passwordHash"));

        // 3. Pack HTML check
        MvcResult packRes = mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String packHtml = packRes.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(packHtml.contains("arjun@example.com"));
        assertFalse(packHtml.contains("9876543210"));
        assertFalse(packHtml.contains("Secret123!"));
    }

    @Test
    @DisplayName("9. Ownership enforcement: User B receives 403 Forbidden across all four wallet endpoints")
    public void testOwnershipEnforcementFourEndpoints() throws Exception {
        // 1. JSON wallet
        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userB.getEmail())))
                .andExpect(status().isForbidden());

        // 2. Print HTML
        mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId())
                        .with(user(userB.getEmail())))
                .andExpect(status().isForbidden());

        // 3. Calendar ICS
        mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId())
                        .with(user(userB.getEmail())))
                .andExpect(status().isForbidden());

        // 4. Trip Pack
        mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId())
                        .with(user(userB.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Unauthenticated access: Logged out traveller receives 401 Unauthorized across all four endpoints")
    public void testUnauthenticatedAccessFourEndpoints() throws Exception {
        // 1. JSON wallet
        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId()))
                .andExpect(status().isUnauthorized());

        // 2. Print HTML
        mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId()))
                .andExpect(status().isUnauthorized());

        // 3. Calendar ICS
        mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId()))
                .andExpect(status().isUnauthorized());

        // 4. Trip Pack
        mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. Scale: Trip with 50 bookings renders cleanly and maintains sort order")
    public void testTripWithFiftyBookings() throws Exception {
        LocalDateTime baseTime = LocalDateTime.of(2026, 11, 1, 6, 0);
        List<BookingEntity> batch = new ArrayList<>();

        for (int i = 50; i >= 1; i--) { // Insert in reverse chronological order
            batch.add(BookingEntity.builder()
                    .id(String.format("BKG-50-%02d", i))
                    .trip(tripA)
                    .user(userA)
                    .type(i % 2 == 0 ? "BUS" : "CAB")
                    .provider("Transit Option " + i)
                    .fromLocation("Stop " + i)
                    .toLocation("Stop " + (i + 1))
                    .startTime(baseTime.plusHours(i))
                    .endTime(baseTime.plusHours(i).plusMinutes(45))
                    .cost(new BigDecimal("150.00"))
                    .status("CONFIRMED")
                    .build());
        }
        bookingJpaRepository.saveAll(batch);

        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings", hasSize(50)))
                .andExpect(jsonPath("$.data.bookings[0].id", is("BKG-50-01")))
                .andExpect(jsonPath("$.data.bookings[49].id", is("BKG-50-50")));
    }

    @Test
    @DisplayName("12. Edge cases: Single booking and empty itinerary trip render successfully")
    public void testTripWithSingleBookingAndEmptyTrip() throws Exception {
        // Test empty trip first
        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings", hasSize(0)));

        // Test single booking
        BookingEntity single = bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-ONE")
                .trip(tripA)
                .user(userA)
                .type("FLIGHT")
                .provider("Air India AI-505")
                .fromLocation("Bengaluru")
                .toLocation("Mangaluru")
                .startTime(LocalDateTime.of(2026, 10, 20, 8, 0))
                .endTime(LocalDateTime.of(2026, 10, 20, 9, 15))
                .cost(new BigDecimal("3200.00"))
                .status("CONFIRMED")
                .build());

        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId())
                        .with(user(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookings", hasSize(1)))
                .andExpect(jsonPath("$.data.bookings[0].id", is("BKG-ONE")));
    }

    @Test
    @DisplayName("13. Read-only guarantee: Database row counts for trips, bookings and ledger remain unchanged")
    public void testReadOnlyRowCountsUnchanged() throws Exception {
        bookingJpaRepository.save(BookingEntity.builder()
                .id("BKG-READONLY-1")
                .trip(tripA)
                .user(userA)
                .type("BUS")
                .provider("KSRTC Express")
                .fromLocation("Hubballi")
                .toLocation("Dharwad")
                .startTime(LocalDateTime.of(2026, 10, 20, 10, 0))
                .endTime(LocalDateTime.of(2026, 10, 20, 11, 0))
                .cost(new BigDecimal("120.00"))
                .status("CONFIRMED")
                .build());

        long tripsBefore = tripJpaRepository.count();
        long bookingsBefore = bookingJpaRepository.count();
        long ledgerBefore = ledgerEntryJpaRepository.count();

        // Perform requests on all four endpoints
        mockMvc.perform(get("/api/trips/{tripId}/wallet", tripA.getId()).with(user(userA.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/trips/{tripId}/wallet/print", tripA.getId()).with(user(userA.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/trips/{tripId}/wallet/calendar", tripA.getId()).with(user(userA.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/trips/{tripId}/wallet/pack", tripA.getId()).with(user(userA.getEmail()))).andExpect(status().isOk());

        long tripsAfter = tripJpaRepository.count();
        long bookingsAfter = bookingJpaRepository.count();
        long ledgerAfter = ledgerEntryJpaRepository.count();

        assertEquals(tripsBefore, tripsAfter, "Trips row count must remain unchanged");
        assertEquals(bookingsBefore, bookingsAfter, "Bookings row count must remain unchanged");
        assertEquals(ledgerBefore, ledgerAfter, "Ledger row count must remain unchanged");
    }
}
