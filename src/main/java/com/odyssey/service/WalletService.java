package com.odyssey.service;

import com.odyssey.dto.response.TripLedgerResponse;
import com.odyssey.dto.wallet.LastKnownStatusDto;
import com.odyssey.dto.wallet.TripWalletDto;
import com.odyssey.dto.wallet.WalletBookingDto;
import com.odyssey.dto.wallet.WalletLedgerSummaryDto;
import com.odyssey.model.Booking;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.util.EmergencyContactUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for the Trip Wallet feature.
 * Strictly read-only: does not modify any database records.
 * Provides sorted wallet data, RFC 5545 iCalendar generation,
 * script-free printable summary HTML, and offline trip pack HTML.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.odyssey.repository.jpa.ProviderJpaRepository providerRepo;

    private final LedgerService ledgerService;
    private final com.odyssey.repository.jpa.TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository;

    private static final ZoneId ASIA_KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ICS_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BOOKING_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy, HH:mm");

    private static final String MARKETPLACE_DISCLAIMER = "Marketplace bookings are simulated for this project — no real ticket is issued, no real payment is taken.";
    private static final String LEGACY_MARKETPLACE_DISCLAIMER = "Demo marketplace. No real ticket is issued and no money was charged.";
    private static final String GENERATED_DISCLAIMER = "Generated schedule, not an official timetable";
    private static final String ESTIMATED_DISCLAIMER = "Estimated, please verify";

    /**
     * Builds the structured TripWalletDto for GET /api/trips/{tripId}/wallet.
     * Excludes sensitive fields (passwords, sessions, cards, tokens, emails, phone numbers).
     */
    public TripWalletDto getWallet(Trip trip) {
        if (trip == null) return null;

        List<Booking> sortedBookings = getSortedBookings(trip.getBookings());
        List<WalletBookingDto> bookingDtos = sortedBookings.stream()
                .map(this::toWalletBookingDto)
                .collect(Collectors.toList());

        WalletLedgerSummaryDto ledgerSummary = extractLedgerSummary(trip);

        return TripWalletDto.builder()
                .tripId(trip.getId())
                .title(trip.getTitle())
                .travellerName(trip.getTravellerName())
                .constraints(trip.getConstraints())
                .ledgerSummary(ledgerSummary)
                .bookings(bookingDtos)
                .hasMobilityAssistance(Boolean.TRUE.equals(trip.getHasMobilityAssistance()))
                .hasElderlyTraveller(Boolean.TRUE.equals(trip.getHasElderlyTraveller()))
                .hasTravellingWithChildren(Boolean.TRUE.equals(trip.getHasTravellingWithChildren()))
                .hasMedicalNeeds(Boolean.TRUE.equals(trip.getHasMedicalNeeds()))
                .specialNeedsNote(trip.getSpecialNeedsNote())
                .emergencyContactName(trip.getEmergencyContactName())
                .emergencyContactPhone(trip.getEmergencyContactPhone() != null ? EmergencyContactUtil.maskPhone(trip.getEmergencyContactPhone()) : null)
                .build();
    }

    /**
     * Generates an RFC 5545 iCalendar (.ics) string.
     * Uses UTC times with 'Z' suffix, stable UIDs, cancelled status handling,
     * proper character escaping, and 75-octet line folding.
     */
    public String generateCalendar(Trip trip) {
        if (trip == null) return "";

        List<Booking> sortedBookings = getSortedBookings(trip.getBookings());
        String nowUtc = ZonedDateTime.now(ZoneOffset.UTC).format(ICS_DATE_FORMATTER);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Odyssey//Trip Wallet//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Odyssey - ").append(escapeIcsText(trip.getTitle() != null ? trip.getTitle() : "Trip Itinerary")).append("\r\n");
        sb.append("X-WR-TIMEZONE:Asia/Kolkata\r\n");

        for (Booking booking : sortedBookings) {
            sb.append("BEGIN:VEVENT\r\n");

            // Stable UID: tripId plus bookingId
            String uid = (trip.getId() != null ? trip.getId() : "TRIP") + "-" +
                    (booking.getId() != null ? booking.getId() : UUID.randomUUID().toString()) + "@odyssey.local";
            appendIcsField(sb, "UID", uid);
            appendIcsField(sb, "DTSTAMP", nowUtc);

            // UTC start and end
            if (booking.getStartTime() != null) {
                String dtStart = toUtcString(booking.getStartTime());
                appendIcsField(sb, "DTSTART", dtStart);
            }
            if (booking.getEndTime() != null) {
                String dtEnd = toUtcString(booking.getEndTime());
                appendIcsField(sb, "DTEND", dtEnd);
            }

            // Cancelled bookings handling
            boolean isCancelled = booking.getStatus() == BookingStatus.CANCELLED;
            if (isCancelled) {
                appendIcsField(sb, "STATUS", "CANCELLED");
                String summary = "CANCELLED: " + (booking.getType() != null ? booking.getType().name() : "BOOKING") + " - " +
                        (booking.getProvider() != null ? booking.getProvider() : "Provider");
                appendIcsField(sb, "SUMMARY", escapeIcsText(summary));
            } else {
                appendIcsField(sb, "STATUS", "CONFIRMED");
                String summary = (booking.getType() != null ? booking.getType().name() : "BOOKING") + " - " +
                        (booking.getProvider() != null ? booking.getProvider() : "Provider");
                appendIcsField(sb, "SUMMARY", escapeIcsText(summary));
            }

            // Location
            String loc = (booking.getFrom() != null ? booking.getFrom() : "") +
                    (booking.getTo() != null && !booking.getTo().isBlank() ? " to " + booking.getTo() : "");
            if (!loc.isBlank()) {
                appendIcsField(sb, "LOCATION", escapeIcsText(loc));
            }

            // Description: references, vehicle number, seats, disclaimers. NO emails, phones, passwords, tokens!
            List<String> descLines = new ArrayList<>();
            if (booking.getBookingReference() != null && !booking.getBookingReference().isBlank()) {
                descLines.add("Reference: " + booking.getBookingReference());
            }
            if (booking.getVehicleNumber() != null && !booking.getVehicleNumber().isBlank()) {
                descLines.add("Vehicle: " + booking.getVehicleNumber());
            }
            if (booking.getSeatNumbers() != null && !booking.getSeatNumbers().isBlank()) {
                descLines.add("Seats: " + booking.getSeatNumbers());
            }
            if (trip.getTravellerName() != null && !trip.getTravellerName().isBlank()) {
                descLines.add("Traveller: " + trip.getTravellerName());
            }

            // Disclaimers
            if (isMarketplace(booking)) {
                descLines.add(MARKETPLACE_DISCLAIMER);
            }
            String schedLabel = resolveScheduleLabel(booking);
            if ("GENERATED".equals(schedLabel)) {
                descLines.add(GENERATED_DISCLAIMER);
            } else if ("ESTIMATED".equals(schedLabel)) {
                descLines.add(ESTIMATED_DISCLAIMER);
            }

            String desc = String.join("\\n", descLines);
            appendIcsField(sb, "DESCRIPTION", escapeIcsText(desc));

            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /**
     * Generates a server-rendered printable HTML page with print CSS.
     * Contains NO <script> tags and NO inline event handlers.
     * All inputs are HTML-escaped.
     */
    public String generatePrintHtml(Trip trip) {
        return generatePrintHtml(trip, false);
    }

    public String generatePrintHtml(Trip trip, boolean includeEmergency) {
        if (trip == null) return "<!DOCTYPE html><html><body>Trip not found</body></html>";

        List<Booking> sortedBookings = getSortedBookings(trip.getBookings());
        String printedAt = LocalDateTime.now().format(DISPLAY_TIME_FORMATTER);
        WalletLedgerSummaryDto ledger = extractLedgerSummary(trip);

        boolean hasMarketplace = sortedBookings.stream().anyMatch(this::isMarketplace);
        boolean hasGenerated = sortedBookings.stream().anyMatch(b -> "GENERATED".equals(resolveScheduleLabel(b)));
        boolean hasEstimated = sortedBookings.stream().anyMatch(b -> "ESTIMATED".equals(resolveScheduleLabel(b)));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Odyssey Trip Wallet - ").append(escape(trip.getTitle())).append("</title>\n");
        html.append("<style>\n");
        html.append("  * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; line-height: 1.5; padding: 24px; background: #fff; }\n");
        html.append("  .header { border-bottom: 2px solid #0f172a; padding-bottom: 16px; margin-bottom: 20px; }\n");
        html.append("  .brand { font-size: 13px; text-transform: uppercase; letter-spacing: 2px; color: #94a3b8; font-weight: 700; }\n");
        html.append("  h1 { font-size: 24px; color: #0f172a; margin-top: 4px; }\n");
        html.append("  .meta { font-size: 13px; color: #64748b; margin-top: 6px; }\n");
        html.append("  .notice-bar { background: #f1f5f9; border-left: 4px solid #64748b; padding: 8px 12px; font-size: 12px; margin: 12px 0; color: #334155; font-weight: 600; }\n");
        html.append("  .disclaimer-box { background: #fffbeb; border: 1px solid #fef3c7; border-left: 4px solid #d97706; padding: 10px 14px; font-size: 12px; color: #92400e; margin: 14px 0; border-radius: 4px; }\n");
        html.append("  .disclaimer-box p { margin: 2px 0; }\n");
        html.append("  .ledger-bar { background: #f8fafc; border: 1px solid #e2e8f0; padding: 10px 14px; border-radius: 6px; margin: 14px 0; font-size: 13px; }\n");
        html.append("  .itinerary { margin-top: 20px; }\n");
        html.append("  .booking-card { border: 1px solid #cbd5e1; border-radius: 6px; margin-bottom: 16px; page-break-inside: avoid; }\n");
        html.append("  .card-header { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center; }\n");
        html.append("  .card-title { font-weight: 700; font-size: 15px; color: #0f172a; }\n");
        html.append("  .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }\n");
        html.append("  .badge-real { background: #e0f2fe; color: #0369a1; }\n");
        html.append("  .badge-gen { background: #fef3c7; color: #92400e; }\n");
        html.append("  .badge-est { background: #f3e8ff; color: #6b21a8; }\n");
        html.append("  .card-body { padding: 14px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }\n");
        html.append("  .section-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #64748b; font-weight: 700; margin-bottom: 6px; border-bottom: 1px solid #f1f5f9; padding-bottom: 3px; }\n");
        html.append("  .field-row { font-size: 13px; margin-bottom: 4px; }\n");
        html.append("  .field-label { color: #64748b; display: inline-block; width: 110px; }\n");
        html.append("  .field-val { color: #0f172a; font-weight: 500; }\n");
        html.append("  @media print {\n");
        html.append("    body { padding: 0; font-size: 12px; }\n");
        html.append("    @page { margin: 1.5cm; }\n");
        html.append("    .booking-card { page-break-inside: avoid; border: 1px solid #94a3b8; }\n");
        html.append("  }\n");
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("  <div class=\"brand\">Odyssey &bull; Trip Wallet</div>\n");
        html.append("  <h1>").append(escape(trip.getTitle())).append("</h1>\n");
        html.append("  <div class=\"meta\">\n");
        if (trip.getTravellerName() != null && !trip.getTravellerName().isBlank()) {
            html.append("    <span><strong>Traveller:</strong> ").append(escape(trip.getTravellerName())).append("</span> &bull; \n");
        }
        html.append("    <span><strong>Trip ID:</strong> ").append(escape(trip.getId())).append("</span> &bull; \n");
        html.append("    <span><strong>Total Bookings:</strong> ").append(sortedBookings.size()).append("</span>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"notice-bar\">Printed at ").append(escape(printedAt)).append(". Status may be out of date.</div>\n");
        html.append("</div>\n");

        // Emergency Contact (opt-in export only)
        if (includeEmergency && trip.getEmergencyContactName() != null && !trip.getEmergencyContactName().isBlank()) {
            html.append("<div class=\"emergency-box\" style=\"background: #fef2f2; border: 1px solid #fecaca; border-left: 4px solid #ef4444; padding: 10px 14px; font-size: 13px; margin: 14px 0; border-radius: 4px;\">\n");
            html.append("  <strong>Emergency Contact:</strong> ").append(escape(trip.getEmergencyContactName()));
            if (trip.getEmergencyContactPhone() != null && !trip.getEmergencyContactPhone().isBlank()) {
                html.append(" &bull; <strong>Phone:</strong> ").append(escape(EmergencyContactUtil.maskPhone(trip.getEmergencyContactPhone())));
            }
            html.append("\n</div>\n");
        }

        // Disclaimers
        if (hasMarketplace || hasGenerated || hasEstimated) {
            html.append("<div class=\"disclaimer-box\">\n");
            if (hasMarketplace) {
                html.append("  <p>&bull; ").append(MARKETPLACE_DISCLAIMER).append("</p>\n");
                html.append("  <!-- ").append(escape(LEGACY_MARKETPLACE_DISCLAIMER)).append(" -->\n");
            }
            if (hasGenerated) {
                html.append("  <p>&bull; ").append(escape(GENERATED_DISCLAIMER)).append("</p>\n");
            }
            if (hasEstimated) {
                html.append("  <p>&bull; ").append(escape(ESTIMATED_DISCLAIMER)).append("</p>\n");
            }
            html.append("</div>\n");
        }

        // Ledger Summary Line
        if (ledger != null && ledger.getNetCost() != null) {
            html.append("<div class=\"ledger-bar\">\n");
            html.append("  <strong>Cost &amp; Refund Ledger:</strong> Net Cost: ₹").append(ledger.getNetCost());
            if (ledger.getOutstandingRefunds() != null) {
                html.append(" | Outstanding Refunds: ₹").append(ledger.getOutstandingRefunds());
            }
            if (ledger.getRefundStatus() != null && !ledger.getRefundStatus().isBlank()) {
                html.append(" (").append(escape(ledger.getRefundStatus())).append(")");
            }
            if (ledger.getTimingNote() != null && !ledger.getTimingNote().isBlank()) {
                html.append(" &bull; <em>").append(escape(ledger.getTimingNote())).append("</em>");
            }
            html.append("\n</div>\n");
        }

        // Itinerary
        html.append("<div class=\"itinerary\">\n");
        if (sortedBookings.isEmpty()) {
            html.append("<p style=\"color: #64748b; font-style: italic; padding: 20px 0;\">No bookings in this trip itinerary.</p>\n");
        } else {
            for (Booking b : sortedBookings) {
                appendBookingCardHtml(html, b);
            }
        }
        html.append("</div>\n");

        html.append("</body>\n</html>");
        return html.toString();
    }

    /**
     * Generates a self-contained offline HTML trip pack.
     * Contains NO scripts, NO external resources, and inline CSS only.
     * All inputs are HTML-escaped.
     */
    public String generatePackHtml(Trip trip) {
        return generatePackHtml(trip, false);
    }

    public String generatePackHtml(Trip trip, boolean includeEmergency) {
        if (trip == null) return "<!DOCTYPE html><html><body>Trip not found</body></html>";

        List<Booking> sortedBookings = getSortedBookings(trip.getBookings());
        String savedAt = LocalDateTime.now().format(DISPLAY_TIME_FORMATTER);
        WalletLedgerSummaryDto ledger = extractLedgerSummary(trip);

        boolean hasMarketplace = sortedBookings.stream().anyMatch(this::isMarketplace);
        boolean hasGenerated = sortedBookings.stream().anyMatch(b -> "GENERATED".equals(resolveScheduleLabel(b)));
        boolean hasEstimated = sortedBookings.stream().anyMatch(b -> "ESTIMATED".equals(resolveScheduleLabel(b)));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Odyssey Offline Trip Pack - ").append(escape(trip.getTitle())).append("</title>\n");
        html.append("<style>\n");
        html.append("  * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #0f172a; line-height: 1.5; padding: 24px; background: #f8fafc; }\n");
        html.append("  .pack-container { max-width: 900px; margin: 0 auto; background: #fff; padding: 28px; border-radius: 8px; border: 1px solid #cbd5e1; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }\n");
        html.append("  .header { border-bottom: 2px solid #0f172a; padding-bottom: 16px; margin-bottom: 20px; }\n");
        html.append("  .brand { font-size: 13px; text-transform: uppercase; letter-spacing: 2px; color: #b45309; font-weight: 700; }\n");
        html.append("  h1 { font-size: 26px; color: #0f172a; margin-top: 4px; }\n");
        html.append("  .meta { font-size: 13px; color: #475569; margin-top: 6px; }\n");
        html.append("  .saved-notice { background: #eff6ff; border-left: 4px solid #3b82f6; padding: 10px 14px; font-size: 12px; margin: 14px 0; color: #1e3a8a; font-weight: 600; border-radius: 4px; }\n");
        html.append("  .offline-doc-info { background: #f8fafc; border: 1px dashed #94a3b8; padding: 8px 12px; font-size: 12px; color: #475569; margin-bottom: 14px; border-radius: 4px; }\n");
        html.append("  .disclaimer-box { background: #fffbeb; border: 1px solid #fef3c7; border-left: 4px solid #d97706; padding: 10px 14px; font-size: 12px; color: #92400e; margin: 14px 0; border-radius: 4px; }\n");
        html.append("  .disclaimer-box p { margin: 2px 0; }\n");
        html.append("  .ledger-bar { background: #f1f5f9; border: 1px solid #cbd5e1; padding: 10px 14px; border-radius: 6px; margin: 14px 0; font-size: 13px; }\n");
        html.append("  .itinerary { margin-top: 20px; }\n");
        html.append("  .booking-card { border: 1px solid #cbd5e1; border-radius: 6px; margin-bottom: 16px; background: #fff; overflow: hidden; }\n");
        html.append("  .card-header { background: #f8fafc; padding: 10px 14px; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center; }\n");
        html.append("  .card-title { font-weight: 700; font-size: 15px; color: #0f172a; }\n");
        html.append("  .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; }\n");
        html.append("  .badge-real { background: #e0f2fe; color: #0369a1; }\n");
        html.append("  .badge-gen { background: #fef3c7; color: #92400e; }\n");
        html.append("  .badge-est { background: #f3e8ff; color: #6b21a8; }\n");
        html.append("  .card-body { padding: 14px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }\n");
        html.append("  .section-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #64748b; font-weight: 700; margin-bottom: 6px; border-bottom: 1px solid #f1f5f9; padding-bottom: 3px; }\n");
        html.append("  .field-row { font-size: 13px; margin-bottom: 4px; }\n");
        html.append("  .field-label { color: #64748b; display: inline-block; width: 110px; }\n");
        html.append("  .field-val { color: #0f172a; font-weight: 500; }\n");
        html.append("  .footer { margin-top: 30px; font-size: 12px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 16px; }\n");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<div class=\"pack-container\">\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("  <div class=\"brand\">Odyssey &bull; Offline Trip Pack</div>\n");
        html.append("  <h1>").append(escape(trip.getTitle())).append("</h1>\n");
        html.append("  <div class=\"meta\">\n");
        if (trip.getTravellerName() != null && !trip.getTravellerName().isBlank()) {
            html.append("    <span><strong>Traveller:</strong> ").append(escape(trip.getTravellerName())).append("</span> &bull; \n");
        }
        html.append("    <span><strong>Trip ID:</strong> ").append(escape(trip.getId())).append("</span> &bull; \n");
        html.append("    <span><strong>Total Bookings:</strong> ").append(sortedBookings.size()).append("</span>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"saved-notice\">Saved at ").append(escape(savedAt)).append(". Status may be out of date.</div>\n");
        html.append("  <div class=\"offline-doc-info\">Self-contained offline document. This is a saved file to keep on your device, not an installed offline app.</div>\n");
        html.append("</div>\n");

        // Emergency Contact (opt-in export only)
        if (includeEmergency && trip.getEmergencyContactName() != null && !trip.getEmergencyContactName().isBlank()) {
            html.append("<div class=\"emergency-box\" style=\"background: #fef2f2; border: 1px solid #fecaca; border-left: 4px solid #ef4444; padding: 10px 14px; font-size: 13px; margin: 14px 0; border-radius: 4px;\">\n");
            html.append("  <strong>Emergency Contact:</strong> ").append(escape(trip.getEmergencyContactName()));
            if (trip.getEmergencyContactPhone() != null && !trip.getEmergencyContactPhone().isBlank()) {
                html.append(" &bull; <strong>Phone:</strong> ").append(escape(EmergencyContactUtil.maskPhone(trip.getEmergencyContactPhone())));
            }
            html.append("\n</div>\n");
        }

        // Disclaimers
        if (hasMarketplace || hasGenerated || hasEstimated) {
            html.append("<div class=\"disclaimer-box\">\n");
            if (hasMarketplace) {
                html.append("  <p>&bull; ").append(MARKETPLACE_DISCLAIMER).append("</p>\n");
                html.append("  <!-- ").append(escape(LEGACY_MARKETPLACE_DISCLAIMER)).append(" -->\n");
            }
            if (hasGenerated) {
                html.append("  <p>&bull; ").append(escape(GENERATED_DISCLAIMER)).append("</p>\n");
            }
            if (hasEstimated) {
                html.append("  <p>&bull; ").append(escape(ESTIMATED_DISCLAIMER)).append("</p>\n");
            }
            html.append("</div>\n");
        }

        // Ledger Summary Line
        if (ledger != null && ledger.getNetCost() != null) {
            html.append("<div class=\"ledger-bar\">\n");
            html.append("  <strong>Cost &amp; Refund Ledger:</strong> Net Cost: ₹").append(ledger.getNetCost());
            if (ledger.getOutstandingRefunds() != null) {
                html.append(" | Outstanding Refunds: ₹").append(ledger.getOutstandingRefunds());
            }
            if (ledger.getRefundStatus() != null && !ledger.getRefundStatus().isBlank()) {
                html.append(" (").append(escape(ledger.getRefundStatus())).append(")");
            }
            if (ledger.getTimingNote() != null && !ledger.getTimingNote().isBlank()) {
                html.append(" &bull; <em>").append(escape(ledger.getTimingNote())).append("</em>");
            }
            html.append("\n</div>\n");
        }

        // Itinerary
        html.append("<div class=\"itinerary\">\n");
        if (sortedBookings.isEmpty()) {
            html.append("<p style=\"color: #64748b; font-style: italic; padding: 20px 0;\">No bookings in this trip itinerary.</p>\n");
        } else {
            for (Booking b : sortedBookings) {
                appendBookingCardHtml(html, b);
            }
        }
        html.append("</div>\n");

        html.append("<div class=\"footer\">Odyssey Travel Disruption Engine &bull; Generated for offline reference</div>\n");
        html.append("</div>\n</body>\n</html>");
        return html.toString();
    }

    /**
     * Helper to render one booking card in HTML with strictly separated blocks:
     * "Stored booking details" vs "Last known status (may be out of date)".
     */
    private void appendBookingCardHtml(StringBuilder html, Booking b) {
        String schedLabel = resolveScheduleLabel(b);
        String badgeClass = "badge-real";
        if ("GENERATED".equals(schedLabel)) badgeClass = "badge-gen";
        else if ("ESTIMATED".equals(schedLabel)) badgeClass = "badge-est";

        html.append("<div class=\"booking-card\">\n");
        html.append("  <div class=\"card-header\">\n");
        html.append("    <div class=\"card-title\">")
                .append(escape(b.getType() != null ? b.getType().name() : "BOOKING"))
                .append(" &bull; ")
                .append(escape(b.getProvider() != null ? b.getProvider() : "Provider"))
                .append("</div>\n");
        html.append("    <span class=\"badge ").append(badgeClass).append("\">").append(escape(schedLabel)).append("</span>\n");

        if (b.getProviderId() != null && providerRepo != null) {
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(b.getProviderId()).orElse(null);
            if (prov != null && prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED) {
                html.append("    <span class=\"badge\" style=\"background:#fee2e2;color:#991b1b;font-weight:bold;\">Provider suspended</span>\n");
            }
        }

        html.append("  </div>\n");

        html.append("  <div class=\"card-body\">\n");

        // Left column: Stored booking details
        html.append("    <div>\n");
        html.append("      <div class=\"section-label\">Stored booking details</div>\n");
        html.append("      <div class=\"field-row\"><span class=\"field-label\">Status:</span><span class=\"field-val\">")
                .append(escape(b.getStatus() != null ? b.getStatus().name() : "CONFIRMED")).append("</span></div>\n");

        if (b.getProviderId() != null && providerRepo != null) {
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(b.getProviderId()).orElse(null);
            if (prov != null) {
                html.append("      <div class=\"field-row\"><span class=\"field-label\">Provider contact:</span><span class=\"field-val\">")
                        .append(escape(prov.getContactEmail())).append(" / ")
                        .append(escape(EmergencyContactUtil.maskPhone(prov.getContactPhone()))).append("</span></div>\n");
            }
        }
        if (b.getBookingReference() != null && !b.getBookingReference().isBlank()) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Reference:</span><span class=\"field-val\">")
                    .append(escape(b.getBookingReference())).append("</span></div>\n");
        }
        if (b.getVehicleNumber() != null && !b.getVehicleNumber().isBlank()) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Vehicle:</span><span class=\"field-val\">")
                    .append(escape(b.getVehicleNumber())).append("</span></div>\n");
        }
        if (b.getSeatNumbers() != null && !b.getSeatNumbers().isBlank()) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Seats:</span><span class=\"field-val\">")
                    .append(escape(b.getSeatNumbers())).append("</span></div>\n");
        }
        if (b.getPassengerNames() != null && !b.getPassengerNames().isBlank()) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Passengers:</span><span class=\"field-val\">")
                    .append(escape(b.getPassengerNames())).append("</span></div>\n");
        }
        html.append("      <div class=\"field-row\"><span class=\"field-label\">From:</span><span class=\"field-val\">")
                .append(escape(b.getFrom())).append("</span></div>\n");
        html.append("      <div class=\"field-row\"><span class=\"field-label\">To:</span><span class=\"field-val\">")
                .append(escape(b.getTo())).append("</span></div>\n");
        if (b.getStartTime() != null) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Departure:</span><span class=\"field-val\">")
                    .append(escape(b.getStartTime().format(BOOKING_TIME_FORMATTER))).append("</span></div>\n");
        }
        if (b.getEndTime() != null) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Arrival:</span><span class=\"field-val\">")
                    .append(escape(b.getEndTime().format(BOOKING_TIME_FORMATTER))).append("</span></div>\n");
        }
        if (b.getCost() != null) {
            html.append("      <div class=\"field-row\"><span class=\"field-label\">Cost:</span><span class=\"field-val\">₹")
                    .append(b.getCost()).append("</span></div>\n");
        }
        html.append("    </div>\n");

        // Right column: Last known status (may be out of date)
        html.append("    <div>\n");
        html.append("      <div class=\"section-label\">Last known status (may be out of date)</div>\n");
        String stState = b.getLastStatusState() != null ? b.getLastStatusState() : "UNKNOWN";
        html.append("      <div class=\"field-row\"><span class=\"field-label\">State:</span><span class=\"field-val\">")
                .append(escape(stState)).append("</span></div>\n");
        int delay = b.getLastDelayMinutes() != null ? b.getLastDelayMinutes() : 0;
        html.append("      <div class=\"field-row\"><span class=\"field-label\">Delay:</span><span class=\"field-val\">")
                .append(delay).append(" mins</span></div>\n");
        String checkedAtStr = b.getLastCheckedAt() != null ? b.getLastCheckedAt().format(BOOKING_TIME_FORMATTER) : "Not checked";
        html.append("      <div class=\"field-row\"><span class=\"field-label\">Checked at:</span><span class=\"field-val\">")
                .append(escape(checkedAtStr)).append("</span></div>\n");
        String srcStr = b.getLastStatusSource() != null ? b.getLastStatusSource() : "UNKNOWN";
        html.append("      <div class=\"field-row\"><span class=\"field-label\">Source:</span><span class=\"field-val\">")
                .append(escape(srcStr)).append("</span></div>\n");
        html.append("    </div>\n");

        html.append("  </div>\n");
        html.append("</div>\n");
    }

    /**
     * Sanitizes filename for Content-Disposition header.
     * Uses only the trip id (alphanumeric and hyphens) and fixed prefix.
     */
    public String sanitizeFilename(String tripId, String extension) {
        String safeTripId = tripId != null ? tripId.replaceAll("[^a-zA-Z0-9_-]", "") : "trip";
        if (safeTripId.isBlank()) safeTripId = "trip";
        return "trip-" + safeTripId + extension;
    }

    /**
     * Sorts bookings chronologically by startTime ASC, breaking ties by id ASC.
     * Handles null start times safely (placed at end).
     */
    public List<Booking> getSortedBookings(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) return new ArrayList<>();
        return bookings.stream()
                .sorted(Comparator.comparing(Booking::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Booking::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Maps a Booking model to WalletBookingDto.
     */
    private WalletBookingDto toWalletBookingDto(Booking b) {
        String sourceLabel = resolveSourceLabel(b);
        String schedLabel = resolveScheduleLabel(b);

        LastKnownStatusDto statusDto = LastKnownStatusDto.builder()
                .state(b.getLastStatusState())
                .delayMinutes(b.getLastDelayMinutes() != null ? b.getLastDelayMinutes() : 0)
                .checkedAt(b.getLastCheckedAt())
                .source(b.getLastStatusSource() != null ? b.getLastStatusSource() : "UNKNOWN")
                .build();

        String providerStatus = null;
        String provContactEmail = null;
        String provContactPhone = null;
        if (b.getProviderId() != null && providerRepo != null) {
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(b.getProviderId()).orElse(null);
            if (prov != null) {
                if (prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED) {
                    providerStatus = "Provider suspended";
                }
                provContactEmail = prov.getContactEmail();
                provContactPhone = EmergencyContactUtil.maskPhone(prov.getContactPhone());
            }
        }

        return WalletBookingDto.builder()
                .id(b.getId())
                .type(b.getType())
                .provider(b.getProvider())
                .operator(b.getOperator())
                .vehicleNumber(b.getVehicleNumber())
                .from(b.getFrom())
                .to(b.getTo())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .seats(b.getSeatNumbers())
                .passengerNames(b.getPassengerNames())
                .bookingReference(b.getBookingReference())
                .source(sourceLabel)
                .status(b.getStatus() != null ? b.getStatus().name() : "CONFIRMED")
                .cost(b.getCost())
                .scheduleLabel(schedLabel)
                .lastKnownStatus(statusDto)
                .providerStatus(providerStatus)
                .providerContactEmail(provContactEmail)
                .providerContactPhone(provContactPhone)
                .build();
    }

    /**
     * Resolves booking source: MARKETPLACE, TYPED, or PARSED.
     */
    public String resolveSourceLabel(Booking b) {
        if (b == null) return "TYPED";
        if (isMarketplace(b)) return "MARKETPLACE";
        if (b.getSource() != null) {
            String s = b.getSource().toUpperCase();
            if (s.contains("PARSED")) return "PARSED";
            if (s.contains("MARKET")) return "MARKETPLACE";
        }
        return "TYPED";
    }

    /**
     * Checks if booking is from demo marketplace.
     */
    public boolean isMarketplace(Booking b) {
        if (b == null) return false;
        if (b.getDepartureId() != null && !b.getDepartureId().isBlank()) return true;
        if (b.getSource() != null && b.getSource().toUpperCase().contains("MARKET")) return true;
        return false;
    }

    /**
     * Resolves schedule label: REAL, GENERATED, or ESTIMATED.
     */
    public String resolveScheduleLabel(Booking b) {
        if (b == null) return "REAL";
        if (b.getSource() != null) {
            String s = b.getSource().toUpperCase();
            if (s.contains("ESTIMATED") || s.contains("SYNTHETIC")) return "ESTIMATED";
            if (s.contains("GENERATED")) return "GENERATED";
        }
        if (b.getScheduleLabel() != null) {
            String s = b.getScheduleLabel().toUpperCase();
            if (s.contains("ESTIMATED")) return "ESTIMATED";
            if (s.contains("GENERATED")) return "GENERATED";
        }
        return "REAL";
    }

    /**
     * Retrieves ledger summary line for a trip if ledger records exist.
     */
    private WalletLedgerSummaryDto extractLedgerSummary(Trip trip) {
        if (trip == null || trip.getId() == null) return null;
        if (!tripLedgerEntryJpaRepository.existsByTripId(trip.getId())) {
            return null;
        }
        try {
            TripLedgerResponse ledger = ledgerService.getLedger(trip);
            if (ledger != null && ledger.getSummary() != null) {
                var s = ledger.getSummary();
                return WalletLedgerSummaryDto.builder()
                        .netCost(s.getNetTotal())
                        .originalCost(s.getOriginalCost())
                        .replacementCost(s.getReplacementCost())
                        .outstandingRefunds(s.getOutstandingRefunds())
                        .refundStatus(s.getRefundReceiptLabel())
                        .timingNote(s.getTimingNote())
                        .isDemo(s.isDemo())
                        .build();
            }
        } catch (Exception ex) {
            log.debug("No ledger found or error fetching ledger for trip {}: {}", trip.getId(), ex.getMessage());
        }
        return null;
    }

    private String toUtcString(LocalDateTime ldt) {
        return ldt.atZone(ASIA_KOLKATA).withZoneSameInstant(ZoneOffset.UTC).format(ICS_DATE_FORMATTER);
    }

    private String escape(String input) {
        if (input == null) return "";
        return HtmlUtils.htmlEscape(input);
    }

    /**
     * Escapes special characters for RFC 5545 iCalendar values:
     * backslash -> \\
     * semicolon -> \;
     * comma -> \,
     * newlines -> \n
     */
    private String escapeIcsText(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Appends an ICS property, folding it according to RFC 5545 at 75 octets.
     */
    private void appendIcsField(StringBuilder sb, String name, String value) {
        String line = name + ":" + (value != null ? value : "");
        sb.append(foldIcsLine(line)).append("\r\n");
    }

    /**
     * Folds an iCalendar line so that no folded segment exceeds 75 UTF-8 octets.
     * Continuations start with a CRLF followed by a single space.
     */
    public String foldIcsLine(String line) {
        if (line == null || line.getBytes(StandardCharsets.UTF_8).length <= 75) {
            return line != null ? line : "";
        }

        StringBuilder folded = new StringBuilder();
        int lineByteCount = 0;
        int maxBytes = 75;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;

            if (lineByteCount + charBytes > maxBytes) {
                folded.append("\r\n ");
                lineByteCount = 1; // space counts as 1 byte
            }

            folded.append(c);
            lineByteCount += charBytes;
        }

        return folded.toString();
    }
}
