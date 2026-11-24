package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.CreateTripRequest;
import com.odyssey.dto.request.DraftMessageRequest;
import com.odyssey.dto.response.RecoveryResponse;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.service.RecoveryService;
import com.odyssey.service.ScoringService;
import com.odyssey.service.TripService;
import com.odyssey.util.EmergencyContactUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 17: Traveller Needs Automated Tests.
 * Explicitly tests and verifies:
 * 1. FASTEST and CHEAPEST identical with and without needs flags
 * 2. No option labelled accessible (and mandatory disclaimer banner is present)
 * 3. Checklist steps only when a flag is set
 * 4. The hotel step only when a HOTEL booking exists
 * 5. The medical step uses the neutral wording "Tell the provider about any medical requirements you declared."
 * 6. The assistance draft contains only ticked needs and never the note or emergency contact
 * 7. Non-owners (including the demo trip) get no needs fields in the trip, wallet JSON, print page, pack and messages
 * 8. Note over 300 characters and a bad phone are rejected without echoing the value
 * 9. The emergency phone never appears in log output
 * 10. Phone normalization (+91XXXXXXXXXX) and masking (+91******XXXX)
 */
@SpringBootTest
@AutoConfigureMockMvc
public class TravellerNeedsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private TripService tripService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity userA;
    private UserEntity userB;
    private LocalDateTime baseDate;

    @BeforeEach
    void setUp() {
        baseDate = LocalDateTime.of(2026, 10, 21, 10, 0);

        userA = userJpaRepository.findByEmail("traveller.a@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-TRAVELLER-A")
                        .email("traveller.a@example.com")
                        .passwordHash(passwordEncoder.encode("Secret123!"))
                        .name("Traveller A")
                        .phone("+919876543210")
                        .createdAt(LocalDateTime.now())
                        .build())
        );

        userB = userJpaRepository.findByEmail("traveller.b@example.com").orElseGet(() ->
                userJpaRepository.save(UserEntity.builder()
                        .id("USR-TRAVELLER-B")
                        .email("traveller.b@example.com")
                        .passwordHash(passwordEncoder.encode("Secret123!"))
                        .name("Traveller B")
                        .phone("+919876543211")
                        .createdAt(LocalDateTime.now())
                        .build())
        );
    }

    // =========================================================================
    // 1. FASTEST and CHEAPEST identical with and without needs flags
    // =========================================================================

    @Test
    @DisplayName("1. FASTEST and CHEAPEST identical with and without needs flags")
    void testFastestAndCheapestIdenticalWithAndWithoutNeedsFlags() {
        Booking disrupted = Booking.builder()
                .id("BKG-ORIG-1")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-101")
                .from("BLR")
                .to("GOI")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(2))
                .cost(new BigDecimal("5000.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("20000.00"))
                .mustArriveBy(baseDate.plusHours(10))
                .avoidOvernightTravel(true)
                .preferredModes(List.of(BookingType.FLIGHT, BookingType.CAB, BookingType.TRAIN, BookingType.BUS))
                .build();

        // Baseline trip without needs flags
        Trip baselineTrip = Trip.builder()
                .id("TRIP-BASE")
                .userId(userA.getId())
                .title("Trip Without Needs")
                .travellerName("Traveller A")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .constraints(constraints)
                .hasMobilityAssistance(false)
                .hasElderlyTraveller(false)
                .hasTravellingWithChildren(false)
                .hasMedicalNeeds(false)
                .build();

        // Needs trip with all 4 needs flags
        Trip needsTrip = Trip.builder()
                .id("TRIP-NEEDS")
                .userId(userA.getId())
                .title("Trip With Needs")
                .travellerName("Traveller A")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .constraints(constraints)
                .hasMobilityAssistance(true)
                .hasElderlyTraveller(true)
                .hasTravellingWithChildren(true)
                .hasMedicalNeeds(true)
                .build();

        // Candidate 1: Earliest arrival (12:30), cost 7000 (FASTEST)
        InventoryItem itemFast = InventoryItem.builder()
                .id("INV-FAST")
                .mode(BookingType.FLIGHT)
                .provider("Air India AI-101")
                .from("BLR")
                .to("GOI")
                .departure(baseDate.plusMinutes(30))
                .arrival(baseDate.plusHours(2).plusMinutes(30))
                .cost(new BigDecimal("7000.00"))
                .transfers(0)
                .reliabilityScore(85)
                .seatsAvailable(10)
                .build();

        // Candidate 2: Cheapest net cost (3000), arrives 18:00 (CHEAPEST)
        InventoryItem itemCheap = InventoryItem.builder()
                .id("INV-CHEAP")
                .mode(BookingType.BUS)
                .provider("KSRTC Sarige")
                .from("BLR")
                .to("GOI")
                .departure(baseDate.plusHours(1))
                .arrival(baseDate.plusHours(8))
                .cost(new BigDecimal("3000.00"))
                .transfers(0)
                .reliabilityScore(80)
                .seatsAvailable(20)
                .build();

        // Candidate 3: Balanced direct flight, cost 5500, arrives 14:00 (SAFEST contender)
        InventoryItem itemSafe = InventoryItem.builder()
                .id("INV-SAFE")
                .mode(BookingType.FLIGHT)
                .provider("Star Air S9-102")
                .from("BLR")
                .to("GOI")
                .departure(baseDate.plusHours(1))
                .arrival(baseDate.plusHours(4))
                .cost(new BigDecimal("5500.00"))
                .transfers(0)
                .reliabilityScore(95)
                .seatsAvailable(15)
                .build();

        List<InventoryItem> inventory = List.of(itemFast, itemCheap, itemSafe);

        RecoveryResponse baselineResp = scoringService.scoreAndRank(baselineTrip, disrupted, inventory);
        RecoveryResponse needsResp = scoringService.scoreAndRank(needsTrip, disrupted, inventory);

        assertNotNull(baselineResp);
        assertNotNull(needsResp);

        Option baseFastest = baselineResp.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.FASTEST).findFirst().orElseThrow();
        Option needsFastest = needsResp.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.FASTEST).findFirst().orElseThrow();
        assertEquals(baseFastest.getLegs().get(0).getId(), needsFastest.getLegs().get(0).getId(),
                "FASTEST option must be identical with or without needs flags");

        Option baseCheapest = baselineResp.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.CHEAPEST).findFirst().orElseThrow();
        Option needsCheapest = needsResp.getOptions().stream().filter(o -> o.getLabel() == OptionLabel.CHEAPEST).findFirst().orElseThrow();
        assertEquals(baseCheapest.getLegs().get(0).getId(), needsCheapest.getLegs().get(0).getId(),
                "CHEAPEST option must be identical with or without needs flags");

        // Verify explanation in constraintsMet contains real numbers
        boolean hasNeedsExplanation = needsResp.getOptions().stream()
                .anyMatch(o -> o.getConstraintsMet().stream().anyMatch(m -> m.toLowerCase().contains("traveller needs") && m.contains("pts")));
        assertTrue(hasNeedsExplanation, "Constraints met must explain applied traveller needs preference with points");
    }

    // =========================================================================
    // 2. No option labelled accessible
    // =========================================================================

    @Test
    @DisplayName("2. No option labelled accessible")
    void testNoOptionLabelledAccessible() {
        Booking disrupted = Booking.builder()
                .id("BKG-ORIG-2")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-101")
                .from("BLR")
                .to("GOI")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(2))
                .cost(new BigDecimal("5000.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Trip needsTrip = Trip.builder()
                .id("TRIP-NEEDS-ACC")
                .userId(userA.getId())
                .title("Trip With Needs")
                .travellerName("Traveller A")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .hasMobilityAssistance(true)
                .hasElderlyTraveller(true)
                .hasTravellingWithChildren(true)
                .hasMedicalNeeds(true)
                .build();

        InventoryItem item = InventoryItem.builder()
                .id("INV-ITEM-ACC")
                .mode(BookingType.FLIGHT)
                .provider("Air India AI-101")
                .from("BLR")
                .to("GOI")
                .departure(baseDate.plusMinutes(30))
                .arrival(baseDate.plusHours(2).plusMinutes(30))
                .cost(new BigDecimal("7000.00"))
                .transfers(0)
                .reliabilityScore(85)
                .seatsAvailable(10)
                .build();

        RecoveryResponse resp = scoringService.scoreAndRank(needsTrip, disrupted, List.of(item));

        for (Option opt : resp.getOptions()) {
            assertFalse(opt.getExplanation().toLowerCase().contains("accessible"),
                    "Option explanation must not claim accessibility");
            assertNotEquals("ACCESSIBLE", opt.getLabel() != null ? opt.getLabel().name() : "",
                    "Option label must never be ACCESSIBLE");
        }

        assertEquals("Accessibility information is not available in this catalogue. Please confirm with the operator.",
                resp.getAccessibilityDisclaimer());
    }

    // =========================================================================
    // 3. Checklist steps only when a flag is set
    // =========================================================================

    @Test
    @DisplayName("3. Checklist steps only when a flag is set")
    void testChecklistStepsOnlyWhenFlagIsSet() {
        Booking disrupted = Booking.builder()
                .id("BKG-TR-FLAG-TEST")
                .type(BookingType.TRAIN)
                .provider("IRCTC 12627")
                .from("SBC")
                .to("MAO")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(9))
                .cost(new BigDecimal("1200.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Option optSafest = Option.builder()
                .id("OPT-SAFEST-FLAG")
                .label(OptionLabel.SAFEST)
                .legs(List.of(Booking.builder()
                        .id("REP-FLAG-1")
                        .type(BookingType.TRAIN)
                        .provider("IRCTC Replacement")
                        .from("SBC")
                        .to("MAO")
                        .startTime(baseDate.plusHours(1))
                        .endTime(baseDate.plusHours(10))
                        .cost(new BigDecimal("1400.00"))
                        .build()))
                .arrivalTime(baseDate.plusHours(10))
                .totalCost(new BigDecimal("1400.00"))
                .build();

        // 1. Trip with NO needs flags set
        Trip tripNoFlags = Trip.builder()
                .id("TRIP-NO-FLAGS")
                .userId(userA.getId())
                .title("Trip With No Needs Flags")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .options(List.of(optSafest))
                .hasMobilityAssistance(false)
                .hasElderlyTraveller(false)
                .hasTravellingWithChildren(false)
                .hasMedicalNeeds(false)
                .build();
        tripRepository.save(tripNoFlags);

        RecoveryPlan planNoFlags = recoveryService.buildPlan(tripNoFlags.getId(), "OPT-SAFEST-FLAG");
        List<ActionChecklistItem> itemsNoFlags = planNoFlags.getChecklist();

        assertFalse(itemsNoFlags.stream().anyMatch(i -> i.getText().toLowerCase().contains("boarding")),
                "Boarding assistance must not appear when flags are not set");
        assertFalse(itemsNoFlags.stream().anyMatch(i -> i.getText().toLowerCase().contains("medical")),
                "Medical assistance must not appear when flags are not set");
        assertFalse(itemsNoFlags.stream().anyMatch(i -> i.getText().toLowerCase().contains("child")),
                "Child seating assistance must not appear when flags are not set");
        assertFalse(itemsNoFlags.stream().anyMatch(i -> i.getText().toLowerCase().contains("lift or ramp")),
                "Hotel lift/ramp assistance must not appear when flags are not set");

        // 2. Trip with mobility assistance ONLY
        Trip tripMobilityOnly = Trip.builder()
                .id("TRIP-MOBILITY-ONLY")
                .userId(userA.getId())
                .title("Trip Mobility Only")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .options(List.of(optSafest))
                .hasMobilityAssistance(true)
                .hasElderlyTraveller(false)
                .hasTravellingWithChildren(false)
                .hasMedicalNeeds(false)
                .build();
        tripRepository.save(tripMobilityOnly);

        RecoveryPlan planMobility = recoveryService.buildPlan(tripMobilityOnly.getId(), "OPT-SAFEST-FLAG");
        List<ActionChecklistItem> itemsMobility = planMobility.getChecklist();
        assertTrue(itemsMobility.stream().anyMatch(i -> i.getText().contains("request assistance for boarding")),
                "Boarding assistance must appear when hasMobilityAssistance=true");
        assertFalse(itemsMobility.stream().anyMatch(i -> i.getText().toLowerCase().contains("medical")),
                "Medical step must not appear when hasMedicalNeeds=false");
        assertFalse(itemsMobility.stream().anyMatch(i -> i.getText().toLowerCase().contains("child")),
                "Child step must not appear when hasTravellingWithChildren=false");

        // 3. Trip with children flag ONLY
        Trip tripChildrenOnly = Trip.builder()
                .id("TRIP-CHILDREN-ONLY")
                .userId(userA.getId())
                .title("Trip Children Only")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .options(List.of(optSafest))
                .hasMobilityAssistance(false)
                .hasElderlyTraveller(false)
                .hasTravellingWithChildren(true)
                .hasMedicalNeeds(false)
                .build();
        tripRepository.save(tripChildrenOnly);

        RecoveryPlan planChildren = recoveryService.buildPlan(tripChildrenOnly.getId(), "OPT-SAFEST-FLAG");
        List<ActionChecklistItem> itemsChildren = planChildren.getChecklist();
        assertTrue(itemsChildren.stream().anyMatch(i -> i.getText().contains("Request child/infant seating arrangements")),
                "Child seating must appear when hasTravellingWithChildren=true");
        assertFalse(itemsChildren.stream().anyMatch(i -> i.getText().toLowerCase().contains("boarding")),
                "Boarding step must not appear when mobility/elderly flags are false");
        assertFalse(itemsChildren.stream().anyMatch(i -> i.getText().toLowerCase().contains("medical")),
                "Medical step must not appear when hasMedicalNeeds=false");
    }

    // =========================================================================
    // 4. The hotel step only when a HOTEL booking exists
    // =========================================================================

    @Test
    @DisplayName("4. The hotel step only when a HOTEL booking exists")
    void testHotelStepOnlyWhenHotelBookingExists() {
        Booking transportBooking = Booking.builder()
                .id("BKG-TR-HTL-TEST")
                .type(BookingType.TRAIN)
                .provider("IRCTC 12627")
                .from("SBC")
                .to("MAO")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(9))
                .cost(new BigDecimal("1200.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Option optSafest = Option.builder()
                .id("OPT-SAFEST-HTL")
                .label(OptionLabel.SAFEST)
                .legs(List.of(Booking.builder()
                        .id("REP-HTL-1")
                        .type(BookingType.TRAIN)
                        .provider("IRCTC Replacement")
                        .from("SBC")
                        .to("MAO")
                        .startTime(baseDate.plusHours(1))
                        .endTime(baseDate.plusHours(10))
                        .cost(new BigDecimal("1400.00"))
                        .build()))
                .arrivalTime(baseDate.plusHours(10))
                .totalCost(new BigDecimal("1400.00"))
                .build();

        // 1. Trip WITHOUT hotel booking
        Trip tripWithoutHotel = Trip.builder()
                .id("TRIP-NO-HOTEL-BOOKING")
                .userId(userA.getId())
                .title("Trip No Hotel")
                .bookings(new ArrayList<>(List.of(transportBooking)))
                .options(List.of(optSafest))
                .hasMobilityAssistance(true)
                .hasMedicalNeeds(true)
                .build();
        tripRepository.save(tripWithoutHotel);

        RecoveryPlan planWithoutHotel = recoveryService.buildPlan(tripWithoutHotel.getId(), "OPT-SAFEST-HTL");
        assertFalse(planWithoutHotel.getChecklist().stream().anyMatch(i -> i.getText().contains("Confirm lift or ramp access at the hotel")),
                "Hotel assistance step must not appear without a HOTEL booking");

        // 2. Trip WITH hotel booking
        Booking hotelBooking = Booking.builder()
                .id("BKG-HOTEL-EXISTS")
                .type(BookingType.HOTEL)
                .provider("Taj Holiday Village")
                .from("Candolim, Goa")
                .to("Candolim, Goa")
                .startTime(baseDate.plusHours(12))
                .endTime(baseDate.plusDays(2))
                .cost(new BigDecimal("8000.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        Trip tripWithHotel = Trip.builder()
                .id("TRIP-WITH-HOTEL-BOOKING")
                .userId(userA.getId())
                .title("Trip With Hotel")
                .bookings(new ArrayList<>(List.of(transportBooking, hotelBooking)))
                .options(List.of(optSafest))
                .hasMobilityAssistance(true)
                .hasMedicalNeeds(true)
                .build();
        tripRepository.save(tripWithHotel);

        RecoveryPlan planWithHotel = recoveryService.buildPlan(tripWithHotel.getId(), "OPT-SAFEST-HTL");
        assertTrue(planWithHotel.getChecklist().stream().anyMatch(i -> i.getText().contains("Confirm lift or ramp access at the hotel")),
                "Hotel assistance step must appear when trip has a HOTEL booking and needs flags");
    }

    // =========================================================================
    // 5. The medical step uses the neutral wording "Tell the provider about any medical requirements you declared."
    // =========================================================================

    @Test
    @DisplayName("5. The medical step uses the neutral wording 'Tell the provider about any medical requirements you declared.'")
    void testMedicalStepUsesNeutralWording() {
        Booking disrupted = Booking.builder()
                .id("BKG-MED-TEST")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-101")
                .from("BLR")
                .to("GOI")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(2))
                .cost(new BigDecimal("5000.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        Option optSafest = Option.builder()
                .id("OPT-SAFEST-MED")
                .label(OptionLabel.SAFEST)
                .legs(List.of(Booking.builder()
                    .id("REP-MED-1")
                    .type(BookingType.FLIGHT)
                    .provider("Star Air S9-102")
                    .from("BLR")
                    .to("GOI")
                    .startTime(baseDate.plusHours(1))
                    .endTime(baseDate.plusHours(4))
                    .cost(new BigDecimal("5500.00"))
                    .build()))
                .arrivalTime(baseDate.plusHours(4))
                .totalCost(new BigDecimal("5500.00"))
                .build();

        Trip trip = Trip.builder()
                .id("TRIP-MED-EXACT")
                .userId(userA.getId())
                .title("Trip Medical Neutral")
                .bookings(new ArrayList<>(List.of(disrupted)))
                .options(List.of(optSafest))
                .hasMedicalNeeds(true)
                .build();
        tripRepository.save(trip);

        RecoveryPlan plan = recoveryService.buildPlan(trip.getId(), "OPT-SAFEST-MED");
        List<ActionChecklistItem> items = plan.getChecklist();

        ActionChecklistItem medItem = items.stream()
                .filter(i -> i.getText().contains("medical requirements"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Medical checklist item not found"));

        assertEquals("Tell the provider about any medical requirements you declared.", medItem.getText());
        assertFalse(medItem.getText().toLowerCase().contains("equipment"));
        assertFalse(medItem.getText().toLowerCase().contains("medication"));
        assertFalse(medItem.getText().toLowerCase().contains("wheelchair"));
        assertFalse(medItem.getText().toLowerCase().contains("oxygen"));
    }

    // =========================================================================
    // 6. The assistance draft contains only ticked needs and never the note or emergency contact
    // =========================================================================

    @Test
    @DisplayName("6. The assistance draft contains only ticked needs and never the note or emergency contact")
    void testAssistanceDraftContainsOnlyTickedNeedsAndNeverNoteOrEmergencyContact() throws Exception {
        Trip trip = Trip.builder()
                .id("TRIP-DRAFT-1")
                .userId(userA.getId())
                .title("Assistance Request Test")
                .travellerName("Rohan Verma")
                .travellerPhone("+919876543210")
                .hasMobilityAssistance(true)
                .hasElderlyTraveller(false)
                .hasTravellingWithChildren(false)
                .hasMedicalNeeds(true)
                .specialNeedsNote("Super confidential private diagnosis and note")
                .emergencyContactName("Dr. Anita Gupta")
                .emergencyContactPhone("+919123456789")
                .build();
        trip = tripRepository.save(trip);

        DraftMessageRequest request = DraftMessageRequest.builder()
                .recipientType(RecipientType.OPERATOR)
                .draftKind(DraftKind.ASSISTANCE_REQUEST)
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/" + trip.getId() + "/messages/draft")
                        .with(user(userA.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String body = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("body").asText();
        String status = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("status").asText();
        String draftKind = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("draftKind").asText();

        assertEquals("DRAFT", status, "Message draft must remain DRAFT until user confirms sending");
        assertEquals("ASSISTANCE_REQUEST", draftKind);

        // Ticked needs must be included
        assertTrue(body.contains("Mobility assistance / wheelchair boarding support"), "Should include ticked mobility need");
        assertTrue(body.contains("Medical requirements accommodation"), "Should include ticked medical need");

        // Unticked needs must not be included
        assertFalse(body.contains("Senior traveller assistance"), "Should not include unticked senior need");
        assertFalse(body.contains("Travelling with children"), "Should not include unticked children need");

        // Private note must NEVER appear in the generated draft body
        assertFalse(body.contains("Super confidential"), "Draft must NEVER include the special needs note");
        assertFalse(body.contains("private diagnosis"), "Draft must NEVER include the special needs note");

        // Emergency contact name and phone must NEVER appear in the generated draft body
        assertFalse(body.contains("Dr. Anita Gupta"), "Draft must NEVER include emergency contact name");
        assertFalse(body.contains("9123456789"), "Draft must NEVER include emergency contact phone");
    }

    // =========================================================================
    // 7. Non-owners (including the demo trip) get no needs fields in trip, wallet, print, pack, messages
    // =========================================================================

    @Test
    @DisplayName("7. Non-owners (including demo trip) get no needs fields across trip, wallet, print, pack, and messages")
    void testNonOwnersAndDemoTripGetNoNeedsFieldsAcrossTripWalletPrintPackMessages() throws Exception {
        Booking initBooking = Booking.builder()
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-101")
                .from("BLR")
                .to("GOI")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(2))
                .cost(new BigDecimal("4000.00"))
                .build();

        CreateTripRequest req = CreateTripRequest.builder()
                .title("Confidential Journey")
                .travellerName("Traveller A")
                .travellerPhone("9876543210")
                .hasMobilityAssistance(true)
                .hasElderlyTraveller(true)
                .hasTravellingWithChildren(true)
                .hasMedicalNeeds(true)
                .specialNeedsNote("Private health note: patient needs quiet space")
                .emergencyContactName("A Emergency Contact")
                .emergencyContactPhone("9876543210")
                .constraints(Constraints.builder()
                        .maxBudget(new BigDecimal("10000.00"))
                        .mustArriveBy(baseDate.plusHours(6))
                        .build())
                .bookings(List.of(initBooking))
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/trips")
                        .with(user(userA.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String tripId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 1. Owner (User A) requests GET /api/trips/{tripId} -> Sees needs fields
        mockMvc.perform(get("/api/trips/" + tripId)
                        .with(user(userA.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMobilityAssistance").value(true))
                .andExpect(jsonPath("$.data.specialNeedsNote").value("Private health note: patient needs quiet space"))
                .andExpect(jsonPath("$.data.emergencyContactName").value("A Emergency Contact"));

        // 2. Non-owner (User B) is forbidden across trip, wallet, print, pack, and messages
        mockMvc.perform(get("/api/trips/" + tripId)
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trips/" + tripId + "/wallet")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trips/" + tripId + "/wallet/print")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trips/" + tripId + "/wallet/pack")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/trips/" + tripId + "/messages")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        // 3. Shared demo trip TRIP-DEMO-001 (unowned) -> readable by anyone, but ALL needs fields STRIPPED
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMobilityAssistance").value(false))
                .andExpect(jsonPath("$.data.hasMedicalNeeds").value(false))
                .andExpect(jsonPath("$.data.specialNeedsNote").doesNotExist())
                .andExpect(jsonPath("$.data.emergencyContactName").doesNotExist())
                .andExpect(jsonPath("$.data.emergencyContactPhone").doesNotExist());

        // 4. Wallet JSON for demo trip -> no emergency contact info
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/wallet")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMobilityAssistance").value(false))
                .andExpect(jsonPath("$.data.emergencyContactName").doesNotExist())
                .andExpect(jsonPath("$.data.emergencyContactPhone").doesNotExist());

        // 5. Wallet Print on demo trip even with includeEmergency=true -> NO emergency contact box
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/wallet/print?includeEmergency=true")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Emergency Contact:"))));

        // 6. Offline Pack on demo trip even with includeEmergency=true -> NO emergency contact box
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/wallet/pack?includeEmergency=true")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Emergency Contact:"))));

        // 7. Messages on demo trip -> 200 OK, no private needs in messages
        mockMvc.perform(get("/api/trips/TRIP-DEMO-001/messages")
                        .with(user(userB.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Private health note"))))
                .andExpect(content().string(not(containsString("Emergency Contact"))));

        // 8. Owner Wallet Print with includeEmergency=true -> shows emergency contact box with masked phone
        mockMvc.perform(get("/api/trips/" + tripId + "/wallet/print?includeEmergency=true")
                        .with(user(userA.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Emergency Contact:")))
                .andExpect(content().string(containsString("A Emergency Contact")))
                .andExpect(content().string(containsString("+91******3210")));

        // 9. Owner Offline Pack with includeEmergency=true -> shows emergency contact box with masked phone
        mockMvc.perform(get("/api/trips/" + tripId + "/wallet/pack?includeEmergency=true")
                        .with(user(userA.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Emergency Contact:")))
                .andExpect(content().string(containsString("A Emergency Contact")))
                .andExpect(content().string(containsString("+91******3210")));
    }

    // =========================================================================
    // 8. Note over 300 characters and bad phone rejected without echoing value
    // =========================================================================

    @Test
    @DisplayName("8. Note over 300 characters and a bad phone are rejected without echoing the value")
    void testNoteOver300AndBadPhoneRejectedWithoutEchoingValue() throws Exception {
        Booking initBooking = Booking.builder()
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-101")
                .from("BLR")
                .to("GOI")
                .startTime(baseDate)
                .endTime(baseDate.plusHours(2))
                .cost(new BigDecimal("4000.00"))
                .build();

        // 1. Note over 300 characters
        String longNote = "A".repeat(301);
        CreateTripRequest reqLongNote = CreateTripRequest.builder()
                .title("Long Note Trip")
                .travellerName("Traveller A")
                .travellerPhone("9876543210")
                .specialNeedsNote(longNote)
                .constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(baseDate.plusHours(6)).build())
                .bookings(List.of(initBooking))
                .build();

        MvcResult resLongNote = mockMvc.perform(post("/api/trips")
                        .with(user(userA.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLongNote)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String errBodyLongNote = resLongNote.getResponse().getContentAsString();
        assertTrue(errBodyLongNote.contains("Special needs note must not exceed 300 characters"));

        // 2. Bad emergency phone via API
        String secretBadPhone = "BAD_PHONE_SECRET_999";
        CreateTripRequest reqBadPhone = CreateTripRequest.builder()
                .title("Bad Phone Trip")
                .travellerName("Traveller A")
                .travellerPhone("9876543210")
                .emergencyContactPhone(secretBadPhone)
                .constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(baseDate.plusHours(6)).build())
                .bookings(List.of(initBooking))
                .build();

        MvcResult resBadPhone = mockMvc.perform(post("/api/trips")
                        .with(user(userA.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBadPhone)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String errBodyBadPhone = resBadPhone.getResponse().getContentAsString();
        assertTrue(errBodyBadPhone.contains("Emergency contact phone must be a valid 10-digit Indian phone number"));
        assertFalse(errBodyBadPhone.contains(secretBadPhone), "Error response must NEVER echo the entered phone value");

        // 3. Direct EmergencyContactUtil verification
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                EmergencyContactUtil.normalizePhone(secretBadPhone)
        );
        assertFalse(ex.getMessage().contains(secretBadPhone), "Exception message must NEVER echo the invalid phone input");
    }

    // =========================================================================
    // 9. The emergency phone never appears in log output
    // =========================================================================

    @Test
    @DisplayName("9. The emergency phone never appears in log output")
    void testEmergencyPhoneNeverAppearsInLogOutput() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        String rawSecretPhone = "9988776655";
        String normalizedPhone = "+919988776655";

        try {
            Booking booking = Booking.builder()
                    .id("BKG-LOG-1")
                    .type(BookingType.CAB)
                    .provider("Uber India")
                    .from("Koramangala")
                    .to("BLR Airport")
                    .startTime(baseDate)
                    .endTime(baseDate.plusHours(1))
                    .cost(new BigDecimal("1000.00"))
                    .status(BookingStatus.CONFIRMED)
                    .build();

            CreateTripRequest req = CreateTripRequest.builder()
                    .title("Trip For Log Privacy Check")
                    .travellerName("Privacy Tester")
                    .travellerPhone("9876543210")
                    .hasMobilityAssistance(true)
                    .emergencyContactName("Private Contact")
                    .emergencyContactPhone(rawSecretPhone)
                    .specialNeedsNote("Private health details")
                    .constraints(Constraints.builder().maxBudget(new BigDecimal("5000")).mustArriveBy(baseDate.plusHours(5)).build())
                    .bookings(List.of(booking))
                    .build();

            Trip created = tripService.createTrip(req);
            tripService.getTripById(created.getId());
            tripRepository.findById(created.getId());

            List<ch.qos.logback.classic.spi.ILoggingEvent> logs = listAppender.list;
            for (ch.qos.logback.classic.spi.ILoggingEvent event : logs) {
                String msg = event.getFormattedMessage();
                assertFalse(msg.contains(rawSecretPhone), "Raw emergency phone must never appear in log messages");
                assertFalse(msg.contains(normalizedPhone), "Normalized emergency phone must never appear in log messages");
                assertFalse(msg.contains("Private health details"), "Special needs note must never appear in log messages");
            }
        } finally {
            rootLogger.detachAppender(listAppender);
        }
    }

    // =========================================================================
    // 10. Emergency phone normalization and masking
    // =========================================================================

    @Test
    @DisplayName("10. Emergency phone normalization and masking")
    void testEmergencyPhoneNormalizationAndMasking() {
        // Standard 10-digit number
        assertEquals("+919876543210", EmergencyContactUtil.normalizePhone("9876543210"));

        // Number with leading 0
        assertEquals("+919876543210", EmergencyContactUtil.normalizePhone("09876543210"));

        // Number with leading 91
        assertEquals("+919876543210", EmergencyContactUtil.normalizePhone("919876543210"));

        // Number with +91 and spaces/dashes
        assertEquals("+919876543210", EmergencyContactUtil.normalizePhone("+91 98765-43210"));

        // Masking
        assertEquals("+91******3210", EmergencyContactUtil.maskPhone("+919876543210"));
        assertEquals("-", EmergencyContactUtil.maskPhone(null));
    }
}
