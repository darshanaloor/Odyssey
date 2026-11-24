package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.RefundProperties;
import com.odyssey.dto.request.RecordRefundRequest;
import com.odyssey.dto.request.RefundDeductionRequest;
import com.odyssey.dto.request.ShareRefundRequest;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.TripLedgerEntryEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.CostBreakdown;
import com.odyssey.model.CostBreakdownLine;
import com.odyssey.model.RecoveryPlan;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.*;
import com.odyssey.notification.EmailChannel;
import com.odyssey.notification.SendResult;
import com.odyssey.notification.SendStatus;
import com.odyssey.notification.SmsChannel;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RefundDeductionsAndSharingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripJpaRepository tripJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private AuditEntryJpaRepository auditEntryJpaRepository;

    @Autowired
    private TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefundProperties refundProperties;

    @MockBean
    private EmailChannel mockEmailChannel;

    @MockBean
    private SmsChannel mockSmsChannel;

    private UserEntity ownerUser;
    private UserEntity otherUser;
    private LocalDateTime baseDepartureTime;

    @BeforeEach
    void setUp() {
        ownerUser = userJpaRepository.findByEmail("deduction_owner@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-DEDUCT-1")
                        .email("deduction_owner@example.com")
                        .passwordHash(passwordEncoder.encode("Password123"))
                        .name("Deduction Owner")
                        .createdAt(LocalDateTime.now())
                        .build()));

        otherUser = userJpaRepository.findByEmail("deduction_other@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-DEDUCT-2")
                        .email("deduction_other@example.com")
                        .passwordHash(passwordEncoder.encode("Password123"))
                        .name("Other User")
                        .createdAt(LocalDateTime.now())
                        .build()));

        baseDepartureTime = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);

        // Default mock behaviors
        when(mockEmailChannel.getChannelType()).thenReturn("EMAIL");
        when(mockEmailChannel.isActive()).thenReturn(true);
        when(mockEmailChannel.send(any())).thenReturn(SendResult.builder()
                .status(SendStatus.SENT)
                .providerMessageId("MSG-EML-TEST-1")
                .build());

        when(mockSmsChannel.getChannelType()).thenReturn("SMS");
        when(mockSmsChannel.isActive()).thenReturn(true);
        when(mockSmsChannel.send(any())).thenReturn(SendResult.builder()
                .status(SendStatus.SENT)
                .providerMessageId("MSG-SMS-TEST-1")
                .build());
    }

    private Trip createTestTripWithBooking(String ownerId, double bookingCostVal, boolean isMarketplace) {
        String tripId = IdGenerator.generate("TRIP");
        String bookingId = IdGenerator.generate("BKG");
        BigDecimal cost = BigDecimal.valueOf(bookingCostVal).setScale(2, RoundingMode.HALF_UP);

        Booking booking = Booking.builder()
                .id(bookingId)
                .type(BookingType.FLIGHT)
                .provider("IndiGo")
                .from("DEL")
                .to("BOM")
                .startTime(baseDepartureTime)
                .endTime(baseDepartureTime.plusHours(2))
                .status(BookingStatus.CONFIRMED)
                .cost(cost)
                .bookingReference("INDIGO-REF-1")
                .source(isMarketplace ? "MARKETPLACE" : "DIRECT")
                .paymentStatus(isMarketplace ? "DEMO_CONFIRMED" : "CONFIRMED")
                .build();

        Trip trip = Trip.builder()
                .id(tripId)
                .title("Trip with Deductions " + tripId)
                .travellerName("Test Traveller")
                .travellerEmail("traveller@example.com")
                .travellerPhone("+919876543210")
                .status("DISRUPTED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(baseDepartureTime.plusHours(12))
                        .build())
                .userId(ownerId)
                .bookings(new ArrayList<>(List.of(booking)))
                .build();

        trip = tripRepository.save(trip);

        // Also persist BookingEntity
        TripEntity tripEntity = tripJpaRepository.findById(tripId).orElse(null);
        if (tripEntity != null) {
            BookingEntity bEntity = BookingEntity.builder()
                    .id(bookingId)
                    .trip(tripEntity)
                    .type(BookingType.FLIGHT.name())
                    .provider("IndiGo")
                    .fromLocation("DEL")
                    .toLocation("BOM")
                    .startTime(baseDepartureTime)
                    .endTime(baseDepartureTime.plusHours(2))
                    .status(BookingStatus.CONFIRMED.name())
                    .cost(cost)
                    .bookingReference("INDIGO-REF-1")
                    .source(isMarketplace ? "MARKETPLACE" : "DIRECT")
                    .paymentStatus(isMarketplace ? "DEMO_CONFIRMED" : "CONFIRMED")
                    .build();
            bookingJpaRepository.save(bEntity);
        }

        return trip;
    }

    @Test
    @DisplayName("Net refund arithmetic: gross minus deductions, two decimal places, append-only entries created")
    void testNetRefundArithmeticAndDeductionBreakdown() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 2500.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest request = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .status(RefundStatus.RECEIVED)
                .reference("BANK-UTR-9988")
                .receivedOn(LocalDate.now())
                .note("Refund processed with standard penalties")
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(350.50))
                                .description("Airline cancellation charge per fare rules")
                                .build(),
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CONVENIENCE_FEE)
                                .amount(BigDecimal.valueOf(50.25))
                                .description("Non-refundable portal booking convenience fee")
                                .build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.recordedRefunds").value(1599.25)) // 2000 - 350.50 - 50.25 = 1599.25
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");

        // Verify entries
        JsonNode entries = data.get("entries");
        assertTrue(entries.isArray());
        assertEquals(3, entries.size(), "Should have 1 REFUND entry + 2 REFUND_DEDUCTION entries");

        JsonNode refundEntry = null;
        List<JsonNode> deductionEntries = new ArrayList<>();
        for (JsonNode e : entries) {
            String type = e.get("type").asText();
            if ("REFUND".equals(type) || "REFUND_RECORDED".equals(type)) {
                refundEntry = e;
            } else if ("REFUND_DEDUCTION".equals(type)) {
                deductionEntries.add(e);
            }
        }

        assertNotNull(refundEntry, "Must have parent REFUND entry");
        assertEquals(2000.00, refundEntry.get("amount").asDouble(), 0.001);
        assertEquals("USER_REPORTED", refundEntry.get("basis").asText());
        assertEquals("RECEIVED", refundEntry.get("refundStatus").asText());
        assertEquals("User-reported, not verified", refundEntry.get("statusLabel").asText());

        String parentId = refundEntry.get("id").asText();
        assertEquals(2, deductionEntries.size());
        for (JsonNode d : deductionEntries) {
            assertEquals(parentId, d.get("parentEntryId").asText());
            assertEquals("USER_REPORTED", d.get("basis").asText());
            assertNotNull(d.get("category").asText());
            assertNotNull(d.get("description").asText());
        }

        // Verify booking refunds summary breakdown
        JsonNode bookingRefunds = data.get("bookingRefunds");
        assertNotNull(bookingRefunds);
        assertEquals(1, bookingRefunds.size());
        JsonNode bSummary = bookingRefunds.get(0);
        assertEquals(bookingId, bSummary.get("bookingId").asText());
        assertEquals(2500.00, bSummary.get("originalCost").asDouble(), 0.001);
        assertEquals(2000.00, bSummary.get("grossRefund").asDouble(), 0.001);
        assertEquals(1599.25, bSummary.get("netRefund").asDouble(), 0.001);
        assertEquals("RECEIVED", bSummary.get("status").asText());
        assertEquals(0.00, bSummary.get("outstandingAmount").asDouble(), 0.001); // RECEIVED status outstanding = 0
        assertEquals(400.75, bSummary.get("reduction").asDouble(), 0.001); // 350.50 + 50.25 = 400.75
        assertEquals(2, bSummary.get("deductions").size());
    }

    @Test
    @DisplayName("Validation: zero or negative deduction, missing description, <5 or >200 chars, deductions > gross, net < 0, gross > booking cost, future date")
    void testValidationFailures() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 1000.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        // 1. Gross > booking cost
        RecordRefundRequest reqExcessGross = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1500.00))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqExcessGross)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot exceed booking cost")));

        // 2. Deduction with zero or negative amount
        RecordRefundRequest reqZeroDeduction = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.ZERO)
                                .description("Valid description text")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqZeroDeduction)))
                .andExpect(status().isBadRequest());

        // 3. Deduction description too short (< 5 chars)
        RecordRefundRequest reqShortDesc = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.OTHER)
                                .amount(BigDecimal.valueOf(50.00))
                                .description("bad")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqShortDesc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Request validation failed")));

        // 4. Deduction description too long (> 200 chars)
        String longDesc = "A".repeat(205);
        RecordRefundRequest reqLongDesc = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.OTHER)
                                .amount(BigDecimal.valueOf(50.00))
                                .description(longDesc)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLongDesc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Request validation failed")));

        // 5. Total deductions > gross
        RecordRefundRequest reqDeductionsExceed = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(500.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(600.00))
                                .description("Exceeding charge")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDeductionsExceed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot exceed gross refund")));

        // 6. Future received date
        RecordRefundRequest reqFutureDate = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1000.00))
                .receivedOn(LocalDate.now().plusDays(2))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqFutureDate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be in the future")));

        // 7. Booking not belonging to trip
        RecordRefundRequest reqWrongBooking = RecordRefundRequest.builder()
                .bookingId("BK-NONEXISTENT")
                .grossAmount(BigDecimal.valueOf(100.00))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqWrongBooking)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Booking not found on trip")));
    }

    @Test
    @DisplayName("Reduced refund must be explained: if net is less than booking cost, at least one deduction with description required")
    void testReducedRefundExplanationEnforcement() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        // Net 1500.00 is less than booking cost 2000.00, but no deduction provided -> 400 Bad Request
        RecordRefundRequest reqUnexplained = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1500.00))
                .deductions(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnexplained)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reduced refund must be explained")));

        // Full refund equal to booking cost without deduction -> 200 OK
        RecordRefundRequest reqFullRefund = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .deductions(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqFullRefund)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Reduced refund with deduction explaining reduction on a separate trip -> 200 OK
        Trip trip2 = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bookingId2 = trip2.getBookings().get(0).getId();

        RecordRefundRequest reqExplained = RecordRefundRequest.builder()
                .bookingId(bookingId2)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(500.00))
                                .description("25% cancellation penalty applied")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip2.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqExplained)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Append-only persistence: subsequent refunds on same booking add new rows, not overwriting")
    void testAppendOnlyPersistence() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 3000.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        // First refund: gross 1000, net 900 (explain reduction via deduction)
        RecordRefundRequest ref1 = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.PARTIAL_REFUND)
                                .amount(BigDecimal.valueOf(100.00))
                                .description("First instalment partial deduction")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref1)))
                .andExpect(status().isOk());

        // Second refund: gross 1500, deduction 200
        RecordRefundRequest ref2 = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1500.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.PENALTY)
                                .amount(BigDecimal.valueOf(200.00))
                                .description("Late cancellation penalty")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ref2)))
                .andExpect(status().isOk());

        // Query database directly
        List<TripLedgerEntryEntity> entries = tripLedgerEntryJpaRepository.findByTripIdOrderByCreatedAtAsc(trip.getId());
        long refundCount = entries.stream().filter(e -> e.getType() == LedgerEntryType.REFUND || e.getType() == LedgerEntryType.REFUND_RECORDED).count();
        long deductionCount = entries.stream().filter(e -> e.getType() == LedgerEntryType.REFUND_DEDUCTION).count();

        assertEquals(2, refundCount, "Subsequent refunds must append new rows");
        assertEquals(2, deductionCount, "Each deduction must append new row");
    }

    @Test
    @DisplayName("Timing note: read from config, switches to demo note for marketplace bookings")
    void testRefundTimingNotesConfigAndDemoSwitch() throws Exception {
        // Assert RefundProperties loaded from application.yml
        assertEquals(5, refundProperties.getTypicalDaysMin());
        assertEquals(7, refundProperties.getTypicalDaysMax());
        assertNotNull(refundProperties.getNote());
        assertTrue(refundProperties.getNote().contains("Refunds are not instant"));
        assertNotNull(refundProperties.getDemoNote());
        assertTrue(refundProperties.getDemoNote().contains("Demo refund. No real money is moved"));

        // Test Real Booking Trip
        Trip realTrip = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        MvcResult resReal = mockMvc.perform(get("/api/trips/{tripId}/ledger", realTrip.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode realData = objectMapper.readTree(resReal.getResponse().getContentAsString()).get("data");
        String realNote = realData.get("timingNote").asText();
        assertFalse(realData.get("isDemo").asBoolean());
        assertTrue(realNote.contains("Refunds are not instant"));
        assertTrue(realNote.contains("typically 5 to 7"));

        // Test Demo Marketplace Trip
        Trip demoTrip = createTestTripWithBooking(ownerUser.getId(), 2000.00, true);
        MvcResult resDemo = mockMvc.perform(get("/api/trips/{tripId}/ledger", demoTrip.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode demoData = objectMapper.readTree(resDemo.getResponse().getContentAsString()).get("data");
        String demoNote = demoData.get("timingNote").asText();
        assertTrue(demoData.get("isDemo").asBoolean());
        assertTrue(demoNote.contains("Demo refund. No real money is moved"));
        assertTrue(demoNote.contains("Demo marketplace"));
    }

    @Test
    @DisplayName("Share endpoint: ownership, CSRF, confirm validation, invalid recipient")
    void testShareRefundEndpointGuardsAndValidations() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 1800.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        // Record a refund to get an entry id
        RecordRefundRequest refReq = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1800.00))
                .deductions(Collections.emptyList())
                .build();

        MvcResult recResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode recData = objectMapper.readTree(recResult.getResponse().getContentAsString()).get("data");
        String entryId = recData.get("entries").get(0).get("id").asText();

        // 1. Unauthenticated -> 401
        ShareRefundRequest shareReq = ShareRefundRequest.builder()
                .channel(ShareChannel.EMAIL)
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isUnauthorized());

        // 2. Other user -> 403 or 404
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(otherUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isForbidden());

        // 3. Missing CSRF -> 403
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isForbidden());

        // 4. confirm = false -> 400 Bad Request
        ShareRefundRequest reqUnconfirmed = ShareRefundRequest.builder()
                .channel(ShareChannel.EMAIL)
                .confirm(false)
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnconfirmed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("confirm must be true")));

        // 5. Invalid email recipient -> 400 Bad Request
        ShareRefundRequest reqInvalidEmail = ShareRefundRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("not-an-email")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalidEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid email")));
    }

    @Test
    @DisplayName("Share dispatch: EmailChannel, SmsChannel <= 320 chars, plain text template with operator disclaimer and masked recipient")
    void testShareDispatchEmailAndSms() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 2200.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest refReq = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(200.00))
                                .description("Operator cancellation fee")
                                .build()
                ))
                .build();

        MvcResult recResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode recData = objectMapper.readTree(recResult.getResponse().getContentAsString()).get("data");
        String entryId = recData.get("entries").get(0).get("id").asText();

        // 1. Share via EMAIL
        ShareRefundRequest shareEmail = ShareRefundRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("testrecipient@example.com")
                .confirm(true)
                .build();

        MvcResult emailResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andReturn();

        JsonNode emailData = objectMapper.readTree(emailResult.getResponse().getContentAsString()).get("data");
        String emailContent = emailData.get("content").asText();
        assertTrue(emailContent.contains("Amounts are user-reported and not verified by the operator."));
        assertTrue(emailContent.contains("Operator cancellation fee"));
        assertTrue(emailContent.contains("Refunds are not instant. They usually take a few working days"));

        // Check NotificationEntity persisted
        List<NotificationEntity> notifs = notificationJpaRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertFalse(notifs.isEmpty());
        NotificationEntity notif = notifs.get(0);
        assertEquals("REFUND_SUMMARY", notif.getEventType());
        assertEquals("EMAIL", notif.getChannel());
        assertTrue(notif.getRecipientMasked().contains("***@example.com"));

        // Check AuditEntryEntity
        List<AuditEntryEntity> audits = auditEntryJpaRepository.findByTripIdOrderByTimestampAsc(trip.getId());
        boolean hasShareAudit = audits.stream().anyMatch(a -> "REFUND_SHARED".equals(a.getAction()));
        assertTrue(hasShareAudit, "Audit entry must record REFUND_SHARED");

        // 2. Share via SMS (assert content <= 320 chars)
        ShareRefundRequest shareSms = ShareRefundRequest.builder()
                .channel(ShareChannel.SMS)
                .recipient("9876543210")
                .confirm(true)
                .build();

        MvcResult smsResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareSms)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("SMS"))
                .andReturn();

        JsonNode smsData = objectMapper.readTree(smsResult.getResponse().getContentAsString()).get("data");
        String smsContent = smsData.get("content").asText();
        assertTrue(smsContent.length() <= 320, "SMS content must be <= 320 characters, but was " + smsContent.length());
        assertTrue(smsContent.contains("User-reported, not verified"));

        // 3. Share via MAILTO
        ShareRefundRequest shareMailto = ShareRefundRequest.builder()
                .channel(ShareChannel.MAILTO)
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareMailto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("MAILTO"))
                .andExpect(jsonPath("$.data.status").value("LINK_CREATED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"))
                .andExpect(jsonPath("$.data.mailtoUrl", startsWith("mailto:")));

        // 4. Share via COPY
        ShareRefundRequest shareCopy = ShareRefundRequest.builder()
                .channel(ShareChannel.COPY)
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareCopy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("COPY"))
                .andExpect(jsonPath("$.data.status").value("COPIED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"))
                .andExpect(jsonPath("$.data.content", not(emptyString())));
    }

    @Test
    @DisplayName("Rate limiting: 10 shares per hour succeed, 11th returns 429 Too Many Requests")
    void testShareRateLimiting() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 1500.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest refReq = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1500.00))
                .deductions(Collections.emptyList())
                .build();

        MvcResult recResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode recData = objectMapper.readTree(recResult.getResponse().getContentAsString()).get("data");
        String entryId = recData.get("entries").get(0).get("id").asText();

        ShareRefundRequest shareReq = ShareRefundRequest.builder()
                .channel(ShareChannel.COPY)
                .confirm(true)
                .build();

        // Execute 10 shares successfully
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                            .with(user(ownerUser.getEmail()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shareReq)))
                    .andExpect(status().isOk());
        }

        // 11th share in the same hour must return 429
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Rate limit reached")));
    }

    @Test
    @DisplayName("Failing channel does not break ledger or trip")
    void testChannelFailureDoesNotBreakLedger() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 1200.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest refReq = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1200.00))
                .deductions(Collections.emptyList())
                .build();

        MvcResult recResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode recData = objectMapper.readTree(recResult.getResponse().getContentAsString()).get("data");
        String entryId = recData.get("entries").get(0).get("id").asText();

        // Configure mock channel to throw or fail
        when(mockEmailChannel.send(any())).thenReturn(SendResult.builder()
                .status(SendStatus.FAILED)
                .error("SMTP connection timed out")
                .build());

        ShareRefundRequest shareReq = ShareRefundRequest.builder()
                .channel(ShareChannel.EMAIL)
                .confirm(true)
                .build();

        // Share should return 200 with FAILED status (graceful handling, not 500 error)
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        // Ledger is still queryable and completely intact
        mockMvc.perform(get("/api/trips/{tripId}/ledger", trip.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.recordedRefunds").value(1200.00));
    }

    @Test
    @DisplayName("Outstanding refund definitions: RECEIVED outstanding=0; EXPECTED/REQUESTED outstanding=expected net; reduction shown separately")
    void testOutstandingRefundCalculationAndReductionDisplay() throws Exception {
        // 1. RECEIVED with deductions: outstanding = 0, reduction = 300
        Trip trip1 = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bkg1 = trip1.getBookings().get(0).getId();
        RecordRefundRequest req1 = RecordRefundRequest.builder()
                .bookingId(bkg1)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .status(RefundStatus.RECEIVED)
                .deductions(List.of(RefundDeductionRequest.builder()
                        .category(DeductionCategory.CANCELLATION_CHARGE)
                        .amount(BigDecimal.valueOf(300.00))
                        .description("Cancellation charge applied")
                        .build()))
                .build();
        MvcResult res1 = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip1.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data");
        JsonNode bSummary1 = data1.get("bookingRefunds").get(0);
        assertEquals(0.00, bSummary1.get("outstandingAmount").asDouble(), 0.001, "RECEIVED status must have outstanding = 0");
        assertEquals(300.00, bSummary1.get("reduction").asDouble(), 0.001, "Reduction must reflect deductions");
        assertEquals(0.00, data1.get("summary").get("outstandingRefunds").asDouble(), 0.001);

        // 2. RECEIVED without deductions: outstanding = 0, reduction = 0
        Trip trip2 = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bkg2 = trip2.getBookings().get(0).getId();
        RecordRefundRequest req2 = RecordRefundRequest.builder()
                .bookingId(bkg2)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .status(RefundStatus.RECEIVED)
                .deductions(Collections.emptyList())
                .build();
        MvcResult res2 = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip2.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("data");
        JsonNode bSummary2 = data2.get("bookingRefunds").get(0);
        assertEquals(0.00, bSummary2.get("outstandingAmount").asDouble(), 0.001);
        assertEquals(0.00, bSummary2.get("reduction").asDouble(), 0.001);

        // 3. REQUESTED with deductions: outstanding = expected net (2000 - 400 = 1600), reduction = 400
        Trip trip3 = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bkg3 = trip3.getBookings().get(0).getId();
        RecordRefundRequest req3 = RecordRefundRequest.builder()
                .bookingId(bkg3)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .status(RefundStatus.REQUESTED)
                .deductions(List.of(RefundDeductionRequest.builder()
                        .category(DeductionCategory.PENALTY)
                        .amount(BigDecimal.valueOf(400.00))
                        .description("Late modification fee")
                        .build()))
                .build();
        MvcResult res3 = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip3.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data3 = objectMapper.readTree(res3.getResponse().getContentAsString()).get("data");
        JsonNode bSummary3 = data3.get("bookingRefunds").get(0);
        assertEquals(1600.00, bSummary3.get("outstandingAmount").asDouble(), 0.001, "REQUESTED with deductions outstanding must equal expected gross minus deductions");
        assertEquals(400.00, bSummary3.get("reduction").asDouble(), 0.001);
        assertEquals(1600.00, data3.get("summary").get("outstandingRefunds").asDouble(), 0.001);

        // 4. REQUESTED without deductions: outstanding = 2000, reduction = 0
        Trip trip4 = createTestTripWithBooking(ownerUser.getId(), 2000.00, false);
        String bkg4 = trip4.getBookings().get(0).getId();
        RecordRefundRequest req4 = RecordRefundRequest.builder()
                .bookingId(bkg4)
                .grossAmount(BigDecimal.valueOf(2000.00))
                .status(RefundStatus.REQUESTED)
                .deductions(Collections.emptyList())
                .build();
        MvcResult res4 = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip4.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data4 = objectMapper.readTree(res4.getResponse().getContentAsString()).get("data");
        JsonNode bSummary4 = data4.get("bookingRefunds").get(0);
        assertEquals(2000.00, bSummary4.get("outstandingAmount").asDouble(), 0.001);
        assertEquals(0.00, bSummary4.get("reduction").asDouble(), 0.001);

        // 5. EXPECTED with and without deductions
        Trip trip5 = createTestTripWithBooking(ownerUser.getId(), 2500.00, false);
        String bkg5 = trip5.getBookings().get(0).getId();
        trip5.getBookings().get(0).setStatus(BookingStatus.CANCELLED);
        tripRepository.save(trip5);
        TripEntity t5Entity = tripJpaRepository.getReferenceById(trip5.getId());
        tripLedgerEntryJpaRepository.save(TripLedgerEntryEntity.builder()
                .id(IdGenerator.generate("LED"))
                .trip(t5Entity)
                .bookingId(bkg5)
                .type(LedgerEntryType.REFUND_EXPECTED)
                .amount(BigDecimal.valueOf(2500.00))
                .basis(LedgerBasis.ESTIMATED)
                .refundStatus("EXPECTED")
                .createdAt(LocalDateTime.now())
                .build());

        // 5a. EXPECTED without deductions: outstanding = 2500, reduction = 0
        MvcResult res5a = mockMvc.perform(get("/api/trips/{tripId}/ledger", trip5.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data5a = objectMapper.readTree(res5a.getResponse().getContentAsString()).get("data");
        JsonNode bSummary5a = data5a.get("bookingRefunds").get(0);
        assertEquals(2500.00, bSummary5a.get("outstandingAmount").asDouble(), 0.001);
        assertEquals(0.00, bSummary5a.get("reduction").asDouble(), 0.001);

        // 5b. EXPECTED with deduction recorded: outstanding = 2500 - 250 = 2250, reduction = 250
        tripLedgerEntryJpaRepository.save(TripLedgerEntryEntity.builder()
                .id(IdGenerator.generate("LED"))
                .trip(t5Entity)
                .bookingId(bkg5)
                .type(LedgerEntryType.REFUND_DEDUCTION)
                .amount(BigDecimal.valueOf(250.00))
                .basis(LedgerBasis.USER_REPORTED)
                .category("CANCELLATION_CHARGE")
                .description("Operator cancellation fee")
                .createdAt(LocalDateTime.now().plusSeconds(1))
                .build());

        MvcResult res5b = mockMvc.perform(get("/api/trips/{tripId}/ledger", trip5.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data5b = objectMapper.readTree(res5b.getResponse().getContentAsString()).get("data");
        JsonNode bSummary5b = data5b.get("bookingRefunds").get(0);
        assertEquals(2250.00, bSummary5b.get("outstandingAmount").asDouble(), 0.001, "EXPECTED with deduction outstanding must equal expected gross minus recorded deductions");
        assertEquals(250.00, bSummary5b.get("reduction").asDouble(), 0.001);
    }

    @Test
    @DisplayName("MAILTO and COPY must never be recorded as SENT: LINK_CREATED and COPIED used, labeled 'Not sent by Odyssey'")
    void testMailtoAndCopyNeverRecordedAsSent() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 1800.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest refReq = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(1800.00))
                .deductions(Collections.emptyList())
                .build();

        MvcResult recResult = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn();

        String entryId = objectMapper.readTree(recResult.getResponse().getContentAsString()).get("data").get("entries").get(0).get("id").asText();

        // Share via MAILTO
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ShareRefundRequest.builder().channel(ShareChannel.MAILTO).confirm(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LINK_CREATED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"));

        // Share via COPY
        mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ShareRefundRequest.builder().channel(ShareChannel.COPY).confirm(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COPIED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"));

        // Query database notifications
        List<NotificationEntity> notifs = notificationJpaRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
        for (NotificationEntity n : notifs) {
            if ("MAILTO".equals(n.getChannel())) {
                assertEquals("LINK_CREATED", n.getStatus(), "MAILTO must be recorded as LINK_CREATED, never SENT");
            }
            if ("COPY".equals(n.getChannel())) {
                assertEquals("COPIED", n.getStatus(), "COPY must be recorded as COPIED, never SENT");
            }
            assertNotEquals("SENT", n.getStatus(), "MAILTO and COPY must NEVER be recorded as SENT");
        }
    }

    @Test
    @DisplayName("REPLACEMENT_COST basis: RECORDED for existing booking, DEMO for marketplace, ESTIMATED for generated/estimated")
    void testReplacementCostBasisRecordedDemoEstimated() throws Exception {
        // 1. Existing booking in trip -> RECORDED
        Trip trip1 = createTestTripWithBooking(ownerUser.getId(), 2500.00, false);
        Booking existingLeg = trip1.getBookings().get(0);
        existingLeg.setSource("DIRECT");
        RecoveryPlan plan1 = RecoveryPlan.builder()
                .id("PLAN-1")
                .newLeg(existingLeg)
                .costBreakdown(CostBreakdown.builder()
                        .lines(List.of(CostBreakdownLine.builder()
                                .label("New FLIGHT Ticket (IndiGo)")
                                .amount(BigDecimal.valueOf(2500.00))
                                .isDeduction(false)
                                .build()))
                        .build())
                .build();
        trip1.setLatestPlan(plan1);
        tripRepository.save(trip1);

        MvcResult res1 = mockMvc.perform(get("/api/trips/{tripId}/ledger", trip1.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data").get("entries");
        boolean foundRecorded = false;
        for (JsonNode e : entries1) {
            if ("REPLACEMENT_COST".equals(e.get("type").asText()) && "RECORDED".equals(e.get("basis").asText())) {
                foundRecorded = true;
            }
        }
        assertTrue(foundRecorded, "Must contain REPLACEMENT_COST with basis RECORDED for existing booking");

        // 2. Marketplace booking -> DEMO
        Trip trip2 = createTestTripWithBooking(ownerUser.getId(), 2500.00, true);
        Booking marketLeg = Booking.builder()
                .id(IdGenerator.generate("BKG"))
                .type(BookingType.FLIGHT)
                .provider("Air India")
                .from("DEL")
                .to("BOM")
                .startTime(baseDepartureTime.plusHours(4))
                .endTime(baseDepartureTime.plusHours(6))
                .cost(BigDecimal.valueOf(1800.00))
                .source("MARKETPLACE")
                .paymentStatus("DEMO_CONFIRMED")
                .status(BookingStatus.CONFIRMED)
                .build();
        trip2.getBookings().add(marketLeg);
        bookingJpaRepository.save(BookingEntity.builder()
                .id(marketLeg.getId())
                .trip(tripJpaRepository.getReferenceById(trip2.getId()))
                .type(BookingType.FLIGHT.name())
                .provider("Air India")
                .fromLocation("DEL")
                .toLocation("BOM")
                .startTime(baseDepartureTime.plusHours(4))
                .endTime(baseDepartureTime.plusHours(6))
                .status(BookingStatus.CONFIRMED.name())
                .cost(BigDecimal.valueOf(1800.00))
                .source("MARKETPLACE")
                .paymentStatus("DEMO_CONFIRMED")
                .build());

        RecoveryPlan plan2 = RecoveryPlan.builder()
                .id("PLAN-2")
                .newLeg(marketLeg)
                .costBreakdown(CostBreakdown.builder()
                        .lines(List.of(CostBreakdownLine.builder()
                                .label("New FLIGHT Ticket (Air India)")
                                .amount(BigDecimal.valueOf(1800.00))
                                .isDeduction(false)
                                .build()))
                        .build())
                .build();
        trip2.setLatestPlan(plan2);
        tripRepository.save(trip2);

        MvcResult res2 = mockMvc.perform(get("/api/trips/{tripId}/ledger", trip2.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("data").get("entries");
        boolean foundDemo = false;
        for (JsonNode e : entries2) {
            if ("REPLACEMENT_COST".equals(e.get("type").asText()) && "DEMO".equals(e.get("basis").asText())) {
                foundDemo = true;
            }
        }
        assertTrue(foundDemo, "Must contain REPLACEMENT_COST with basis DEMO for marketplace booking");

        // 3. Estimated or generated option -> ESTIMATED
        Trip trip3 = createTestTripWithBooking(ownerUser.getId(), 2500.00, false);
        Booking estimatedLeg = Booking.builder()
                .id("BKG-EST-1")
                .type(BookingType.BUS)
                .cost(BigDecimal.valueOf(1200.00))
                .source("ESTIMATED")
                .build();
        RecoveryPlan plan3 = RecoveryPlan.builder()
                .id("PLAN-3")
                .newLeg(estimatedLeg)
                .costBreakdown(CostBreakdown.builder()
                        .lines(List.of(CostBreakdownLine.builder()
                                .label("New BUS Ticket (Estimated)")
                                .amount(BigDecimal.valueOf(1200.00))
                                .isDeduction(false)
                                .build()))
                        .build())
                .build();
        trip3.setLatestPlan(plan3);
        tripRepository.save(trip3);

        MvcResult res3 = mockMvc.perform(get("/api/trips/{tripId}/ledger", trip3.getId())
                        .with(user(ownerUser.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries3 = objectMapper.readTree(res3.getResponse().getContentAsString()).get("data").get("entries");
        boolean foundEstimated = false;
        for (JsonNode e : entries3) {
            if ("REPLACEMENT_COST".equals(e.get("type").asText()) && "ESTIMATED".equals(e.get("basis").asText())) {
                foundEstimated = true;
            }
        }
        assertTrue(foundEstimated, "Must contain REPLACEMENT_COST with basis ESTIMATED for estimated/generated option");
    }

    @Test
    @DisplayName("Ledger reconciliation: sum of net refunds = gross minus deductions, plan net cost equals ledger summary, no double counting")
    void testLedgerReconciliationNoDoubleCounting() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 3000.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest req = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(3000.00))
                .status(RefundStatus.RECEIVED)
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(400.00))
                                .description("Operator cancellation fee 10%")
                                .build(),
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CONVENIENCE_FEE)
                                .amount(BigDecimal.valueOf(100.00))
                                .description("Portal convenience charge")
                                .build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        JsonNode summary = data.get("summary");
        JsonNode entries = data.get("entries");

        // Net recorded refunds in summary must be 3000 - 400 - 100 = 2500
        assertEquals(2500.00, summary.get("recordedRefunds").asDouble(), 0.001);

        // Verify that gross refund row (3000) and deduction rows (400, 100) are not double counted
        double grossFromEntries = 0;
        double deductionsFromEntries = 0;
        for (JsonNode e : entries) {
            String type = e.get("type").asText();
            if ("REFUND_RECORDED".equals(type) || "REFUND".equals(type)) {
                grossFromEntries += e.get("amount").asDouble();
            } else if ("REFUND_DEDUCTION".equals(type)) {
                deductionsFromEntries += e.get("amount").asDouble();
            }
        }

        assertEquals(3000.00, grossFromEntries, 0.001);
        assertEquals(500.00, deductionsFromEntries, 0.001);
        assertEquals(grossFromEntries - deductionsFromEntries, summary.get("recordedRefunds").asDouble(), 0.001,
                "Net recorded refunds must equal sum of gross refunds minus sum of deductions");
    }

    @Test
    @DisplayName("Stable deduction order: creation order preserved across API, UI/DTO, Email and SMS")
    void testStableDeductionOrder() throws Exception {
        Trip trip = createTestTripWithBooking(ownerUser.getId(), 2500.00, false);
        String bookingId = trip.getBookings().get(0).getId();

        RecordRefundRequest req = RecordRefundRequest.builder()
                .bookingId(bookingId)
                .grossAmount(BigDecimal.valueOf(2500.00))
                .status(RefundStatus.RECEIVED)
                .deductions(List.of(
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CANCELLATION_CHARGE)
                                .amount(BigDecimal.valueOf(200.00))
                                .description("First deduction in creation order")
                                .build(),
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.CONVENIENCE_FEE)
                                .amount(BigDecimal.valueOf(75.00))
                                .description("Second deduction in creation order")
                                .build(),
                        RefundDeductionRequest.builder()
                                .category(DeductionCategory.OTHER)
                                .amount(BigDecimal.valueOf(25.00))
                                .description("Third deduction in creation order")
                                .build()
                ))
                .build();

        MvcResult res = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds", trip.getId())
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        JsonNode bSummary = data.get("bookingRefunds").get(0);
        JsonNode deductions = bSummary.get("deductions");

        assertEquals(3, deductions.size());
        assertEquals("CANCELLATION_CHARGE", deductions.get(0).get("category").asText());
        assertEquals("CONVENIENCE_FEE", deductions.get(1).get("category").asText());
        assertEquals("OTHER", deductions.get(2).get("category").asText());

        String entryId = data.get("entries").get(0).get("id").asText();

        // Share via EMAIL and assert deduction order in email body
        MvcResult emailRes = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ShareRefundRequest.builder().channel(ShareChannel.EMAIL).confirm(true).build())))
                .andExpect(status().isOk())
                .andReturn();

        String emailContent = objectMapper.readTree(emailRes.getResponse().getContentAsString()).get("data").get("content").asText();
        int idx1 = emailContent.indexOf("CANCELLATION_CHARGE");
        int idx2 = emailContent.indexOf("CONVENIENCE_FEE");
        int idx3 = emailContent.indexOf("OTHER");
        assertTrue(idx1 < idx2 && idx2 < idx3, "Email must list deductions in stable creation order");

        // Share via SMS and assert stable order
        MvcResult smsRes = mockMvc.perform(post("/api/trips/{tripId}/ledger/refunds/{entryId}/share", trip.getId(), entryId)
                        .with(user(ownerUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ShareRefundRequest.builder().channel(ShareChannel.SMS).confirm(true).build())))
                .andExpect(status().isOk())
                .andReturn();

        String smsContent = objectMapper.readTree(smsRes.getResponse().getContentAsString()).get("data").get("content").asText();
        int sIdx1 = smsContent.indexOf("CANCELLATION_CHARGE");
        int sIdx2 = smsContent.indexOf("CONVENIENCE_FEE");
        int sIdx3 = smsContent.indexOf("OTHER");
        if (sIdx1 != -1 && sIdx2 != -1 && sIdx3 != -1) {
            assertTrue(sIdx1 < sIdx2 && sIdx2 < sIdx3, "SMS must list deductions in stable creation order");
        }
    }
}
