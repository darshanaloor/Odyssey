package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.RecoveryActionNoteRequest;
import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.request.SelectOptionRequest;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import com.odyssey.model.HardCommitment;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RecoveryExecutionTrackerTest {

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
        private NotificationJpaRepository notificationJpaRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        private UserEntity ownerUser;
        private UserEntity otherUser;
        private LocalDateTime baseDepartureTime;

        @BeforeEach
        void setUp() {
                ownerUser = userJpaRepository.findByEmail("tracker_owner@example.com")
                                .orElseGet(() -> userJpaRepository.save(UserEntity.builder()
                                                .id("USR-TRACK-1")
                                                .email("tracker_owner@example.com")
                                                .passwordHash(passwordEncoder.encode("Password123"))
                                                .name("Tracker Owner")
                                                .createdAt(LocalDateTime.now())
                                                .build()));

                otherUser = userJpaRepository.findByEmail("tracker_other@example.com")
                                .orElseGet(() -> userJpaRepository.save(UserEntity.builder()
                                                .id("USR-TRACK-2")
                                                .email("tracker_other@example.com")
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

        private Trip createAndSaveCustomTrip(UserEntity owner, boolean withCabHotelAndCommitment) {
                String tripId = IdGenerator.generate("TRIP");
                String transitId = IdGenerator.generate("BKG");
                List<Booking> bookings = new ArrayList<>();

                bookings.add(Booking.builder()
                                .id(transitId)
                                .type(BookingType.BUS)
                                .provider("Emerald Intercity Coach")
                                .from("BLR")
                                .to("IXE")
                                .startTime(baseDepartureTime)
                                .endTime(baseDepartureTime.plusHours(5))
                                .cost(new BigDecimal("1800.00"))
                                .status(BookingStatus.CONFIRMED)
                                .bookingReference("PNR-BUS-9001")
                                .build());

                if (withCabHotelAndCommitment) {
                        String cabId = IdGenerator.generate("BKG");
                        String hotelId = IdGenerator.generate("BKG");
                        bookings.add(Booking.builder()
                                        .id(cabId)
                                        .type(BookingType.CAB)
                                        .provider("Harbor Sedan Transfers")
                                        .from("IXE")
                                        .to("Malabar Crest Hotel")
                                        .startTime(baseDepartureTime.plusHours(5).plusMinutes(15))
                                        .endTime(baseDepartureTime.plusHours(6))
                                        .cost(new BigDecimal("950.00"))
                                        .status(BookingStatus.CONFIRMED)
                                        .dependsOn(new ArrayList<>(List.of(transitId)))
                                        .bookingReference("CAB-REF-4422")
                                        .build());

                        bookings.add(Booking.builder()
                                        .id(hotelId)
                                        .type(BookingType.HOTEL)
                                        .provider("Malabar Crest Hotel")
                                        .from("Malabar Crest Hotel")
                                        .to("Malabar Crest Hotel")
                                        .startTime(baseDepartureTime.plusHours(6).plusMinutes(15))
                                        .endTime(baseDepartureTime.plusDays(1).plusHours(10))
                                        .cost(new BigDecimal("5200.00"))
                                        .status(BookingStatus.CONFIRMED)
                                        .dependsOn(new ArrayList<>(List.of(cabId)))
                                        .bookingReference("HTL-CONF-8811")
                                        .hotelContactEmail("concierge@malabarcrest.test")
                                        .build());
                }

                List<HardCommitment> commitments = withCabHotelAndCommitment
                                ? List.of(HardCommitment.builder()
                                                .id(IdGenerator.generate("COM"))
                                                .name("Annual Coastal Summit Check-In")
                                                .commitmentTime(baseDepartureTime.plusHours(16))
                                                .location("Convention Hall")
                                                .build())
                                : new ArrayList<>();

                Trip trip = Trip.builder()
                                .id(tripId)
                                .title("Custom Recovery Tracker Trip")
                                .userId(owner.getId())
                                .travellerName(owner.getName())
                                .status("PLANNED")
                                .constraints(Constraints.builder()
                                                .maxBudget(new BigDecimal("25000.00"))
                                                .mustArriveBy(baseDepartureTime.plusHours(14))
                                                .build())
                                .hardCommitments(commitments)
                                .bookings(bookings)
                                .build();

                return tripRepository.save(trip);
        }

        private String disruptRecoverAndApprove(Trip trip, String disruptedBookingId) throws Exception {
                ReportDisruptionRequest disruptionReq = ReportDisruptionRequest.builder()
                                .type(DisruptionType.CANCELLATION)
                                .bookingId(disruptedBookingId)
                                .reason("Coach mechanical cancellation")
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
                assertTrue(options.isArray() && options.size() > 0, "Expected at least one feasible recovery option");
                String optionId = options.get(0).path("id").asText();

                SelectOptionRequest selectReq = SelectOptionRequest.builder().optionId(optionId).build();
                mockMvc.perform(post("/api/trips/" + trip.getId() + "/approve")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(selectReq)))
                                .andExpect(status().isOk());

                return optionId;
        }

        private JsonNode getActions(String tripId, UserEntity actor) throws Exception {
                MvcResult res = mockMvc.perform(get("/api/trips/" + tripId + "/actions")
                                .with(user(actor.getEmail())))
                                .andExpect(status().isOk())
                                .andReturn();
                return objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        }

        @Test
        @DisplayName("Unapproved trip returns empty unapproved actions and 409 on complete")
        void unapprovedTripReturnsEmptyAndRejectsMutation() throws Exception {
                Trip trip = createAndSaveCustomTrip(ownerUser, false);
                String transitId = trip.getBookings().get(0).getId();

                mockMvc.perform(get("/api/trips/" + trip.getId() + "/actions")
                                .with(user(ownerUser.getEmail())))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.approved", is(false)))
                                .andExpect(jsonPath("$.data.actions", hasSize(0)))
                                .andExpect(jsonPath("$.data.progressText", is("0 of 0 done")));

                mockMvc.perform(post("/api/trips/" + trip.getId() + "/actions/ANY/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"early\"}"))
                                .andExpect(status().isConflict());

                assertNotNull(transitId);
        }

        @Test
        @DisplayName("Bus-only trip generates only applicable actions (no hotel/cab/cancel/confirm) and no demo strings")
        void busOnlyTripGeneratesOnlyApplicableActionsAndNoDemoLeaks() throws Exception {
                Trip busOnly = createAndSaveCustomTrip(ownerUser, false);
                String transitId = busOnly.getBookings().get(0).getId();
                disruptRecoverAndApprove(busOnly, transitId);

                JsonNode data = getActions(busOnly.getId(), ownerUser);
                assertTrue(data.path("approved").asBoolean());
                JsonNode actions = data.path("actions");
                assertEquals(2, actions.size());
                assertEquals("BOOK_REPLACEMENT", actions.get(0).path("type").asText());
                assertEquals("PENDING", actions.get(0).path("status").asText());
                assertEquals("REQUEST_REFUND", actions.get(1).path("type").asText());
                assertEquals("PNR-BUS-9001", actions.get(1).path("bookingReference").asText());
                assertEquals("0 of 2 done", data.path("progressText").asText());

                // Ensure no demo trip strings leaked into custom trip actions
                String rawJson = actions.toString();
                assertFalse(rawJson.contains("Ocean Pearl"));
                assertFalse(rawJson.contains("Gateway Hotel"));
                assertFalse(rawJson.contains("IndiGo 6E-534"));
        }

        @Test
        @DisplayName("Full Bus+Cab+Hotel+Commitment trip enforces order protection, persists progress, writes USER audits, and causes no booking/notification side effects")
        void fullExecutionTrackerLifecycleAndOrderProtection() throws Exception {
                Trip fullTrip = createAndSaveCustomTrip(ownerUser, true);
                String busId = fullTrip.getBookings().stream()
                                .filter(b -> b.getType() == BookingType.BUS)
                                .map(Booking::getId)
                                .findFirst()
                                .orElseThrow();

                disruptRecoverAndApprove(fullTrip, busId);

                long baselineBookingCount = bookingJpaRepository.findByTripIdOrderByStartTimeAsc(fullTrip.getId())
                                .size();
                long baselineNotificationCount = notificationJpaRepository.count();

                JsonNode initial = getActions(fullTrip.getId(), ownerUser);
                JsonNode actions = initial.path("actions");
                // Applicable actions: BOOK_REPLACEMENT, BOOK_CAB, MESSAGE_HOTEL,
                // CANCEL_OLD_BOOKING, REQUEST_REFUND, CONFIRM_ARRIVAL
                assertEquals(6, actions.size());
                assertEquals("0 of 6 done", initial.path("progressText").asText());

                String bookRepId = null;
                String bookCabId = null;
                String messageHotelId = null;
                String cancelOldId = null;
                String refundId = null;
                for (JsonNode a : actions) {
                        switch (a.path("type").asText()) {
                                case "BOOK_REPLACEMENT" -> bookRepId = a.path("id").asText();
                                case "BOOK_CAB" -> bookCabId = a.path("id").asText();
                                case "MESSAGE_HOTEL" -> {
                                        messageHotelId = a.path("id").asText();
                                        assertEquals("HTL-CONF-8811", a.path("bookingReference").asText());
                                        assertEquals("concierge@malabarcrest.test", a.path("contactInfo").asText());
                                }
                                case "CANCEL_OLD_BOOKING" -> {
                                        cancelOldId = a.path("id").asText();
                                        assertFalse(a.path("canComplete").asBoolean());
                                        assertEquals("CAB-REF-4422", a.path("bookingReference").asText());
                                }
                                case "REQUEST_REFUND" -> {
                                        refundId = a.path("id").asText();
                                        assertFalse(a.path("canComplete").asBoolean());
                                        assertEquals("PNR-BUS-9001", a.path("bookingReference").asText());
                                }
                                default -> {
                                }
                        }
                }
                assertNotNull(bookRepId);
                assertNotNull(bookCabId);
                assertNotNull(messageHotelId);
                assertNotNull(cancelOldId);
                assertNotNull(refundId);

                // Order protection: completing CANCEL_OLD_BOOKING or REQUEST_REFUND before
                // BOOK_REPLACEMENT -> 409 Conflict
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + cancelOldId + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message", containsString("replacement")));

                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + refundId + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isConflict());

                // Skip without note -> 400 Bad Request
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + bookCabId + "/skip")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"   \"}"))
                                .andExpect(status().isBadRequest());

                // Complete BOOK_REPLACEMENT -> succeeds & unlocks cleanup actions
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + bookRepId + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new RecoveryActionNoteRequest("Ticket booked on carrier portal"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.doneCount", is(1)))
                                .andExpect(jsonPath("$.data.progressText", is("1 of 6 done")));

                // Complete MESSAGE_HOTEL and CANCEL_OLD_BOOKING (now unblocked) -> 3 of 6 done
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + messageHotelId + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Emailed front desk\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.progressText", is("2 of 6 done")));

                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + cancelOldId + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Cancelled cab by phone\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.doneCount", is(3)))
                                .andExpect(jsonPath("$.data.progressText", is("3 of 6 done")));

                // Skip BOOK_CAB with valid note
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + bookCabId + "/skip")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Friend picking me up\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.skippedCount", is(1)))
                                .andExpect(jsonPath("$.data.doneCount", is(3)))
                                .andExpect(jsonPath("$.data.progressText", is("3 of 6 done")));

                // Verify persistence on fresh GET
                JsonNode persisted = getActions(fullTrip.getId(), ownerUser);
                assertEquals("3 of 6 done", persisted.path("progressText").asText());
                assertEquals(3, persisted.path("doneCount").asInt());
                assertEquals(1, persisted.path("skippedCount").asInt());
                assertEquals(2, persisted.path("pendingCount").asInt());

                // Reopen CANCEL_OLD_BOOKING -> DONE count drops back to 2
                mockMvc.perform(post("/api/trips/" + fullTrip.getId() + "/actions/" + cancelOldId + "/reopen")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Need refund receipt first\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.doneCount", is(2)))
                                .andExpect(jsonPath("$.data.progressText", is("2 of 6 done")));

                // Verify USER audit entries written for complete, skip, and reopen
                List<AuditEntryEntity> audits = auditEntryJpaRepository
                                .findByTripIdOrderByTimestampAsc(fullTrip.getId());
                long userActionAudits = audits.stream()
                                .filter(a -> "USER".equals(a.getActor())
                                                && a.getAction().startsWith("RECOVERY_ACTION_"))
                                .count();
                assertEquals(5, userActionAudits);

                // Verify zero unintended side effects (no bookings altered, no notifications
                // sent, trip status stays RESOLVED)
                List<BookingEntity> currentBookings = bookingJpaRepository
                                .findByTripIdOrderByStartTimeAsc(fullTrip.getId());
                assertEquals(baselineBookingCount, currentBookings.size());
                assertEquals(baselineNotificationCount, notificationJpaRepository.count());
                assertEquals("RESOLVED", tripRepository.findById(fullTrip.getId()).orElseThrow().getStatus());
        }

        @Test
        @DisplayName("Skipping BOOK_REPLACEMENT unblocks CANCEL_OLD_BOOKING and multi-trip checklist IDs do not collide")
        void skipReplacementUnblocksCleanupAndMultipleTripsDoNotCollide() throws Exception {
                Trip tripOne = createAndSaveCustomTrip(ownerUser, false);
                Trip tripTwo = createAndSaveCustomTrip(ownerUser, false);
                disruptRecoverAndApprove(tripOne, tripOne.getBookings().get(0).getId());
                disruptRecoverAndApprove(tripTwo, tripTwo.getBookings().get(0).getId());

                JsonNode actionsOne = getActions(tripOne.getId(), ownerUser).path("actions");
                JsonNode actionsTwo = getActions(tripTwo.getId(), ownerUser).path("actions");
                assertEquals(2, actionsOne.size());
                assertEquals(2, actionsTwo.size());
                assertNotEquals(actionsOne.get(0).path("id").asText(), actionsTwo.get(0).path("id").asText());

                String repOne = actionsOne.get(0).path("id").asText();
                String refundOne = actionsOne.get(1).path("id").asText();

                mockMvc.perform(post("/api/trips/" + tripOne.getId() + "/actions/" + repOne + "/skip")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Taking own vehicle instead\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.actions[1].canComplete", is(true)));

                mockMvc.perform(post("/api/trips/" + tripOne.getId() + "/actions/" + refundOne + "/complete")
                                .with(user(ownerUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.progressText", is("1 of 2 done")));

                // Trip Two remains untouched at 0 of 2 done
                JsonNode tripTwoState = getActions(tripTwo.getId(), ownerUser);
                assertEquals("0 of 2 done", tripTwoState.path("progressText").asText());
        }

        @Test
        @DisplayName("Auth, ownership (403/404), and CSRF enforcement")
        void authOwnershipAndCsrfEnforcement() throws Exception {
                Trip trip = createAndSaveCustomTrip(ownerUser, false);
                disruptRecoverAndApprove(trip, trip.getBookings().get(0).getId());
                String actionId = getActions(trip.getId(), ownerUser).path("actions").get(0).path("id").asText();

                // 401 when unauthenticated
                mockMvc.perform(get("/api/trips/" + trip.getId() + "/actions"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(post("/api/trips/" + trip.getId() + "/actions/" + actionId + "/complete")
                                .with(csrf()))
                                .andExpect(status().isUnauthorized());

                // 403 when CSRF missing on authenticated POST
                mockMvc.perform(post("/api/trips/" + trip.getId() + "/actions/" + actionId + "/complete")
                                .with(user(ownerUser.getEmail()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden());

                // 403 for another user on GET and POST
                mockMvc.perform(get("/api/trips/" + trip.getId() + "/actions")
                                .with(user(otherUser.getEmail())))
                                .andExpect(status().isForbidden());

                mockMvc.perform(post("/api/trips/" + trip.getId() + "/actions/" + actionId + "/complete")
                                .with(user(otherUser.getEmail())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden());

                // 404 for unknown trip
                mockMvc.perform(get("/api/trips/TRIP-DOES-NOT-EXIST/actions")
                                .with(user(ownerUser.getEmail())))
                                .andExpect(status().isNotFound());
        }
}
