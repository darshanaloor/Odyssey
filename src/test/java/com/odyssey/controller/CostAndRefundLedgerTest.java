package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.RecordRefundRequest;
import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.request.SelectOptionRequest;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class CostAndRefundLedgerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private AuditEntryJpaRepository auditEntryJpaRepository;

    @Autowired
    private TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity ownerUser;
    private UserEntity otherUser;
    private LocalDateTime baseDepartureTime;

    @BeforeEach
    void setUp() {
        ownerUser = userJpaRepository.findByEmail("ledger_owner@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-LEDGER-1")
                        .email("ledger_owner@example.com")
                        .passwordHash(passwordEncoder.encode("Password123"))
                        .name("Ledger Owner")
                        .createdAt(LocalDateTime.now())
                        .build()));

        otherUser = userJpaRepository.findByEmail("ledger_other@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-LEDGER-2")
                        .email("ledger_other@example.com")
                        .passwordHash(passwordEncoder.encode("Password123"))
                        .name("Other Traveller")
                        .createdAt(LocalDateTime.now())
                        .build()));

        Trip demo = tripRepository.resetDemoTrip();
        baseDepartureTime = demo.getBookings().stream()
                .filter(b -> b.getType() == BookingType.FLIGHT)
                .map(Booking::getStartTime)
                .findFirst()
                .orElse(LocalDateTime.now().plusHours(4));
    }

    private Trip createCustomTrip(UserEntity owner, boolean withCabAndHotel) {
        String tripId = IdGenerator.generate("TRIP");
        String transitId = IdGenerator.generate("BKG");
        List<Booking> bookings = new ArrayList<>();

        bookings.add(Booking.builder()
                .id(transitId)
                .type(BookingType.BUS)
                .provider("Pinnacle Intercity Bus")
                .from("BLR")
                .to("IXE")
                .startTime(baseDepartureTime)
                .endTime(baseDepartureTime.plusHours(5))
                .cost(new BigDecimal("1800.00"))
                .status(BookingStatus.CONFIRMED)
                .bookingReference("PNR-PINNACLE-101")
                .build());

        if (withCabAndHotel) {
            String cabId = IdGenerator.generate("BKG");
            String hotelId = IdGenerator.generate("BKG");

            bookings.add(Booking.builder()
                    .id(cabId)
                    .type(BookingType.CAB)
                    .provider("Coastline Cab Service")
                    .from("IXE")
                    .to("The Royal Palm Hotel")
                    .startTime(baseDepartureTime.plusHours(5).plusMinutes(15))
                    .endTime(baseDepartureTime.plusHours(6))
                    .cost(new BigDecimal("950.00"))
                    .status(BookingStatus.CONFIRMED)
                    .dependsOn(new ArrayList<>(List.of(transitId)))
                    .bookingReference("CAB-ROYAL-202")
                    .build());

            bookings.add(Booking.builder()
                    .id(hotelId)
                    .type(BookingType.HOTEL)
                    .provider("The Royal Palm Hotel")
                    .from("The Royal Palm Hotel")
                    .to("The Royal Palm Hotel")
                    .startTime(baseDepartureTime.plusHours(6).plusMinutes(15))
                    .endTime(baseDepartureTime.plusDays(1).plusHours(10))
                    .cost(new BigDecimal("5200.00"))
                    .status(BookingStatus.CONFIRMED)
                    .dependsOn(new ArrayList<>(List.of(cabId)))
                    .bookingReference("HTL-ROYAL-303")
                    .hotelContactEmail("reservations@royalpalm.test")
                    .build());
        }

        Trip trip = Trip.builder()
                .id(tripId)
                .title("Custom Ledger Test Trip")
                .userId(owner.getId())
                .travellerName(owner.getName())
                .status("PLANNED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("25000.00"))
                        .mustArriveBy(baseDepartureTime.plusHours(14))
                        .build())
                .bookings(bookings)
                .build();

        return tripRepository.save(trip);
    }

    private JsonNode disruptRecoverAndApprove(Trip trip, String bookingIdToCancel) throws Exception {
        ReportDisruptionRequest disruptionReq = ReportDisruptionRequest.builder()
                .type(DisruptionType.CANCELLATION)
                .bookingId(bookingIdToCancel)
                .reason("Carrier operational cancellation")
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/disruptions")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isOk());

        MvcResult recoverRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/recover")
                        .with(user(ownerUser.getEmail())).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode options = objectMapper.readTree(recoverRes.getResponse().getContentAsString())
                .path("data").path("options");
        assertTrue(options.isArray() && options.size() > 0);
        String optionId = options.get(0).path("id").asText();

        SelectOptionRequest selectReq = SelectOptionRequest.builder().optionId(optionId).build();
        MvcResult approveRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/approve")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectReq)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(approveRes.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("Bus-only trip: ledger totals equal plan's net cost, correct basis labelling (RECORDED, ESTIMATED)")
    void testBusOnlyTripLedgerTotalEqualsPlanNetCost() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);
        String busId = trip.getBookings().get(0).getId();
        JsonNode approvedPlan = disruptRecoverAndApprove(trip, busId);

        BigDecimal planNetCost = new BigDecimal(approvedPlan.path("costBreakdown").path("netTripCost").asText())
                .setScale(2, RoundingMode.HALF_UP);

        MvcResult ledgerRes = mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn();

        JsonNode ledgerData = objectMapper.readTree(ledgerRes.getResponse().getContentAsString()).path("data");
        BigDecimal ledgerNetTotal = new BigDecimal(ledgerData.path("netTotal").asText()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal summaryNetTotal = new BigDecimal(ledgerData.path("summary").path("netTotal").asText()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal summaryLedgerTotal = new BigDecimal(ledgerData.path("summary").path("ledgerTotal").asText()).setScale(2, RoundingMode.HALF_UP);

        // Ledger total equals the plan's net cost
        assertEquals(planNetCost, ledgerNetTotal);
        assertEquals(planNetCost, summaryNetTotal);
        assertEquals(planNetCost, summaryLedgerTotal);

        // In a bus-only trip, original preserved bookings cost is 0
        BigDecimal originalCost = new BigDecimal(ledgerData.path("summary").path("originalCost").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.00"), originalCost);

        // Expected refund is 1800.00 (the cancelled bus)
        BigDecimal expectedRefunds = new BigDecimal(ledgerData.path("summary").path("expectedRefunds").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1800.00"), expectedRefunds);

        BigDecimal outstandingRefunds = new BigDecimal(ledgerData.path("summary").path("outstandingRefunds").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1800.00"), outstandingRefunds);

        BigDecimal recordedRefunds = new BigDecimal(ledgerData.path("summary").path("recordedRefunds").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.00"), recordedRefunds);

        // Basis labelling
        JsonNode entries = ledgerData.path("entries");
        assertTrue(entries.size() >= 2);

        boolean foundRecordedReplacement = false;
        boolean foundEstimatedRefund = false;
        for (JsonNode e : entries) {
            String type = e.path("type").asText();
            String basis = e.path("basis").asText();
            if ("REPLACEMENT_COST".equals(type) && ("RECORDED".equals(basis) || "DEMO".equals(basis))) {
                foundRecordedReplacement = true;
            }
            if ("REFUND_EXPECTED".equals(type) && "ESTIMATED".equals(basis)) {
                foundEstimatedRefund = true;
            }
        }
        assertTrue(foundRecordedReplacement, "Should contain REPLACEMENT_COST with basis RECORDED");
        assertTrue(foundEstimatedRefund, "Should contain REFUND_EXPECTED with basis ESTIMATED");
    }

    @Test
    @DisplayName("Bus+Cab+Hotel trip: ledger totals equal plan's net cost and preserves hotel original cost")
    void testBusCabHotelTripLedgerTotalEqualsPlanNetCost() throws Exception {
        Trip trip = createCustomTrip(ownerUser, true);
        String busId = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.BUS)
                .findFirst().orElseThrow().getId();

        JsonNode approvedPlan = disruptRecoverAndApprove(trip, busId);

        BigDecimal planNetCost = new BigDecimal(approvedPlan.path("costBreakdown").path("netTripCost").asText())
                .setScale(2, RoundingMode.HALF_UP);

        MvcResult ledgerRes = mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ledgerData = objectMapper.readTree(ledgerRes.getResponse().getContentAsString()).path("data");
        BigDecimal ledgerNetTotal = new BigDecimal(ledgerData.path("netTotal").asText()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal summaryNetTotal = new BigDecimal(ledgerData.path("summary").path("netTotal").asText()).setScale(2, RoundingMode.HALF_UP);

        // Reconciliation
        assertEquals(planNetCost, ledgerNetTotal);
        assertEquals(planNetCost, summaryNetTotal);

        // Hotel is preserved original cost: 5200.00
        BigDecimal originalCost = new BigDecimal(ledgerData.path("summary").path("originalCost").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("5200.00"), originalCost);

        // Expected refunds include bus (1800) + dependent cab (950) = 2750.00
        BigDecimal expectedRefunds = new BigDecimal(ledgerData.path("summary").path("expectedRefunds").asText()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("2750.00"), expectedRefunds);

        // Check basis chips: Hotel has RECORDED, Cab extra has ESTIMATED, Refunds have ESTIMATED
        JsonNode entries = ledgerData.path("entries");
        boolean foundOriginalRecorded = false;
        boolean foundExtraCost = false;
        for (JsonNode e : entries) {
            String type = e.path("type").asText();
            String basis = e.path("basis").asText();
            if ("ORIGINAL_COST".equals(type) && "RECORDED".equals(basis)) {
                foundOriginalRecorded = true;
            }
            if ("EXTRA_COST".equals(type) && "ESTIMATED".equals(basis)) {
                foundExtraCost = true;
            }
        }
        assertTrue(foundOriginalRecorded, "Should contain ORIGINAL_COST with basis RECORDED");
        assertTrue(foundExtraCost, "Should contain EXTRA_COST with basis ESTIMATED");
    }

    @Test
    @DisplayName("DEMO_REFUNDED booking appears with basis DEMO in the ledger")
    void testDemoRefundedAppearsAsDemoBasis() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);
        String busId = trip.getBookings().get(0).getId();
        disruptRecoverAndApprove(trip, busId);

        // Update booking entity payment_status to DEMO_REFUNDED (as set by MarketplaceService replacement booking)
        BookingEntity busEntity = bookingJpaRepository.findById(busId).orElseThrow();
        busEntity.setStatus("CANCELLED");
        busEntity.setPaymentStatus("DEMO_REFUNDED");
        bookingJpaRepository.save(busEntity);

        // Update trip model
        trip = tripRepository.findById(trip.getId()).orElseThrow();

        MvcResult ledgerRes = mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode entries = objectMapper.readTree(ledgerRes.getResponse().getContentAsString())
                .path("data").path("entries");

        boolean foundDemoBasis = false;
        for (JsonNode e : entries) {
            if ("REFUND_EXPECTED".equals(e.path("type").asText()) && "DEMO".equals(e.path("basis").asText())) {
                foundDemoBasis = true;
                break;
            }
        }
        assertTrue(foundDemoBasis, "DEMO_REFUNDED booking should appear with basis DEMO in ledger");
    }

    @Test
    @DisplayName("Refund validation: zero, negative, exceeding booking cost, future date, invalid booking")
    void testRefundValidation() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);
        String busId = trip.getBookings().get(0).getId();
        disruptRecoverAndApprove(trip, busId);

        // 1. Zero amount -> 400
        RecordRefundRequest zeroReq = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(BigDecimal.ZERO)
                .receivedOn(LocalDate.now())
                .build();
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroReq)))
                .andExpect(status().isBadRequest());

        // 2. Negative amount -> 400
        RecordRefundRequest negReq = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("-100.00"))
                .receivedOn(LocalDate.now())
                .build();
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negReq)))
                .andExpect(status().isBadRequest());

        // 3. Exceeding booking cost (cost is 1800, requesting 2500) -> 400
        RecordRefundRequest exceedReq = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("2500.00"))
                .receivedOn(LocalDate.now())
                .build();
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot exceed")));

        // 4. Future date -> 400
        RecordRefundRequest futureReq = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("500.00"))
                .receivedOn(LocalDate.now().plusDays(3))
                .build();
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(futureReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("future")));

        // 5. Unknown booking ID -> 404 / 400
        RecordRefundRequest unknownReq = RecordRefundRequest.builder()
                .bookingId("BKG-NONEXISTENT")
                .amount(new BigDecimal("500.00"))
                .receivedOn(LocalDate.now())
                .build();
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Append-only behaviour: recording multiple refunds appends new entries, reduces outstanding, writes USER audit")
    void testAppendOnlyBehaviour() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);
        String busId = trip.getBookings().get(0).getId();
        disruptRecoverAndApprove(trip, busId);

        // Record partial refund 1: Rs 600
        RecordRefundRequest ref1 = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("600.00"))
                .reference("UTR-BANK-1100")
                .receivedOn(LocalDate.now())
                .note("First installment from bus operator")
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.recordedRefunds", is(600.0)))
                .andExpect(jsonPath("$.data.summary.outstandingRefunds", is(1200.0)));

        // Record partial refund 2: Rs 400 (append-only)
        RecordRefundRequest ref2 = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("400.00"))
                .reference("UTR-BANK-1200")
                .receivedOn(LocalDate.now())
                .note("Second installment from bus operator")
                .build();

        MvcResult secondRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.recordedRefunds", is(1000.0)))
                .andExpect(jsonPath("$.data.summary.outstandingRefunds", is(800.0)))
                .andReturn();

        JsonNode entries = objectMapper.readTree(secondRes.getResponse().getContentAsString()).path("data").path("entries");

        List<JsonNode> recordedEntries = new ArrayList<>();
        for (JsonNode e : entries) {
            if ("REFUND_RECORDED".equals(e.path("type").asText())) {
                recordedEntries.add(e);
                assertEquals("USER_REPORTED", e.path("basis").asText());
            }
        }
        assertEquals(2, recordedEntries.size(), "Both refund entries must be retained append-only");

        // Verify USER audit entry was written
        List<AuditEntryEntity> audits = auditEntryJpaRepository.findByTripIdOrderByTimestampDesc(trip.getId());
        long userRefundAudits = audits.stream()
                .filter(a -> "USER".equals(a.getActor()) && "REFUND_RECORDED".equals(a.getAction()))
                .count();
        assertTrue(userRefundAudits >= 2, "Expected audit entries for both recorded refunds");
    }

    @Test
    @DisplayName("CSV export: returns text/csv, contains basis column, no secrets or credentials")
    void testCsvExportContentAndNoSecrets() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);
        String busId = trip.getBookings().get(0).getId();
        disruptRecoverAndApprove(trip, busId);

        // Record a refund so USER_REPORTED is present
        RecordRefundRequest req = RecordRefundRequest.builder()
                .bookingId(busId)
                .amount(new BigDecimal("500.00"))
                .reference("REF-CSV-CHECK")
                .receivedOn(LocalDate.now())
                .note("Verified in CSV export")
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        MvcResult csvRes = mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger/export")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"ledger-")))
                .andReturn();

        String csv = csvRes.getResponse().getContentAsString();
        assertTrue(csv.startsWith("ID,Trip ID,Booking ID,Type,Amount (INR),Basis,Note,Reference,Received On,Created At"));
        assertTrue(csv.contains("Basis"));
        assertTrue(csv.contains("RECORDED"));
        assertTrue(csv.contains("ESTIMATED"));
        assertTrue(csv.contains("USER_REPORTED"));
        assertTrue(csv.contains("REF-CSV-CHECK"));

        // No secrets check
        assertFalse(csv.toLowerCase().contains("password"));
        assertFalse(csv.toLowerCase().contains("secret"));
        assertFalse(csv.toLowerCase().contains("hash"));
        assertFalse(csv.toLowerCase().contains("apikey"));
        assertFalse(csv.toLowerCase().contains("cardnumber"));
    }

    @Test
    @DisplayName("Ownership and Security: 401 unauthenticated, 403 other user, 403 missing CSRF, 404 missing trip")
    void testOwnershipAndSecurity() throws Exception {
        Trip trip = createCustomTrip(ownerUser, false);

        // 1. 401 Unauthenticated
        mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 2. 403 Other user
        mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger")
                        .with(user(otherUser.getEmail())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(otherUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + trip.getBookings().get(0).getId() + "\",\"amount\":100}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger/export")
                        .with(user(otherUser.getEmail())))
                .andExpect(status().isForbidden());

        // 3. 403 Missing CSRF on authenticated POST
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/ledger/refunds")
                        .with(user(ownerUser.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // 4. 404 Missing trip
        mockMvc.perform(get("/api/trips/TRIP-DOES-NOT-EXIST/ledger")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Rounding: exactly two decimal places, no float precision anomalies")
    void testRoundingTwoDecimals() throws Exception {
        Trip trip = createCustomTrip(ownerUser, true);
        String busId = trip.getBookings().get(0).getId();
        disruptRecoverAndApprove(trip, busId);

        MvcResult res = mockMvc.perform(get("/api/trips/" + trip.getId() + "/ledger")
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        String rawJson = res.getResponse().getContentAsString();
        assertTrue(rawJson.contains("\"originalCost\":5200.00"), "originalCost should be 5200.00 in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"replacementCost\":5800.00") || rawJson.contains("\"replacementCost\":6200.00") || rawJson.contains("\"replacementCost\":1450.00"), "replacementCost should have two decimals (.00) in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"estimatedExtraCost\":950.00"), "estimatedExtraCost should be 950.00 in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"expectedRefunds\":2750.00"), "expectedRefunds should be 2750.00 in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"outstandingRefunds\":2750.00"), "outstandingRefunds should be 2750.00 in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"recordedRefunds\":0.00"), "recordedRefunds should be 0.00 in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"netTotal\":9200.00") || rawJson.contains("\"netTotal\":9600.00") || rawJson.contains("\"netTotal\":4850.00"), "netTotal should have two decimals (.00) in JSON: " + rawJson);
        assertTrue(rawJson.contains("\"ledgerTotal\":9200.00") || rawJson.contains("\"ledgerTotal\":9600.00") || rawJson.contains("\"ledgerTotal\":4850.00"), "ledgerTotal should have two decimals (.00) in JSON: " + rawJson);

        assertTrue(rawJson.contains("\"amount\":5800.00") || rawJson.contains("\"amount\":6200.00") || rawJson.contains("\"amount\":1450.00"), "replacement amount should have two decimals (.00): " + rawJson);
        assertTrue(rawJson.contains("\"amount\":950.00"), "amount should be 950.00: " + rawJson);
        assertTrue(rawJson.contains("\"amount\":5200.00"), "amount should be 5200.00: " + rawJson);
        assertTrue(rawJson.contains("\"amount\":1800.00"), "amount should be 1800.00: " + rawJson);
        JsonNode data = objectMapper.readTree(rawJson).path("data");
        for (JsonNode e : data.path("entries")) {
            double amt = e.path("amount").asDouble();
            assertTrue(amt > 0, "Amount must be positive: " + amt);
            assertEquals(amt, Math.round(amt * 100.0) / 100.0, 0.0001,
                    "Amount must not have float precision anomalies: " + amt);
        }
    }
}

