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
import java.util.regex.Pattern;
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

    public static List<CityInfo> getAllCities() {
        synchronized (CITIES) {
            return new ArrayList<>(CITIES);
        }
    }

    public static String stripAccents(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    public static List<CityInfo> searchCities(String query) {
        if (query == null || query.isBlank()) {
            synchronized (CITIES) {
                return CITIES.stream().limit(20).collect(Collectors.toList());
            }
        }

        String normQ = stripAccents(query).trim().toUpperCase();
        List<CityInfo> prefixNameMatches = new ArrayList<>();
        List<CityInfo> prefixAliasMatches = new ArrayList<>();
        List<CityInfo> substringMatches = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        synchronized (CITIES) {
            for (CityInfo c : CITIES) {
                String cName = stripAccents(c.getName()).toUpperCase();
                String cKey = c.getKey().toUpperCase();

                if (cName.startsWith(normQ) || cKey.startsWith(normQ)) {
                    if (seenKeys.add(c.getKey())) {
                        prefixNameMatches.add(c);
                    }
                    continue;
                }

                boolean aliasPrefix = false;
                if (c.getAliases() != null) {
                    for (String a : c.getAliases()) {
                        if (stripAccents(a).toUpperCase().startsWith(normQ)) {
                            aliasPrefix = true;
                            break;
                        }
                    }
                }
                if (aliasPrefix) {
                    if (seenKeys.add(c.getKey())) {
                        prefixAliasMatches.add(c);
                    }
                    continue;
                }

                boolean sub = cName.contains(normQ) || cKey.contains(normQ);
                if (!sub && c.getAliases() != null) {
                    for (String a : c.getAliases()) {
                        if (stripAccents(a).toUpperCase().contains(normQ)) {
                            sub = true;
                            break;
                        }
                    }
                }
                if (sub && seenKeys.add(c.getKey())) {
                    substringMatches.add(c);
                }
            }
        }

        List<CityInfo> combined = new ArrayList<>();
        combined.addAll(prefixNameMatches);
        combined.addAll(prefixAliasMatches);
        combined.addAll(substringMatches);

        return combined.stream().limit(20).collect(Collectors.toList());
    }

    public static List<CityInfo> findClosestCities(String query) {
        return findClosestCities(query, 3);
    }

    public static List<CityInfo> findClosestCities(String query, int max) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String normQ = stripAccents(query).trim().toLowerCase();

        class ScoredCity implements Comparable<ScoredCity> {
            final CityInfo city;
            final int distance;

            ScoredCity(CityInfo city, int distance) {
                this.city = city;
                this.distance = distance;
            }

            @Override
            public int compareTo(ScoredCity o) {
                return Integer.compare(this.distance, o.distance);
            }
        }

        List<ScoredCity> scored = new ArrayList<>();
        synchronized (CITIES) {
            for (CityInfo c : CITIES) {
                int best = levenshtein(normQ, stripAccents(c.getName()).toLowerCase());
                if (c.getAliases() != null) {
                    for (String a : c.getAliases()) {
                        int d = levenshtein(normQ, stripAccents(a).toLowerCase());
                        if (d < best) best = d;
                    }
                }
                scored.add(new ScoredCity(c, best));
            }
        }

        Collections.sort(scored);
        return scored.stream().limit(max).map(s -> s.city).collect(Collectors.toList());
    }

    private static int levenshtein(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    private static boolean isWordMatch(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        if (needle.length() >= 5 && haystack.contains(needle)) {
            return true;
        }
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b").matcher(haystack).find();
    }

    private static boolean isGenericLandmarkWord(String word) {
        return word.equals("RAILWAY") || word.equals("STATION") || word.equals("AIRPORT")
                || word.equals("HOTEL") || word.equals("STAND") || word.equals("STOP")
                || word.equals("PALACE") || word.equals("CENTER") || word.equals("CENTRE")
                || word.equals("TEMPLE") || word.equals("RESORT") || word.equals("CIRCLE")
                || word.equals("HILL") || word.equals("MARKET") || word.equals("STREET")
                || word.equals("ROAD") || word.equals("CROSS");
    }

    public static CityInfo findCity(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String upper = stripAccents(location).toUpperCase().trim();
        if (isGenericLandmarkWord(upper)) {
            return null;
        }

        synchronized (CITIES) {
            // 1. Exact match on key, name, or codes
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
                if (city.getAliases() != null) {
                    for (String alias : city.getAliases()) {
                        if (upper.equalsIgnoreCase(stripAccents(alias))) {
                            return city;
                        }
                    }
                }
            }

            // 2. Word or substring matching: find the city with the longest matching name, key, or alias
            CityInfo bestCity = null;
            int bestLen = 0;

            for (CityInfo city : CITIES) {
                String cName = stripAccents(city.getName()).toUpperCase();
                String cKey = city.getKey().toUpperCase();

                if (isWordMatch(upper, cKey) && cKey.length() > bestLen) {
                    bestLen = cKey.length();
                    bestCity = city;
                }
                if (isWordMatch(upper, cName) && cName.length() > bestLen) {
                    bestLen = cName.length();
                    bestCity = city;
                }

                if (city.getAliases() != null) {
                    for (String alias : city.getAliases()) {
                        String normAlias = stripAccents(alias).toUpperCase();
                        if (isWordMatch(upper, normAlias) && normAlias.length() > bestLen) {
                            bestLen = normAlias.length();
                            bestCity = city;
                        }
                    }
                }
            }

            if (bestCity != null) {
                return bestCity;
            }

            // 3. Fuzzy matching: only for single-word queries without spaces (e.g. slight typos like "mysure" or "banglore")
            if (!upper.contains(" ") && upper.length() >= 4 && upper.length() <= 15) {
                CityInfo fuzzyBest = null;
                int minDistance = 999;

                for (CityInfo city : CITIES) {
                    String cName = stripAccents(city.getName()).toUpperCase();
                    String cKey = city.getKey().toUpperCase();

                    int d = Math.min(levenshtein(upper, cKey), levenshtein(upper, cName));
                    if (city.getAliases() != null) {
                        for (String alias : city.getAliases()) {
                            String normAlias = stripAccents(alias).toUpperCase();
                            d = Math.min(d, levenshtein(upper, normAlias));
                        }
                    }

                    if (d <= 2 && d < minDistance) {
                        minDistance = d;
                        fuzzyBest = city;
                    }
                }

                if (fuzzyBest != null) {
                    return fuzzyBest;
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

        String upper = stripAccents(location).toUpperCase();
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
