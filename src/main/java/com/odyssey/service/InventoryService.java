package com.odyssey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.RecoveryProperties;
import com.odyssey.model.CityInfo;
import com.odyssey.model.InventoryItem;
import com.odyssey.model.enums.BookingType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ObjectMapper objectMapper;
    private final RecoveryProperties recoveryProperties;

    private List<InventoryItem> inventoryItems = new ArrayList<>();
    private static final List<CityInfo> CITIES = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadCities();
        loadInventory();
    }

    public synchronized void loadCities() {
        try {
            ClassPathResource resource = new ClassPathResource("cities.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    List<CityInfo> loaded = objectMapper.readValue(is, new TypeReference<List<CityInfo>>() {});
                    if (loaded != null && !loaded.isEmpty()) {
                        synchronized (CITIES) {
                            CITIES.clear();
                            CITIES.addAll(loaded);
                        }
                        log.info("Successfully loaded {} cities from cities.json", CITIES.size());
                    }
                }
            } else {
                log.warn("cities.json not found on classpath!");
            }
        } catch (Exception e) {
            log.warn("Failed to parse cities.json: {}", e.getMessage(), e);
        }
    }

    public synchronized void loadInventory() {
        try {
            ClassPathResource resource = new ClassPathResource("inventory.json");
            if (!resource.exists()) {
                log.warn("inventory.json not found on classpath!");
                inventoryItems = Collections.emptyList();
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                inventoryItems = objectMapper.readValue(is, new TypeReference<List<InventoryItem>>() {});
                if (inventoryItems == null || inventoryItems.isEmpty()) {
                    log.warn("inventory.json loaded but contains 0 entries!");
                    inventoryItems = Collections.emptyList();
                } else {
                    log.info("Successfully loaded {} inventory items from inventory.json", inventoryItems.size());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse or load inventory.json: {}", e.getMessage(), e);
            inventoryItems = Collections.emptyList();
        }
    }

    public List<InventoryItem> getAll() {
        return Collections.unmodifiableList(inventoryItems);
    }

    public static CityInfo findCity(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String upper = location.toUpperCase().trim();

        // 1. Direct match on key or name
        for (CityInfo city : CITIES) {
            if (upper.equals(city.getKey()) || upper.equalsIgnoreCase(city.getName())) {
                return city;
            }
            if (city.getAirportCodes() != null && city.getAirportCodes().contains(upper)) {
                return city;
            }
            if (city.getStationCodes() != null && city.getStationCodes().contains(upper)) {
                return city;
            }
            if (city.getAliases() != null && city.getAliases().contains(upper)) {
                return city;
            }
        }

        // 2. Substring matching for aliases or codes
        for (CityInfo city : CITIES) {
            if (city.getAliases() != null) {
                for (String alias : city.getAliases()) {
                    if (upper.contains(alias.toUpperCase())) {
                        return city;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Normalises various airport, railway station, bus stand, and city names into a canonical city key.
     */
    public static String normalizeCityKey(String location) {
        if (location == null || location.isBlank()) {
            return "UNKNOWN";
        }
        CityInfo city = findCity(location);
        if (city != null) {
            return city.getKey();
        }

        String upper = location.toUpperCase();
        if (upper.contains("IXE") || upper.contains("MANGALURU") || upper.contains("MANGALORE") || upper.contains("BEJAI")) {
            return "MANGALURU";
        }
        if (upper.contains("BLR") || upper.contains("BENGALURU") || upper.contains("BANGALORE")
                || upper.contains("KEMPEGOWDA") || upper.contains("YESVANTPUR") || upper.contains("MAJESTIC")
                || upper.contains("KSR BENGALURU") || upper.contains("KSR CITY")) {
            return "BENGALURU";
        }
        if (upper.contains("GOI") || upper.contains("GOA") || upper.contains("DABOLIM")) {
            return "GOA";
        }
        if (upper.contains("MYQ") || upper.contains("MYSURU") || upper.contains("MYSORE")) {
            return "MYSURU";
        }
        if (upper.contains("UDUPI")) {
            return "UDUPI";
        }
        if (upper.contains("HUBBALLI") || upper.contains("HUBLI")) {
            return "HUBBALLI";
        }

        String cleaned = upper.replaceAll("\\(.*\\)", "").replaceAll("[^A-Z0-9 ]", "").trim();
        return cleaned.isEmpty() ? upper.trim() : cleaned;
    }

    public static double calculateDistance(CityInfo c1, CityInfo c2) {
        if (c1 == null || c2 == null || (c1.getLatitude() == 0 && c1.getLongitude() == 0)
                || (c2.getLatitude() == 0 && c2.getLongitude() == 0)) {
            return 320.0; // Sensible default distance in km
        }
        final double R = 6371.0; // Earth radius in km
        double latDistance = Math.toRadians(c2.getLatitude() - c1.getLatitude());
        double lonDistance = Math.toRadians(c2.getLongitude() - c1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(c1.getLatitude())) * Math.cos(Math.toRadians(c2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.max(30.0, R * c);
    }

    public List<InventoryItem> search(String origin, String destination) {
        return search(origin, destination, null);
    }

    public List<InventoryItem> search(String origin, String destination, LocalDateTime baseTime) {
        String originKey = normalizeCityKey(origin);
        String destinationKey = normalizeCityKey(destination);

        List<InventoryItem> real = inventoryItems.stream()
                .filter(item -> normalizeCityKey(item.getFrom()).equals(originKey))
                .filter(item -> normalizeCityKey(item.getTo()).equals(destinationKey))
                .collect(Collectors.toList());

        if (!real.isEmpty()) {
            return real;
        }

        // Route lacks real inventory, generate deterministic synthetic inventory items
        log.info("No real inventory for route {} -> {}. Generating synthetic options (source=ESTIMATED).", originKey, destinationKey);
        return generateSyntheticItems(origin, destination, baseTime);
    }

    private List<InventoryItem> generateSyntheticItems(String origin, String destination, LocalDateTime baseTime) {
        CityInfo c1 = findCity(origin);
        CityInfo c2 = findCity(destination);

        String fromName = c1 != null ? c1.getName() : origin;
        String toName = c2 != null ? c2.getName() : destination;
        double distanceKm = calculateDistance(c1, c2);

        LocalDateTime departureBase = baseTime != null ? baseTime : LocalDateTime.now();
        int syntheticRelScore = (int) Math.round(recoveryProperties.getSyntheticReliabilityScore());

        List<InventoryItem> synthetic = new ArrayList<>();

        int searchDays = recoveryProperties != null && recoveryProperties.getSearchWindowDays() > 0
                ? recoveryProperties.getSearchWindowDays() : 2;

        int trainMinutes = Math.max(120, (int) Math.round((distanceKm / 60.0) * 60));
        BigDecimal trainCost = BigDecimal.valueOf(Math.max(450, Math.round(distanceKm * 2.2)))
                .setScale(2, RoundingMode.HALF_UP);

        int busMinutes = Math.max(150, (int) Math.round((distanceKm / 45.0) * 60));
        BigDecimal busCost = BigDecimal.valueOf(Math.max(350, Math.round(distanceKm * 1.6)))
                .setScale(2, RoundingMode.HALF_UP);

        boolean hasAirports = c1 != null && c2 != null && !c1.getAirportCodes().isEmpty() && !c2.getAirportCodes().isEmpty();
        int cabMinutes = Math.max(90, (int) Math.round((distanceKm / 55.0) * 60));
        BigDecimal cabCost = BigDecimal.valueOf(Math.max(1200, Math.round(distanceKm * 14.0)))
                .setScale(2, RoundingMode.HALF_UP);

        int flightMinutes = 70;
        BigDecimal flightCost = BigDecimal.valueOf(Math.max(3500, Math.round(distanceKm * 7.5)))
                .setScale(2, RoundingMode.HALF_UP);

        java.util.Set<String> addedKeys = new java.util.HashSet<>();

        // 1. Immediate relative slots for Day 0 (shortly after departureBase)
        addSyntheticItemIfValid(synthetic, addedKeys, departureBase, departureBase.plusMinutes(45), trainMinutes, trainCost,
                BookingType.TRAIN, fromName + " - " + toName + " SF Express (Estimated)",
                fromName + " Railway Station", toName + " Railway Station", 20, syntheticRelScore, 0);

        addSyntheticItemIfValid(synthetic, addedKeys, departureBase, departureBase.plusMinutes(30), busMinutes, busCost,
                BookingType.BUS, fromName + " - " + toName + " Express Bus (Estimated)",
                fromName + " Bus Stand", toName + " Bus Stand", 24, syntheticRelScore, 0);

        if (distanceKm > 200 && hasAirports) {
            addSyntheticItemIfValid(synthetic, addedKeys, departureBase, departureBase.plusMinutes(75), flightMinutes, flightCost,
                    BookingType.FLIGHT, fromName + " - " + toName + " Air Connect (Estimated)",
                    fromName + " Airport (" + c1.getAirportCodes().get(0) + ")", toName + " Airport (" + c2.getAirportCodes().get(0) + ")",
                    6, syntheticRelScore, 0);
        } else {
            addSyntheticItemIfValid(synthetic, addedKeys, departureBase, departureBase.plusMinutes(15), cabMinutes, cabCost,
                    BookingType.CAB, fromName + " - " + toName + " Intercity Cab (Estimated)",
                    fromName, toName, 4, syntheticRelScore, 0);
        }

        // 2. Fixed timetable slots across searchWindowDays (Day 0 and following day)
        java.time.LocalTime[] trainSlots = new java.time.LocalTime[]{
                java.time.LocalTime.of(8, 0),
                java.time.LocalTime.of(14, 0),
                java.time.LocalTime.of(19, 54)
        };
        // 20:24 departure with 150m duration arrives at exactly 22:54 (outside day-travel window)
        java.time.LocalTime[] busSlots = new java.time.LocalTime[]{
                java.time.LocalTime.of(7, 30),
                java.time.LocalTime.of(12, 30),
                java.time.LocalTime.of(16, 30),
                java.time.LocalTime.of(20, 24)
        };
        java.time.LocalTime[] cabSlots = new java.time.LocalTime[]{
                java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(15, 0),
                java.time.LocalTime.of(18, 30)
        };
        java.time.LocalTime[] flightSlots = new java.time.LocalTime[]{
                java.time.LocalTime.of(10, 0),
                java.time.LocalTime.of(17, 30)
        };

        for (int day = 0; day < searchDays; day++) {
            java.time.LocalDate targetDate = departureBase.toLocalDate().plusDays(day);

            for (java.time.LocalTime t : trainSlots) {
                LocalDateTime dep = LocalDateTime.of(targetDate, t);
                addSyntheticItemIfValid(synthetic, addedKeys, departureBase, dep, trainMinutes, trainCost,
                        BookingType.TRAIN, fromName + " - " + toName + " SF Express (Estimated)",
                        fromName + " Railway Station", toName + " Railway Station", 20, syntheticRelScore, 0);
            }

            for (java.time.LocalTime t : busSlots) {
                LocalDateTime dep = LocalDateTime.of(targetDate, t);
                addSyntheticItemIfValid(synthetic, addedKeys, departureBase, dep, busMinutes, busCost,
                        BookingType.BUS, fromName + " - " + toName + " Express Bus (Estimated)",
                        fromName + " Bus Stand", toName + " Bus Stand", 24, syntheticRelScore, 0);
            }

            if (distanceKm > 200 && hasAirports) {
                for (java.time.LocalTime t : flightSlots) {
                    LocalDateTime dep = LocalDateTime.of(targetDate, t);
                    addSyntheticItemIfValid(synthetic, addedKeys, departureBase, dep, flightMinutes, flightCost,
                            BookingType.FLIGHT, fromName + " - " + toName + " Air Connect (Estimated)",
                            fromName + " Airport (" + c1.getAirportCodes().get(0) + ")", toName + " Airport (" + c2.getAirportCodes().get(0) + ")",
                            6, syntheticRelScore, 0);
                }
            } else {
                for (java.time.LocalTime t : cabSlots) {
                    LocalDateTime dep = LocalDateTime.of(targetDate, t);
                    addSyntheticItemIfValid(synthetic, addedKeys, departureBase, dep, cabMinutes, cabCost,
                            BookingType.CAB, fromName + " - " + toName + " Intercity Cab (Estimated)",
                            fromName, toName, 4, syntheticRelScore, 0);
                }
            }
        }

        return synthetic;
    }

    private void addSyntheticItemIfValid(List<InventoryItem> synthetic, java.util.Set<String> addedKeys,
                                         LocalDateTime earliestAllowed, LocalDateTime departure, int durationMinutes,
                                         BigDecimal cost, BookingType mode, String provider, String from, String to,
                                         int seats, int relScore, int transfers) {
        if (departure.isBefore(earliestAllowed)) {
            return;
        }
        String key = mode.name() + "_" + departure.toString();
        if (addedKeys.contains(key)) {
            return;
        }
        addedKeys.add(key);

        LocalDateTime arrival = departure.plusMinutes(durationMinutes);
        String prefix = switch (mode) {
            case TRAIN -> "SYN-TR-";
            case BUS -> "SYN-BS-";
            case CAB -> "SYN-CB-";
            case FLIGHT -> "SYN-FL-";
            default -> "SYN-OT-";
        };

        synthetic.add(InventoryItem.builder()
                .id(prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .mode(mode)
                .provider(provider)
                .from(from)
                .to(to)
                .departure(departure)
                .arrival(arrival)
                .cost(cost)
                .transfers(transfers)
                .seatsAvailable(seats)
                .reliabilityScore(relScore)
                .source("ESTIMATED")
                .build());
    }
}
