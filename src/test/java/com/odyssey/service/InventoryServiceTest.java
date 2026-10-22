package com.odyssey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.odyssey.config.RecoveryProperties;
import com.odyssey.model.Booking;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        RecoveryProperties recoveryProperties = new RecoveryProperties();
        inventoryService = new InventoryService(objectMapper, recoveryProperties);
        inventoryService.init();
    }

    @Test
    @DisplayName("Startup logging: loads at least 11 items from inventory.json")
    void testInventoryLoading() {
        List<InventoryItem> all = inventoryService.getAll();
        assertNotNull(all);
        assertTrue(all.size() >= 11, "inventory.json should contain at least 11 entries");
    }

    @Test
    @DisplayName("Normalised city key helper maps Bengaluru, Mangaluru, Hubballi, and Goa aliases")
    void testCityNormalisation() {
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("BLR (Kempegowda International Airport, Bengaluru)"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("BLR"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("Bengaluru"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("Bangalore"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("Bengaluru (Kempegowda Bus Station, Majestic)"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("Bengaluru (KSR Bengaluru City Junction)"));
        assertEquals("BENGALURU", InventoryService.normalizeCityKey("Bengaluru (Yesvantpur Junction)"));

        assertEquals("MANGALURU", InventoryService.normalizeCityKey("IXE (Mangaluru International Airport)"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("IXE (Mangaluru Airport)"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("IXE"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("Mangaluru"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("Mangalore"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("Mangaluru (KSRTC Bus Stand, Bejai)"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("Mangaluru (Mangaluru Central Railway Station)"));
        assertEquals("MANGALURU", InventoryService.normalizeCityKey("Mangaluru (Mangaluru Junction Railway Station)"));

        assertEquals("HUBBALLI", InventoryService.normalizeCityKey("Hubballi Junction (UBL)"));
        assertEquals("GOA", InventoryService.normalizeCityKey("Dabolim Airport, Goa (GOI)"));
    }

    @Test
    @DisplayName("Given demo route BLR -> IXE, InventoryService returns at least 4 candidates")
    void testDemoTripCancelledReturnsAtLeastFourCandidates() {
        Booking disruptedFlight = Booking.builder()
                .id("BKG-101")
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-534")
                .from("BLR (Kempegowda International Airport, Bengaluru)")
                .to("IXE (Mangaluru International Airport)")
                .status(BookingStatus.CANCELLED)
                .build();

        List<InventoryItem> candidates = inventoryService.search(disruptedFlight.getFrom(), disruptedFlight.getTo());

        assertNotNull(candidates);
        assertTrue(candidates.size() >= 4, "InventoryService should return at least 4 candidates for recovery route, found: " + candidates.size());

        // Verify presence of: an IndiGo flight, a day KSRTC bus, a day train, and an overnight bus
        boolean hasIndiGoFlight = candidates.stream()
                .anyMatch(c -> c.getMode() == BookingType.FLIGHT && c.getProvider().contains("IndiGo"));
        boolean hasDayKsrtcBus = candidates.stream()
                .anyMatch(c -> c.getMode() == BookingType.BUS && c.getProvider().contains("KSRTC") && c.getProvider().contains("Day"));
        boolean hasDayTrain = candidates.stream()
                .anyMatch(c -> c.getMode() == BookingType.TRAIN);
        boolean hasOvernightBus = candidates.stream()
                .anyMatch(c -> c.getMode() == BookingType.BUS && c.getProvider().contains("Overnight"));

        assertTrue(hasIndiGoFlight, "Candidates must include an IndiGo flight");
        assertTrue(hasDayKsrtcBus, "Candidates must include a day KSRTC bus");
        assertTrue(hasDayTrain, "Candidates must include a day train");
        assertTrue(hasOvernightBus, "Candidates must include an overnight bus");
    }

    @Test
    @DisplayName("Generic recovery search on routes: real inventory vs synthetic fallback")
    void testGenericRecoverySearchThreeRoutes() {
        // Route 1: BLR -> IXE (Real inventory)
        List<InventoryItem> route1 = inventoryService.search("Bengaluru", "Mangaluru");
        assertFalse(route1.isEmpty());
        assertTrue(route1.stream().allMatch(c -> "INVENTORY".equalsIgnoreCase(c.getSource())));

        // Route 2: BOM -> PNQ (Real inventory)
        List<InventoryItem> route2 = inventoryService.search("Mumbai", "Pune");
        assertFalse(route2.isEmpty());
        assertTrue(route2.stream().allMatch(c -> "INVENTORY".equalsIgnoreCase(c.getSource())));

        // Route 3: Hubballi -> Goa (No inventory in inventory.json -> Synthetic fallback)
        LocalDateTime now = LocalDateTime.now();
        List<InventoryItem> route3 = inventoryService.search("Hubballi", "Goa", now);
        assertFalse(route3.isEmpty());
        assertTrue(route3.size() >= 3, "Synthetic fallback should generate at least 3 options");
        assertTrue(route3.stream().allMatch(c -> "ESTIMATED".equalsIgnoreCase(c.getSource())),
                "All synthetic options must have source = ESTIMATED");
        assertTrue(route3.stream().allMatch(c -> c.getReliabilityScore() == 60),
                "Synthetic options must use configured synthetic reliability score (60)");
    }
}
