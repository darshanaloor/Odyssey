package com.odyssey.service;

import com.odyssey.dto.parse.*;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.CityInfo;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.repository.TripRepository;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingImportService {

    private static final long MAX_FILE_SIZE = 100 * 1024; // 100 KB limit

    private final TripRepository tripRepository;
    private final TripAccessService tripAccessService;
    private final DependencyGraph dependencyGraph;

    /**
     * Parse confirmation text or uploaded file without saving anything.
     * Memory-only, UTF-8, no logging of confirmation content.
     */
    public ParsePreviewResponse parseTripConfirmation(String tripId, String rawText, MultipartFile file) {
        Trip trip = tripAccessService.requireOwner(tripId);
        String text = validateAndExtractInput(rawText, file);

        ParsePreviewResponse response = parseTextToPreview(text);

        // Duplicate detection against existing bookings in the trip
        checkDuplicates(response.getBookings(), trip.getBookings());

        int duplicates = (int) response.getBookings().stream().filter(ParsedBookingDto::isDuplicateWarning).count();
        response.setDuplicateCount(duplicates);

        return response;
    }

    /**
     * General parse preview (for unassigned / standalone previews).
     */
    public ParsePreviewResponse parseGeneralConfirmation(String rawText, MultipartFile file) {
        String text = validateAndExtractInput(rawText, file);
        return parseTextToPreview(text);
    }

    /**
     * Confirms and persists the parsed/edited bookings.
     * Enforces required fields, dependencies, and cycle checks.
     * Records an audit entry with booking counts only (never raw text).
     */
    public Trip confirmImport(String tripId, ConfirmImportRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);

        if (request == null || request.getBookings() == null || request.getBookings().isEmpty()) {
            throw new IllegalArgumentException("At least one booking is required to import");
        }

        Set<String> validExistingIds = trip.getBookings().stream()
                .map(Booking::getId)
                .collect(Collectors.toSet());

        List<Booking> newBookings = new ArrayList<>();
        Set<String> allBatchIds = new HashSet<>(validExistingIds);

        // Pre-generate IDs for this batch so internal dependencies can be verified
        for (int i = 0; i < request.getBookings().size(); i++) {
            String genId = IdGenerator.generate(tripId + "-BKG");
            allBatchIds.add(genId);
        }

        Iterator<String> idIterator = allBatchIds.stream()
                .filter(id -> !validExistingIds.contains(id))
                .iterator();

        for (ImportBookingDraftDto draft : request.getBookings()) {
            validateDraft(draft);

            String bookingId = idIterator.hasNext() ? idIterator.next() : IdGenerator.generate(tripId + "-BKG");

            Booking booking = Booking.builder()
                    .id(bookingId)
                    .type(draft.getType())
                    .provider(draft.getProvider().trim())
                    .from(draft.getFrom().trim())
                    .to(draft.getTo().trim())
                    .startTime(draft.getStartTime())
                    .endTime(draft.getEndTime())
                    .cost(draft.getCost())
                    .status(BookingStatus.CONFIRMED)
                    .source("PARSED")
                    .bookingReference(draft.getBookingReference() != null ? draft.getBookingReference().trim() : null)
                    .vehicleNumber(draft.getVehicleNumber() != null ? draft.getVehicleNumber().trim() : null)
                    .seatNumbers(draft.getSeatNumbers() != null ? draft.getSeatNumbers().trim() : null)
                    .passengerNames(draft.getPassengerNames() != null ? draft.getPassengerNames().trim() : null)
                    .hotelContactEmail(draft.getHotelContactEmail() != null ? draft.getHotelContactEmail().trim() : null)
                    .dependsOn(draft.getDependsOn() != null ? new ArrayList<>(draft.getDependsOn()) : new ArrayList<>())
                    .lastStatusState("ON_TIME")
                    .lastDelayMinutes(0)
                    .lastStatusSource("SIMULATED")
                    .lastCheckedAt(LocalDateTime.now())
                    .build();

            newBookings.add(booking);
        }

        // Validate dependencies and cycle detection on combined list
        List<Booking> combinedBookings = new ArrayList<>(trip.getBookings());
        combinedBookings.addAll(newBookings);

        for (Booking b : newBookings) {
            validateDependencies(b, allBatchIds);
        }

        if (dependencyGraph.hasCycle(combinedBookings)) {
            throw new IllegalArgumentException("Dependency cycle detected in bookings");
        }

        // Commit to trip
        trip.getBookings().addAll(newBookings);

        // Audit entry: counts only, no confirmation text
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKINGS_IMPORTED")
                .details("Imported " + newBookings.size() + " bookings from text confirmation")
                .build());

        return tripRepository.save(trip);
    }

    /**
     * Strict input validator:
     * - Only .txt and .eml extensions
     * - Max 100 KB
     * - Memory UTF-8 only
     * - Never logs text
     */
    private String validateAndExtractInput(String rawText, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File size exceeds 100 KB limit");
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".txt") && !filename.toLowerCase().endsWith(".eml"))) {
                throw new IllegalArgumentException("Unsupported file type: only .txt and .eml files are accepted");
            }

            String contentType = file.getContentType();
            if (contentType != null) {
                String ct = contentType.toLowerCase();
                if (ct.startsWith("image/") || ct.equals("application/pdf") || ct.contains("zip") || ct.contains("octet-stream") && !(filename.toLowerCase().endsWith(".txt") || filename.toLowerCase().endsWith(".eml"))) {
                    throw new IllegalArgumentException("Unsupported content type: only plain text and email files are accepted");
                }
            }

            try {
                byte[] bytes = file.getBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to read file as UTF-8 text");
            }
        }

        if (rawText != null && !rawText.isBlank()) {
            byte[] bytes = rawText.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("Input text exceeds 100 KB limit");
            }
            return rawText;
        }

        throw new IllegalArgumentException("Input text or file is required");
    }

    /**
     * Deterministic text parser with field confidences and unreadable lines report.
     */
    public ParsePreviewResponse parseTextToPreview(String confirmationText) {
        ParsePreviewResponse response = new ParsePreviewResponse();
        if (confirmationText == null || confirmationText.isBlank()) {
            return response;
        }

        String[] blocks = confirmationText.split("(?m)(?:\\r?\\n){2,}");
        int draftCounter = 1;

        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) continue;

            String[] lines = trimmedBlock.split("\\r?\\n");

            if (lines.length > 1 && isMultiLineStructuredBooking(trimmedBlock)) {
                ParsedBookingDto booking = parseBlockAsBooking("draft-" + (draftCounter++), trimmedBlock);
                if (booking != null) {
                    response.getBookings().add(booking);
                } else {
                    response.getUnparsedLines().add("Could not read this line: " + trimmedBlock.replace("\n", " ").trim());
                }
            } else {
                for (String line : lines) {
                    String trimmedLine = line.trim();
                    if (trimmedLine.isEmpty()) continue;

                    ParsedBookingDto booking = parseBlockAsBooking("draft-" + (draftCounter++), trimmedLine);
                    if (booking != null) {
                        response.getBookings().add(booking);
                    } else {
                        response.getUnparsedLines().add("Could not read this line: " + trimmedLine);
                    }
                }
            }
        }

        return response;
    }

    private boolean isMultiLineStructuredBooking(String text) {
        boolean hasFieldHeaders = text.matches("(?si).*(?:airline|provider|operator|hotel|carrier|flight|train|departure|arrival|check-?in|check-?out|fare|cost|pnr|seat|passenger)\\s*:.*");
        int departsCount = 0;
        int checkinCount = 0;
        Matcher mDep = Pattern.compile("(?i)\\bdeparts\\b").matcher(text);
        while (mDep.find()) departsCount++;
        Matcher mChk = Pattern.compile("(?i)\\bcheck-?in\\b").matcher(text);
        while (mChk.find()) checkinCount++;

        return hasFieldHeaders && departsCount <= 1 && checkinCount <= 1;
    }

    public ParsedBookingDto parseBlockAsBooking(String draftId, String text) {
        BookingType type = detectBookingType(text);
        String provider = extractProvider(text, type);
        String vehicleNumber = extractVehicleNumber(text, type);
        String pnr = extractBookingReference(text);
        String seats = extractSeatNumbers(text);
        String passengers = extractPassengerNames(text);
        BigDecimal cost = extractCost(text);

        LocalDate contextDate = parseDate(text);
        if (contextDate == null) {
            contextDate = LocalDate.of(2026, 10, 21);
        }

        String from = null;
        String to = null;
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        // Pattern 1: departs <city> 10:00 arrives <city> 14:30
        Pattern departsArrivesPattern = Pattern.compile(
                "(?i)departs\\s+([A-Za-z0-9\\s()]+?)\\s+(?:(?:on|at|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}(?:st|nd|rd|th)?[\\s\\-]+[A-Za-z]{3,9}[\\s\\-]+\\d{4})\\s+)*(\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?)\\s+arrives\\s+([A-Za-z0-9\\s()]+?)\\s+(?:(?:on|at|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}(?:st|nd|rd|th)?[\\s\\-]+[A-Za-z]{3,9}[\\s\\-]+\\d{4})\\s+)*(\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?)"
        );
        Matcher departsArrivesMatcher = departsArrivesPattern.matcher(text);

        if (departsArrivesMatcher.find()) {
            from = departsArrivesMatcher.group(1).trim();
            LocalTime t1 = parseTime(departsArrivesMatcher.group(2).trim());
            to = departsArrivesMatcher.group(3).trim();
            LocalTime t2 = parseTime(departsArrivesMatcher.group(4).trim());

            if (t1 != null && t2 != null) {
                startTime = contextDate.atTime(t1);
                endTime = contextDate.atTime(t2);
                if (endTime.isBefore(startTime)) {
                    endTime = endTime.plusDays(1);
                }
            }
            if (type == null) {
                type = BookingType.BUS;
            }
        } else {
            // Structured from / to
            from = extractPattern(text, "(?i)(?:from|origin|pickup|departure station|boarding)\\s*[:\\-]?\\s*([A-Za-z0-9\\-\\s(),]+?)(?=\\s+(?:to|dest|destination|drop|dropoff|dep|arr|check|cost|fare|at|on|date|time|pnr|seat)|\\n|$|,|;)");
            to = extractPattern(text, "(?i)(?:to|dest|destination|dropoff|drop|arrival station)\\s*[:\\-]?\\s*([A-Za-z0-9\\-\\s(),]+?)(?=\\s+(?:dep|arr|check|cost|fare|from|at|on|date|time|pnr|seat)|\\n|$|,|;)");

            LocalDateTime[] times = extractTimes(text, contextDate, type);
            if (times != null) {
                startTime = times[0];
                endTime = times[1];
            }
        }

        // If no booking type and no times/locations could be recognized, this is not a booking
        if (type == null && startTime == null && provider == null) {
            return null;
        }

        // Fallbacks for locations if type is HOTEL
        if (type == BookingType.HOTEL) {
            if (from == null || from.isBlank()) {
                from = extractLocationFromText(text, provider);
            }
            if (to == null || to.isBlank()) {
                to = provider != null ? provider : from;
            }
        }

        // Normalize city names through cities.json
        from = normalizeCity(from);
        to = normalizeCity(to);

        // Build field confidence objects (EXTRACTED if present, MISSING if absent)
        ParsedFieldDto<BookingType> typeDto = type != null ? ParsedFieldDto.extracted(type) : ParsedFieldDto.missing();
        ParsedFieldDto<String> providerDto = (provider != null && !provider.isBlank()) ? ParsedFieldDto.extracted(provider) : ParsedFieldDto.missing();
        ParsedFieldDto<String> vehicleDto = (vehicleNumber != null && !vehicleNumber.isBlank()) ? ParsedFieldDto.extracted(vehicleNumber) : ParsedFieldDto.missing();
        ParsedFieldDto<String> fromDto = (from != null && !from.isBlank()) ? ParsedFieldDto.extracted(from) : ParsedFieldDto.missing();
        ParsedFieldDto<String> toDto = (to != null && !to.isBlank()) ? ParsedFieldDto.extracted(to) : ParsedFieldDto.missing();
        ParsedFieldDto<LocalDateTime> startDto = startTime != null ? ParsedFieldDto.extracted(startTime) : ParsedFieldDto.missing();
        ParsedFieldDto<LocalDateTime> endDto = endTime != null ? ParsedFieldDto.extracted(endTime) : ParsedFieldDto.missing();
        ParsedFieldDto<String> pnrDto = (pnr != null && !pnr.isBlank()) ? ParsedFieldDto.extracted(pnr) : ParsedFieldDto.missing();
        ParsedFieldDto<String> seatDto = (seats != null && !seats.isBlank()) ? ParsedFieldDto.extracted(seats) : ParsedFieldDto.missing();
        ParsedFieldDto<String> passDto = (passengers != null && !passengers.isBlank()) ? ParsedFieldDto.extracted(passengers) : ParsedFieldDto.missing();
        ParsedFieldDto<BigDecimal> costDto = cost != null ? ParsedFieldDto.extracted(cost) : ParsedFieldDto.missing();

        return ParsedBookingDto.builder()
                .id(draftId)
                .type(typeDto)
                .provider(providerDto)
                .vehicleNumber(vehicleDto)
                .from(fromDto)
                .to(toDto)
                .startTime(startDto)
                .endTime(endDto)
                .bookingReference(pnrDto)
                .seatNumbers(seatDto)
                .passengerNames(passDto)
                .cost(costDto)
                .duplicateWarning(false)
                .duplicateReason(null)
                .build();
    }

    /**
     * Verifies an LLM suggestion against the source text.
     * The value MUST literally appear in the source text; otherwise discarded as hallucination.
     */
    public boolean verifyLlmSuggestion(String sourceText, String suggestedValue) {
        if (sourceText == null || suggestedValue == null || suggestedValue.isBlank()) {
            return false;
        }
        return sourceText.toLowerCase().contains(suggestedValue.trim().toLowerCase());
    }

    public void checkDuplicates(List<ParsedBookingDto> parsedBookings, List<Booking> existingBookings) {
        if (parsedBookings == null || existingBookings == null || existingBookings.isEmpty()) {
            return;
        }

        for (ParsedBookingDto parsed : parsedBookings) {
            String ref = parsed.getBookingReference() != null ? parsed.getBookingReference().getValue() : null;
            String prov = parsed.getProvider() != null ? parsed.getProvider().getValue() : null;
            String from = parsed.getFrom() != null ? parsed.getFrom().getValue() : null;
            String to = parsed.getTo() != null ? parsed.getTo().getValue() : null;
            LocalDateTime start = parsed.getStartTime() != null ? parsed.getStartTime().getValue() : null;

            for (Booking existing : existingBookings) {
                // Rule 1: Same booking reference
                if (ref != null && !ref.isBlank() && existing.getBookingReference() != null
                        && ref.trim().equalsIgnoreCase(existing.getBookingReference().trim())) {
                    parsed.setDuplicateWarning(true);
                    parsed.setDuplicateReason("A booking with reference '" + ref.trim() + "' already exists in this trip.");
                    break;
                }

                // Rule 2: Same provider, route and start time
                if (prov != null && !prov.isBlank() && existing.getProvider() != null
                        && prov.trim().equalsIgnoreCase(existing.getProvider().trim())
                        && from != null && existing.getFrom() != null
                        && normalizeCityKey(from).equalsIgnoreCase(normalizeCityKey(existing.getFrom()))
                        && to != null && existing.getTo() != null
                        && normalizeCityKey(to).equalsIgnoreCase(normalizeCityKey(existing.getTo()))
                        && start != null && existing.getStartTime() != null
                        && start.equals(existing.getStartTime())) {
                    parsed.setDuplicateWarning(true);
                    parsed.setDuplicateReason("A booking with provider '" + prov.trim() + "' on this route and start time already exists in this trip.");
                    break;
                }
            }
        }
    }

    private void validateDraft(ImportBookingDraftDto draft) {
        if (draft.getType() == null) {
            throw new IllegalArgumentException("Booking type is required");
        }
        if (draft.getProvider() == null || draft.getProvider().isBlank()) {
            throw new IllegalArgumentException("Provider cannot be blank");
        }
        if (draft.getFrom() == null || draft.getFrom().isBlank()) {
            throw new IllegalArgumentException("'from' location is required");
        }
        if (draft.getTo() == null || draft.getTo().isBlank()) {
            throw new IllegalArgumentException("'to' location is required");
        }
        if (draft.getStartTime() == null || draft.getEndTime() == null) {
            throw new IllegalArgumentException("Booking startTime and endTime are required");
        }
        if (!draft.getEndTime().isAfter(draft.getStartTime())) {
            throw new IllegalArgumentException("Booking endTime (" + draft.getEndTime() + ") must be after startTime (" + draft.getStartTime() + ")");
        }
        if (draft.getCost() != null && draft.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Booking cost cannot be negative");
        }
    }

    private void validateDependencies(Booking b, Set<String> validIds) {
        if (b.getDependsOn() != null) {
            for (String dep : b.getDependsOn()) {
                if (dep != null && !dep.isBlank()) {
                    if (dep.equalsIgnoreCase(b.getId())) {
                        throw new IllegalArgumentException("Booking cannot depend on itself: " + dep);
                    }
                    if (validIds != null && !validIds.contains(dep)) {
                        throw new IllegalArgumentException("Unknown dependency booking ID: " + dep);
                    }
                }
            }
        }
    }

    private BookingType detectBookingType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("flight") || lower.contains("airline") || lower.contains("indigo") || lower.contains("air india") || lower.contains("vistara") || lower.contains("spicejet") || lower.contains("airport") || lower.matches("(?s).*\\b(?:6e|ai|uk|sg)[-\\s]?\\d{3,4}\\b.*")) {
            return BookingType.FLIGHT;
        }
        if (lower.contains("train") || lower.contains("railway") || lower.contains("irctc") || lower.contains("express") || lower.contains("superfast") || lower.contains("shatabdi") || lower.contains("vande bharat") || lower.contains("rajdhani") || lower.contains("coach") || lower.contains("berth")) {
            return BookingType.TRAIN;
        }
        if (lower.contains("bus") || lower.contains("ksrtc") || lower.contains("sarige") || lower.contains("airavat") || lower.contains("sleeper") || lower.contains("volvo") || lower.contains("vrl") || lower.contains("srs") || lower.contains("anand travels") || lower.contains("redbus")) {
            return BookingType.BUS;
        }
        if (lower.contains("cab") || lower.contains("taxi") || lower.contains("uber") || lower.contains("ola") || lower.contains("ride") || lower.contains("driver")) {
            return BookingType.CAB;
        }
        if (lower.contains("hotel") || lower.contains("resort") || lower.contains("inn") || lower.contains("stay") || lower.contains("room") || lower.contains("check-in") || lower.contains("check in") || lower.contains("checkout") || lower.contains("check-out") || lower.contains("taj")) {
            return BookingType.HOTEL;
        }
        if (lower.contains("event") || lower.contains("keynote") || lower.contains("gathering") || lower.contains("conference") || lower.contains("summit") || lower.contains("seminar")) {
            return BookingType.EVENT;
        }
        return null;
    }

    private String extractProvider(String text, BookingType type) {
        // 1. Explicit label (Airline: IndiGo, Provider: KSRTC, Operator: SRS, Hotel: Taj, etc.)
        String provider = extractPattern(text, "(?i)(?:provider|airline|train|operator|carrier|hotel|service|agency)\\s*[:\\-]?\\s*([A-Za-z0-9\\-\\s()&.]+?)(?=\\s+(?:flight|train|bus|cab|hotel|from|to|dep|arr|check|cost|fare|rs|inr|pnr|seat|vehicle)|\\n|$|,|;)");
        if (provider != null && !provider.isBlank()) {
            return cleanProvider(provider);
        }

        // 2. Phrase pattern: "Bus confirmation KSRTC Sarige departs..." or "Cab booking Ola from..."
        String prefixMatch = extractPattern(text, "(?i)(?:bus|train|flight|cab|hotel)\\s+(?:confirmation|reservation|booking|ticket)?\\s*[:\\-]?\\s*([A-Za-z0-9\\-\\s()&.]+?)(?=\\s+(?:departs|from|to|check|at|cost|fare|arr|\\d{1,2}:\\d{2})|\\n|$|,)");
        if (prefixMatch != null && !prefixMatch.isBlank()) {
            return cleanProvider(prefixMatch);
        }

        // 3. Known provider names
        if (text.matches("(?si).*(?:indigo|air\\s*india|vistara|spicejet).*")) {
            return cleanProvider(extractPattern(text, "(?i)((?:indigo|air\\s*india|vistara|spicejet)[A-Za-z0-9\\-\\s()]*?)(?=\\s+(?:flight|departs|from|to|cost|fare|seat|pnr)|\\n|$|,)"));
        }
        if (text.matches("(?si).*(?:ksrtc\\s*sarige|ksrtc\\s*airavat|ksrtc|vrl|srs\\s*travels|srs|anand\\s*travels).*")) {
            return cleanProvider(extractPattern(text, "(?i)((?:ksrtc\\s*sarige|ksrtc\\s*airavat|ksrtc|vrl|srs\\s*travels|srs|anand\\s*travels)[A-Za-z0-9\\-\\s()]*?)(?=\\s+(?:departs|from|to|cost|fare|bus)|\\n|$|,)"));
        }
        if (text.matches("(?si).*(?:taj\\s*resort\\s*goa|the\\s*gateway\\s*hotel|hotel\\s+[A-Za-z0-9\\s]+|resort\\s+[A-Za-z0-9\\s]+|taj\\s+[A-Za-z0-9\\s]+).*")) {
            return cleanProvider(extractPattern(text, "(?i)([A-Za-z0-9\\-\\s()]*(?:hotel|resort|inn|banquet|taj)[A-Za-z0-9\\-\\s()]*?)(?=\\s+(?:check|from|to|cost|fare|guest|room)|\\n|$|,)"));
        }
        if (text.matches("(?si).*(?:ola|uber).*")) {
            return cleanProvider(extractPattern(text, "(?i)((?:ola|uber)[A-Za-z0-9\\-\\s()]*?)(?=\\s+(?:from|to|departs|cost|fare|cab)|\\n|$|,)"));
        }
        if (text.matches("(?si).*(?:irctc|shatabdi|rajdhani|vande\\s*bharat|express|superfast).*")) {
            return cleanProvider(extractPattern(text, "(?i)([A-Za-z0-9\\-\\s()]*(?:irctc|shatabdi|rajdhani|vande\\s*bharat|express|superfast)[A-Za-z0-9\\-\\s()]*?)(?=\\s+(?:train|departs|from|to|cost|fare)|\\n|$|,)"));
        }

        return null;
    }

    private String cleanProvider(String p) {
        if (p == null) return null;
        String cleaned = p.trim().replaceAll("\\s+", " ");
        if (cleaned.toLowerCase().startsWith("confirmation") || cleaned.toLowerCase().startsWith("booking") || cleaned.toLowerCase().startsWith("reservation")) {
            cleaned = cleaned.replaceFirst("(?i)^(?:confirmation|booking|reservation)\\s*", "");
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private String extractVehicleNumber(String text, BookingType type) {
        // Flight numbers: 6E-534, AI 102, UK-981, SG 234
        Matcher flightMatcher = Pattern.compile("(?i)(?:flight\\s*(?:no|number)?[:\\s\\-]*|\\b)(6E[-\\s]?\\d{3,4}|AI[-\\s]?\\d{3,4}|UK[-\\s]?\\d{3,4}|SG[-\\s]?\\d{3,4})\\b").matcher(text);
        if (flightMatcher.find()) {
            return flightMatcher.group(1).trim().replace(" ", "-");
        }

        // Train numbers: Train: 12658 or Train 12658 or 5-digit number
        Matcher trainMatcher = Pattern.compile("(?i)(?:train\\s*(?:no|number)?[:\\s\\-]*|\\b)(\\d{5})\\b").matcher(text);
        if (trainMatcher.find()) {
            return trainMatcher.group(1).trim();
        }

        // Bus/Cab registration numbers: KA-01-AB-1234 or KA 01 F 9999
        Matcher vehicleMatcher = Pattern.compile("(?i)(?:bus|cab|vehicle|car|taxi)?\\s*(?:no|number|reg|registration)?[:\\s\\-]*([A-Z]{2}[-\\s]?\\d{1,2}[-\\s]?[A-Z]{1,2}[-\\s]?\\d{4})\\b").matcher(text);
        if (vehicleMatcher.find()) {
            return vehicleMatcher.group(1).trim().replaceAll("\\s+", "-");
        }

        return null;
    }

    private String extractBookingReference(String text) {
        Matcher refMatcher = Pattern.compile("(?i)(?:pnr|booking\\s*(?:ref|reference|id|number|no)|reservation\\s*(?:ref|reference|id|number|no)|ticket\\s*(?:no|number)|ref\\s*#?|reference\\s*#?)[:\\s\\-]*([A-Za-z0-9\\-_]{4,25})\\b").matcher(text);
        if (refMatcher.find()) {
            return refMatcher.group(1).trim();
        }
        return null;
    }

    private String extractSeatNumbers(String text) {
        Matcher seatMatcher = Pattern.compile("(?i)(?:seat[s]?|berth[s]?|seat\\s*no[s]?|berth\\s*no[s]?)[:\\s\\-]*([A-Za-z0-9,\\s\\-]+?)(?=\\s+(?:passenger|name|guest|fare|cost|pnr|status|total)|\\n|$|;)").matcher(text);
        if (seatMatcher.find()) {
            return seatMatcher.group(1).trim();
        }
        return null;
    }

    private String extractPassengerNames(String text) {
        Matcher passMatcher = Pattern.compile("(?i)(?:passenger[s]?|guest[s]?|traveller[s]?|traveler[s]?|lead\\s*guest|name[s]?)[:\\s\\-]*([A-Za-z\\s.,]+?)(?=\\s+(?:seat|berth|fare|cost|pnr|status|phone|age|gender)|\\n|$|;)").matcher(text);
        if (passMatcher.find()) {
            return passMatcher.group(1).trim();
        }
        return null;
    }

    private BigDecimal extractCost(String text) {
        Pattern costPattern = Pattern.compile("(?i)(?:rs\\.?|inr|₹|cost\\s*[:\\-]?|fare\\s*[:\\-]?|price\\s*[:\\-]?|total\\s*(?:fare|amount)\\s*[:\\-]?|amount\\s*[:\\-]?)\\s*[:\\-]?\\s*([0-9]+(?:[.,][0-9]{1,2})?)");
        Matcher matcher = costPattern.matcher(text);
        if (matcher.find()) {
            try {
                String val = matcher.group(1).replace(",", "");
                return new BigDecimal(val);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private LocalDate parseDate(String text) {
        if (text == null) return null;

        // 1. ISO date: yyyy-MM-dd
        Matcher isoMatcher = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(text);
        if (isoMatcher.find()) {
            try {
                return LocalDate.parse(isoMatcher.group(1));
            } catch (Exception ignored) {}
        }

        // 2. Formatted date: "21 Oct 2026", "21 October 2026", "21-Oct-2026"
        Matcher fmtMatcher = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-]+([A-Za-z]{3,9})[\\s\\-]+(\\d{4})\\b").matcher(text);
        if (fmtMatcher.find()) {
            int day = Integer.parseInt(fmtMatcher.group(1));
            String monthStr = fmtMatcher.group(2).toLowerCase();
            int year = Integer.parseInt(fmtMatcher.group(3));
            int month = parseMonth(monthStr);
            if (month > 0) {
                try {
                    return LocalDate.of(year, month, day);
                } catch (Exception ignored) {}
            }
        }

        // 3. Slash / Dash: 21/10/2026 or 21-10-2026
        Matcher slashMatcher = Pattern.compile("\\b(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})\\b").matcher(text);
        if (slashMatcher.find()) {
            try {
                int first = Integer.parseInt(slashMatcher.group(1));
                int second = Integer.parseInt(slashMatcher.group(2));
                int year = Integer.parseInt(slashMatcher.group(3));
                // Assume day/month/year (Indian standard) if first > 12 or by default
                int day = first;
                int month = second;
                if (second > 12 && first <= 12) {
                    day = second;
                    month = first;
                }
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private int parseMonth(String m) {
        if (m.startsWith("jan")) return 1;
        if (m.startsWith("feb")) return 2;
        if (m.startsWith("mar")) return 3;
        if (m.startsWith("apr")) return 4;
        if (m.startsWith("may")) return 5;
        if (m.startsWith("jun")) return 6;
        if (m.startsWith("jul")) return 7;
        if (m.startsWith("aug")) return 8;
        if (m.startsWith("sep")) return 9;
        if (m.startsWith("oct")) return 10;
        if (m.startsWith("nov")) return 11;
        if (m.startsWith("dec")) return 12;
        return -1;
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        String clean = timeStr.trim().replaceAll("\\s+", " ");
        // Check 12-hour AM/PM: "10:00 AM", "02:30 PM", "9:15 pm"
        Pattern ampmPattern = Pattern.compile("(?i)(\\d{1,2}):(\\d{2})\\s*([ap]m)");
        Matcher ampmMatcher = ampmPattern.matcher(clean);
        if (ampmMatcher.find()) {
            int h = Integer.parseInt(ampmMatcher.group(1));
            int m = Integer.parseInt(ampmMatcher.group(2));
            boolean isPm = ampmMatcher.group(3).equalsIgnoreCase("pm");
            if (isPm && h < 12) h += 12;
            else if (!isPm && h == 12) h = 0;
            return LocalTime.of(h, m);
        }

        // Check 24-hour: "14:30", "10:00"
        Pattern h24Pattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher h24Matcher = h24Pattern.matcher(clean);
        if (h24Matcher.find()) {
            int h = Integer.parseInt(h24Matcher.group(1));
            int m = Integer.parseInt(h24Matcher.group(2));
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                return LocalTime.of(h, m);
            }
        }
        return null;
    }

    private LocalDateTime[] extractTimes(String text, LocalDate baseDate, BookingType type) {
        // Try extracting ISO date-times: 2026-10-21T10:30:00 or 2026-10-21 10:30
        Pattern isoPattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(?::\\d{2})?)");
        Matcher isoMatcher = isoPattern.matcher(text);
        List<LocalDateTime> foundIso = new ArrayList<>();
        while (isoMatcher.find()) {
            try {
                String match = isoMatcher.group(1).replace(" ", "T");
                if (match.length() == 16) {
                    match = match + ":00";
                }
                foundIso.add(LocalDateTime.parse(match));
            } catch (Exception ignored) {}
        }
        if (foundIso.size() >= 2) {
            LocalDateTime s = foundIso.get(0);
            LocalDateTime e = foundIso.get(1);
            if (e.isBefore(s)) e = e.plusDays(1);
            return new LocalDateTime[]{s, e};
        } else if (foundIso.size() == 1) {
            LocalDateTime s = foundIso.get(0);
            return new LocalDateTime[]{s, type == BookingType.HOTEL ? s.plusDays(1).withHour(11).withMinute(0) : s.plusHours(2)};
        }

        // Check Hotel check-in / check-out: "check-in 21 Oct 2026 16:00", "check-in 16:00"
        Matcher checkInMatcher = Pattern.compile("(?i)(?:check-?in)\\s*[:\\-]?\\s*(?:(?:on|at|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}(?:st|nd|rd|th)?[\\s\\-]+[A-Za-z]{3,9}[\\s\\-]+\\d{4})\\s+)*(\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?)").matcher(text);
        if (checkInMatcher.find()) {
            LocalTime startLocal = parseTime(checkInMatcher.group(1));
            if (startLocal != null) {
                LocalDateTime start = baseDate.atTime(startLocal);
                Matcher checkOutMatcher = Pattern.compile("(?i)(?:check-?out)\\s*[:\\-]?\\s*(?:(?:on|at|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}(?:st|nd|rd|th)?[\\s\\-]+[A-Za-z]{3,9}[\\s\\-]+\\d{4})\\s+)*(\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?)").matcher(text);
                LocalDateTime end;
                if (checkOutMatcher.find()) {
                    LocalTime endLocal = parseTime(checkOutMatcher.group(1));
                    end = endLocal != null ? baseDate.plusDays(1).atTime(endLocal) : start.plusDays(1).withHour(11).withMinute(0);
                } else {
                    end = start.plusDays(1).withHour(11).withMinute(0);
                }
                return new LocalDateTime[]{start, end};
            }
        }

        // Try extracting times with AM/PM or 24-hr
        Pattern timePattern = Pattern.compile("(\\b[0-2]?[0-9]:[0-5][0-9](?:\\s*[AaPp][Mm])?\\b)");
        Matcher timeMatcher = timePattern.matcher(text);
        List<LocalTime> foundTimes = new ArrayList<>();
        while (timeMatcher.find()) {
            LocalTime parsed = parseTime(timeMatcher.group(1));
            if (parsed != null) {
                foundTimes.add(parsed);
            }
        }

        if (foundTimes.size() >= 2) {
            LocalDateTime start = baseDate.atTime(foundTimes.get(0));
            LocalDateTime end = baseDate.atTime(foundTimes.get(1));
            if (end.isBefore(start)) {
                end = end.plusDays(1);
            }
            return new LocalDateTime[]{start, end};
        } else if (foundTimes.size() == 1) {
            LocalDateTime start = baseDate.atTime(foundTimes.get(0));
            LocalDateTime end = type == BookingType.HOTEL
                    ? start.plusDays(1).withHour(11).withMinute(0)
                    : (type == BookingType.CAB ? start.plusMinutes(45) : start.plusHours(2));
            return new LocalDateTime[]{start, end};
        }

        return null;
    }

    private String normalizeCity(String location) {
        if (location == null || location.isBlank()) return null;
        CityInfo city = InventoryService.findCity(location.trim());
        if (city != null) {
            return city.getName();
        }
        return location.trim();
    }

    private String normalizeCityKey(String location) {
        if (location == null || location.isBlank()) return "UNKNOWN";
        return InventoryService.normalizeCityKey(location);
    }

    private String extractLocationFromText(String text, String provider) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        if (lower.contains("goa")) return "Goa";
        if (lower.contains("mangaluru") || lower.contains("ixe") || lower.contains("mangalore")) return "Mangaluru";
        if (lower.contains("bengaluru") || lower.contains("blr") || lower.contains("bangalore")) return "Bengaluru";
        if (lower.contains("hubballi") || lower.contains("hubli")) return "Hubballi";
        if (lower.contains("mumbai") || lower.contains("bom")) return "Mumbai";
        return provider != null ? provider : null;
    }

    private String extractPattern(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
