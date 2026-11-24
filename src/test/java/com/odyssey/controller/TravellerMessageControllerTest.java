package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.DraftMessageRequest;
import com.odyssey.dto.request.EditMessageRequest;
import com.odyssey.dto.request.SendMessageRequest;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.PlanChecklistItemEntity;
import com.odyssey.entity.RecoveryPlanEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.Disruption;
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
import com.odyssey.repository.jpa.PlanChecklistItemJpaRepository;
import com.odyssey.repository.jpa.RecoveryPlanJpaRepository;
import com.odyssey.repository.jpa.TravellerMessageJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.service.LlmService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TravellerMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripJpaRepository tripJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private TravellerMessageJpaRepository messageJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private AuditEntryJpaRepository auditEntryJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanChecklistItemJpaRepository planChecklistItemJpaRepository;

    @Autowired
    private RecoveryPlanJpaRepository recoveryPlanJpaRepository;

    @MockBean
    private EmailChannel emailChannel;

    @MockBean
    private SmsChannel smsChannel;

    @MockBean
    private LlmService llmService;

    private UserEntity primaryUser;
    private UserEntity otherUser;

    @BeforeEach
    public void setup() {
        notificationJpaRepository.deleteAll();
        messageJpaRepository.deleteAll();
        planChecklistItemJpaRepository.deleteAll();
        recoveryPlanJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        auditEntryJpaRepository.deleteAll();
        tripJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        primaryUser = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Odyssey Explorer")
                .email("odyssey@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .phone("+919876543210")
                .createdAt(LocalDateTime.now())
                .build());

        otherUser = userJpaRepository.save(UserEntity.builder()
                .id(IdGenerator.generate("usr"))
                .name("Intruder Explorer")
                .email("intruder@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .phone("+919123456789")
                .createdAt(LocalDateTime.now())
                .build());

        // Default: Mock LLM returns template text unchanged
        when(llmService.rewordDraft(any(Trip.class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private Trip createStandardDisruptedTrip(UserEntity owner) {
        String tripId = "trip-phase13-" + System.currentTimeMillis();
        Trip trip = Trip.builder()
                .id(tripId)
                .userId(owner.getId())
                .title("Goa Expedition")
                .description("Scenic coastal trip")
                .travellerName("Ramesh Hegde")
                .travellerPhone("+919876543210")
                .travellerEmail("ramesh@example.com")
                .status("DISRUPTED")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("15000.00"))
                        .mustArriveBy(LocalDateTime.of(2026, 10, 22, 22, 0))
                        .build())
                .bookings(new ArrayList<>())
                .activeDisruptions(new ArrayList<>())
                .build();

        Booking bus = Booking.builder()
                .id("bkg-bus-" + System.currentTimeMillis())
                .type(BookingType.BUS)
                .provider("KSRTC Express")
                .bookingReference("KSRTC-8899")
                .from("Hubballi")
                .to("Panaji")
                .startTime(LocalDateTime.of(2026, 10, 22, 10, 0))
                .endTime(LocalDateTime.of(2026, 10, 22, 16, 0))
                .cost(new BigDecimal("950.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking hotel = Booking.builder()
                .id("bkg-hotel-" + System.currentTimeMillis())
                .type(BookingType.HOTEL)
                .provider("Taj Fort Aguada")
                .bookingReference("TAJ-4432")
                .from("Panaji")
                .to("Panaji")
                .hotelContactEmail("reservations@tajhotels.example.com")
                .startTime(LocalDateTime.of(2026, 10, 22, 18, 0))
                .endTime(LocalDateTime.of(2026, 10, 24, 11, 0))
                .cost(new BigDecimal("6500.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        trip.getBookings().add(bus);
        trip.getBookings().add(hotel);

        Disruption disruption = Disruption.builder()
                .id("dis-" + System.currentTimeMillis())
                .bookingId(bus.getId())
                .type(DisruptionType.CANCELLATION)
                .reason("Landslide on Anmod Ghat highway")
                .delayMinutes(180)
                .reportedAt(LocalDateTime.now())
                .build();
        trip.getActiveDisruptions().add(disruption);

        RecoveryPlan plan = RecoveryPlan.builder()
                .id("plan-" + System.currentTimeMillis())
                .tripId(tripId)
                .chosenOptionLabel(OptionLabel.FASTEST)
                .newLeg(Booking.builder()
                        .id("bkg-repl-" + System.currentTimeMillis())
                        .type(BookingType.FLIGHT)
                        .provider("IndiGo Flight 6E-241")
                        .bookingReference("6E-241-CONF")
                        .from("Hubballi")
                        .to("Dabolim Airport")
                        .startTime(LocalDateTime.of(2026, 10, 22, 14, 0))
                        .endTime(LocalDateTime.of(2026, 10, 22, 15, 30))
                        .cost(new BigDecimal("4200.00"))
                        .build())
                .build();
        trip.setLatestPlan(plan);

        return tripRepository.save(trip);
    }

    @Test
    @DisplayName("Draft hotel message fills strictly from stored data with no guessing")
    public void testDraftHotelMessageDeterministicTemplateStoredData() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        Booking hotelBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.HOTEL).findFirst().orElseThrow();

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .bookingId(hotelBooking.getId())
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientType").value("HOTEL"))
                .andExpect(jsonPath("$.data.draftKind").value("LATE_CHECKIN"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.subject").value(containsString("Late Check-in Notice")))
                .andExpect(jsonPath("$.data.subject").value(containsString("TAJ-4432")))
                .andExpect(jsonPath("$.data.body").value(containsString("Taj Fort Aguada")))
                .andExpect(jsonPath("$.data.body").value(containsString("TAJ-4432")))
                .andExpect(jsonPath("$.data.body").value(containsString("Ramesh Hegde")))
                .andExpect(jsonPath("$.data.body").value(containsString("+919876543210")))
                .andExpect(jsonPath("$.data.body").value(containsString("Landslide on Anmod Ghat highway")))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String body = json.path("data").path("body").asText();
        // Stored data facts are present
        assertTrue(body.contains("Taj Fort Aguada"));
        // Does NOT contain synthetic or unconfigured data
        assertFalse(body.contains("null"));
        assertFalse(body.contains("undefined"));
    }

    @Test
    @DisplayName("Draft operator cancellation and refund request filled with carrier data")
    public void testDraftOperatorCancellationAndRefundRequest() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        Booking busBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.BUS).findFirst().orElseThrow();

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.CANCELLATION_AND_REFUND_REQUEST)
                .bookingId(busBooking.getId())
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientType").value("OPERATOR"))
                .andExpect(jsonPath("$.data.draftKind").value("CANCELLATION_AND_REFUND_REQUEST"))
                .andExpect(jsonPath("$.data.body").value(containsString("KSRTC Express")))
                .andExpect(jsonPath("$.data.body").value(containsString("KSRTC-8899")))
                .andExpect(jsonPath("$.data.body").value(containsString("Hubballi -> Panaji")))
                .andExpect(jsonPath("$.data.body").value(containsString("Landslide on Anmod Ghat highway")));
    }

    @Test
    @DisplayName("Draft arrival update for family uses warm greeting and traveller name")
    public void testDraftArrivalUpdateForFamily() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.FAMILY)
                .draftKind(DraftKind.ARRIVAL_UPDATE)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientType").value("FAMILY"))
                .andExpect(jsonPath("$.data.draftKind").value("ARRIVAL_UPDATE"))
                .andExpect(jsonPath("$.data.body").value(containsString("Hi everyone,")))
                .andExpect(jsonPath("$.data.body").value(containsString("Goa Expedition")))
                .andExpect(jsonPath("$.data.body").value(containsString("Ramesh Hegde")));
    }

    @Test
    @DisplayName("Marketplace booking adds required demo disclaimer")
    public void testMarketplaceBookingDisclaimerAppended() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        Booking demoBooking = Booking.builder()
                .id("bkg-demo-" + System.currentTimeMillis())
                .type(BookingType.BUS)
                .provider("Demo KSRTC Service")
                .bookingReference("DEMO-BUS-1234")
                .from("Hubballi")
                .to("Panaji")
                .startTime(LocalDateTime.of(2026, 10, 22, 10, 0))
                .endTime(LocalDateTime.of(2026, 10, 22, 16, 0))
                .status(BookingStatus.CONFIRMED)
                .paymentStatus("DEMO_REFUNDED")
                .source("MARKETPLACE_DEMO")
                .cost(new BigDecimal("800.00"))
                .build();
        trip.getBookings().add(demoBooking);
        tripRepository.save(trip);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.CANCELLATION_AND_REFUND_REQUEST)
                .bookingId(demoBooking.getId())
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(containsString(
                        "Demo marketplace. No real ticket is issued and no money was charged.")));
    }

    @Test
    @DisplayName("Estimated schedule adds required estimate disclaimer")
    public void testEstimatedScheduleDisclaimerAppended() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        Booking estBooking = Booking.builder()
                .id("bkg-est-" + System.currentTimeMillis())
                .type(BookingType.BUS)
                .provider("Estimated Regional Transport")
                .bookingReference("EST-999")
                .from("Hubballi")
                .to("Panaji")
                .startTime(LocalDateTime.of(2026, 10, 22, 10, 0))
                .endTime(LocalDateTime.of(2026, 10, 22, 16, 0))
                .status(BookingStatus.CONFIRMED)
                .source("ESTIMATED_SCHEDULE")
                .cost(new BigDecimal("500.00"))
                .build();
        trip.getBookings().add(estBooking);
        tripRepository.save(trip);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.DELAY_NOTICE)
                .bookingId(estBooking.getId())
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(containsString(
                        "Schedule is estimated, please verify.")));
    }

    @Test
    @DisplayName("Normal trip without marketplace or estimates does NOT have demo strings")
    public void testNormalTripNoDemoDisclaimers() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        Booking bus = trip.getBookings().stream().filter(b -> b.getType() == BookingType.BUS).findFirst().orElseThrow();

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.DELAY_NOTICE)
                .bookingId(bus.getId())
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(not(containsString("Demo marketplace"))))
                .andExpect(jsonPath("$.data.body").value(not(containsString("Schedule is estimated"))));
    }

    @Test
    @DisplayName("LLM verification rejects hallucinated dates or numbers and falls back to TEMPLATE")
    public void testLlmVerificationRejectionWhenHallucinationPresent() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        // Simulated LLM hallucinates a non-existent train 12951 and invented departure time 23:45
        when(llmService.rewordDraft(any(Trip.class), anyString(), anyString()))
                .thenReturn("Dear Sir, Train 12951 departing at 23:45 is delayed. Regards, Ramesh.");

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.DELAY_NOTICE)
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedBy").value("TEMPLATE")) // Fallback to template!
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String body = json.path("data").path("body").asText();
        // Hallucinated train number was rejected
        assertFalse(body.contains("12951"));
        assertFalse(body.contains("23:45"));
    }

    @Test
    @DisplayName("LLM verification accepts valid rewording with source data and marks LLM")
    public void testLlmRewordSuccessWhenVerified() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        // LLM rewords politely using only known source facts (Ramesh Hegde, Goa Expedition, Landslide)
        when(llmService.rewordDraft(any(Trip.class), anyString(), anyString()))
                .thenReturn("Hello! Update for Goa Expedition: Ramesh Hegde is delayed due to Landslide on Anmod Ghat highway. Regards.");

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.FAMILY)
                .draftKind(DraftKind.DELAY_NOTICE)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedBy").value("LLM"))
                .andExpect(jsonPath("$.data.body").value(containsString("Hello! Update for Goa Expedition")));
    }

    @Test
    @DisplayName("Editing draft updates subject/body and moves status to EDITED")
    public void testEditDraftSuccess() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        EditMessageRequest editReq = EditMessageRequest.builder()
                .subject("Revised Subject: Check-in Delayed")
                .body("Dear Hotel Team, Please hold my room until 23:00 tonight. Thanks, Ramesh Hegde.")
                .build();

        mockMvc.perform(put("/api/trips/" + trip.getId() + "/messages/" + msgId)
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("EDITED"))
                .andExpect(jsonPath("$.data.subject").value("Revised Subject: Check-in Delayed"))
                .andExpect(jsonPath("$.data.body").value(containsString("hold my room until 23:00 tonight")));
    }

    @Test
    @DisplayName("Editing draft rejects body exceeding 4000 characters")
    public void testEditDraftLengthValidation() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        String longBody = "A".repeat(4005);
        EditMessageRequest editReq = EditMessageRequest.builder()
                .subject("Subject")
                .body(longBody)
                .build();

        mockMvc.perform(put("/api/trips/" + trip.getId() + "/messages/" + msgId)
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("4000")));
    }

    @Test
    @DisplayName("Send message requires confirm = true, returns 400 if confirm is false or omitted")
    public void testSendMessageRequiresConfirmTrue() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        SendMessageRequest unconfirmed = SendMessageRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("hotel@example.com")
                .confirm(false)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unconfirmed)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Confirmation is required")));
    }

    @Test
    @DisplayName("Email send returns SIMULATED when SMTP inactive, records audit and notification")
    public void testSendMessageEmailSimulatedWithoutActiveSmtp() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        when(emailChannel.isActive()).thenReturn(false);
        when(emailChannel.send(any())).thenReturn(SendResult.builder().status(SendStatus.SIMULATED).build());

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("hotel@example.com")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SIMULATED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Simulated, not sent"))
                .andExpect(jsonPath("$.data.recipientMasked").value("h***@example.com"));

        // Verify notification entry recorded
        List<NotificationEntity> notifs = notificationJpaRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertFalse(notifs.isEmpty());
        NotificationEntity notif = notifs.get(0);
        assertEquals("TRAVELLER_MESSAGE", notif.getEventType());
        assertEquals("SIMULATED", notif.getStatus());

        // Verify audit log has USER entry
        List<AuditEntryEntity> audits = auditEntryJpaRepository.findByTripIdOrderByTimestampDesc(trip.getId());
        boolean hasAudit = audits.stream().anyMatch(a -> "USER".equals(a.getActor()) && a.getAction().contains("TRAVELLER_MESSAGE"));
        assertTrue(hasAudit);
    }

    @Test
    @DisplayName("MAILTO channel sets status LINK_CREATED labelled Not sent by Odyssey, never SENT")
    public void testSendMessageMailtoGivesLinkCreatedNotSent() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .channel(ShareChannel.MAILTO)
                .recipient("reservations@tajhotels.example.com")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LINK_CREATED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"))
                .andExpect(jsonPath("$.data.mailtoUrl").value(startsWith("mailto:reservations@tajhotels.example.com")));
    }

    @Test
    @DisplayName("COPY channel sets status COPIED labelled Not sent by Odyssey, never SENT")
    public void testSendMessageCopyGivesCopiedNotSent() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.FAMILY)
                .draftKind(DraftKind.ARRIVAL_UPDATE)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .channel(ShareChannel.COPY)
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COPIED"))
                .andExpect(jsonPath("$.data.statusLabel").value("Not sent by Odyssey"));
    }

    @Test
    @DisplayName("Recipient validation: rejects malformed email, accepts 10-digit phone stored as +91")
    public void testRecipientContactValidation() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Invalid email
        SendMessageRequest invalidEmailReq = SendMessageRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("invalid-email-address")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));

        // Valid 10-digit phone number stored with +91
        when(smsChannel.isActive()).thenReturn(false);
        when(smsChannel.send(any())).thenReturn(SendResult.builder().status(SendStatus.SIMULATED).build());

        SendMessageRequest validPhoneReq = SendMessageRequest.builder()
                .channel(ShareChannel.SMS)
                .recipient("9876554321")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPhoneReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIMULATED"))
                .andExpect(jsonPath("$.data.recipientMasked").value(containsString("+91")));
    }

    @Test
    @DisplayName("Rate limiting: enforces maximum 10 sends per hour per trip (429)")
    public void testRateLimiting10PerHourPerTrip() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        when(emailChannel.isActive()).thenReturn(false);
        when(emailChannel.send(any())).thenReturn(SendResult.builder().status(SendStatus.SIMULATED).build());

        // Perform 10 sends
        for (int i = 0; i < 10; i++) {
            DraftMessageRequest draftReq = DraftMessageRequest.builder()
                    .recipientType(RecipientType.OTHER)
                    .draftKind(DraftKind.ARRIVAL_UPDATE)
                    .build();

            MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                            .with(user(primaryUser.getEmail()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(draftReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                    .path("data").path("id").asText();

            SendMessageRequest sendReq = SendMessageRequest.builder()
                    .channel(ShareChannel.EMAIL)
                    .recipient("test" + i + "@example.com")
                    .confirm(true)
                    .build();

            mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                            .with(user(primaryUser.getEmail()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sendReq)))
                    .andExpect(status().isOk());
        }

        // 11th send should fail with 429 Too Many Requests
        DraftMessageRequest draftReq11 = DraftMessageRequest.builder()
                .recipientType(RecipientType.OTHER)
                .draftKind(DraftKind.ARRIVAL_UPDATE)
                .build();

        MvcResult draftRes11 = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq11)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId11 = objectMapper.readTree(draftRes11.getResponse().getContentAsString())
                .path("data").path("id").asText();

        SendMessageRequest sendReq11 = SendMessageRequest.builder()
                .channel(ShareChannel.EMAIL)
                .recipient("test11@example.com")
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId11 + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq11)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Rate limit")));
    }

    @Test
    @DisplayName("Ownership security: 401 unauthenticated, 403 or 404 for other user")
    public void testOwnershipAndAuthentication() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);

        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .build();

        // 1. Unauthenticated -> 401
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isUnauthorized());

        // 2. Intruder user -> 403 or 404
        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(otherUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sending hotel message does NOT mark MESSAGE_HOTEL recovery action done")
    public void testSendingDoesNotMarkMessageHotelActionDone() throws Exception {
        Trip trip = createStandardDisruptedTrip(primaryUser);
        TripEntity tripEntity = tripJpaRepository.findById(trip.getId()).orElseThrow();

        RecoveryPlanEntity planEntity = recoveryPlanJpaRepository.save(RecoveryPlanEntity.builder()
                .id(IdGenerator.generate("pln"))
                .trip(tripEntity)
                .netTripCost(new BigDecimal("10000.00"))
                .maxBudget(new BigDecimal("15000.00"))
                .remainingBudget(new BigDecimal("5000.00"))
                .createdAt(LocalDateTime.now())
                .build());

        PlanChecklistItemEntity hotelItem = planChecklistItemJpaRepository.save(PlanChecklistItemEntity.builder()
                .id(IdGenerator.generate("act"))
                .recoveryPlan(planEntity)
                .tripId(trip.getId())
                .type(RecoveryActionType.MESSAGE_HOTEL)
                .text("Inform hotel of revised arrival")
                .status(RecoveryActionStatus.PENDING)
                .done(false)
                .itemOrder(1)
                .build());

        // Draft and send hotel message
        DraftMessageRequest draftReq = DraftMessageRequest.builder()
                .recipientType(RecipientType.HOTEL)
                .draftKind(DraftKind.LATE_CHECKIN)
                .bookingId(trip.getBookings().get(1).getId())
                .build();

        MvcResult draftRes = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andReturn();

        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .channel(ShareChannel.COPY)
                .confirm(true)
                .build();

        mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/" + msgId + "/send")
                        .with(user(primaryUser.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk());

        // Verify recovery tracker still has the action as PENDING (sending does NOT mark DONE)
        PlanChecklistItemEntity reloadedItem = planChecklistItemJpaRepository.findById(hotelItem.getId()).orElseThrow();
        assertEquals(RecoveryActionStatus.PENDING, reloadedItem.getStatus());
        assertFalse(reloadedItem.getDone());
    }
}
