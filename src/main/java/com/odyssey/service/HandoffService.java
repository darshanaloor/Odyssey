package com.odyssey.service;

import com.odyssey.dto.response.HandoffLegDto;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final TripRepository tripRepository;
    private final BookingJpaRepository bookingJpaRepository;
    private final TripJpaRepository tripJpaRepository;

    @Value("${handoff.links.train:https://www.irctc.co.in/nget/train-search}")
    private String trainLinkTemplate;

    @Value("${handoff.links.bus-redbus:https://www.redbus.in/bus-tickets/{from}-to-{to}?date={date}}")
    private String busRedbusLinkTemplate;

    @Value("${handoff.links.bus-ksrtc:https://ksrtc.in/oprs-web/guest/home.do}")
    private String busKsrtcLinkTemplate;

    @Value("${handoff.links.flight:https://www.google.com/travel/flights?q=Flights%20to%20{to}%20from%20{from}%20on%20{date}}")
    private String flightLinkTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<HandoffLegDto> getHandoffLegsForTrip(Trip trip) {
        if (trip == null || trip.getBookings() == null) {
            return List.of();
        }

        // Identify new recovery legs (BUS, TRAIN, FLIGHT with status REBOOKED or BOOKED)
        // or legs from latestPlan.newLeg
        String newLegId = (trip.getLatestPlan() != null && trip.getLatestPlan().getNewLeg() != null)
                ? trip.getLatestPlan().getNewLeg().getId()
                : null;

        List<Booking> handoffBookings = trip.getBookings().stream()
                .filter(b -> (b.getType() == BookingType.BUS || b.getType() == BookingType.TRAIN || b.getType() == BookingType.FLIGHT))
                .filter(b -> b.getStatus() == BookingStatus.BOOKED || b.getStatus() == BookingStatus.REBOOKED
                        || (newLegId != null && newLegId.equals(b.getId())))
                .filter(b -> !"MARKETPLACE".equalsIgnoreCase(b.getSource()) && b.getDepartureId() == null)
                .collect(Collectors.toList());

        BigDecimal totalRecoveryCost = BigDecimal.ZERO;
        if (trip.getLatestPlan() != null
                && trip.getLatestPlan().getCostBreakdown() != null
                && trip.getLatestPlan().getCostBreakdown().getNetTripCost() != null) {
            totalRecoveryCost = trip.getLatestPlan().getCostBreakdown().getNetTripCost();
        } else {
            totalRecoveryCost = trip.getBookings().stream()
                    .filter(b -> b.getStatus() == BookingStatus.REBOOKED || b.getStatus() == BookingStatus.BOOKED)
                    .map(b -> b.getCost() != null ? b.getCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        final BigDecimal finalTotalRecoveryCost = totalRecoveryCost;
        return handoffBookings.stream()
                .map(b -> toHandoffLegDto(b, finalTotalRecoveryCost))
                .collect(Collectors.toList());
    }

    public HandoffLegDto toHandoffLegDto(Booking b) {
        return toHandoffLegDto(b, b.getCost() != null ? b.getCost() : BigDecimal.ZERO);
    }

    public HandoffLegDto toHandoffLegDto(Booking b, BigDecimal totalRecoveryCost) {
        List<HandoffLegDto.HandoffLink> links = buildLinks(b);
        boolean isBooked = b.getStatus() == BookingStatus.BOOKED;

        return HandoffLegDto.builder()
                .bookingId(b.getId())
                .type(b.getType())
                .provider(b.getProvider())
                .from(b.getFrom())
                .to(b.getTo())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .status(b.getStatus())
                .bookingReference(b.getBookingReference())
                .booked(isBooked)
                .cost(b.getCost())
                .totalRecoveryCost(totalRecoveryCost)
                .links(links)
                .scheduleNote("Estimated schedule, please verify on the official site before booking.")
                .build();
    }

    public List<HandoffLegDto.HandoffLink> buildLinks(Booking b) {
        List<HandoffLegDto.HandoffLink> links = new ArrayList<>();
        String fromClean = cleanLocation(b.getFrom());
        String toClean = cleanLocation(b.getTo());
        String dateStr = b.getStartTime() != null ? b.getStartTime().format(DATE_FMT) : "";

        if (b.getType() == BookingType.TRAIN) {
            links.add(HandoffLegDto.HandoffLink.builder()
                    .label("IRCTC Train Search")
                    .url(trainLinkTemplate)
                    .prefilled(false)
                    .build());
        } else if (b.getType() == BookingType.BUS) {
            String redbusUrl = busRedbusLinkTemplate
                    .replace("{from}", urlEncode(fromClean.toLowerCase()))
                    .replace("{to}", urlEncode(toClean.toLowerCase()))
                    .replace("{date}", dateStr);

            links.add(HandoffLegDto.HandoffLink.builder()
                    .label("redBus Search")
                    .url(redbusUrl)
                    .prefilled(true)
                    .build());

            links.add(HandoffLegDto.HandoffLink.builder()
                    .label("KSRTC Portal")
                    .url(busKsrtcLinkTemplate)
                    .prefilled(false)
                    .build());
        } else if (b.getType() == BookingType.FLIGHT) {
            String flightUrl = flightLinkTemplate
                    .replace("{from}", urlEncode(fromClean))
                    .replace("{to}", urlEncode(toClean))
                    .replace("{date}", dateStr);

            links.add(HandoffLegDto.HandoffLink.builder()
                    .label("Google Flights Search")
                    .url(flightUrl)
                    .prefilled(true)
                    .build());
        }

        return links;
    }

    @Transactional
    public Trip markBookingBooked(String tripId, String bookingId, String reference) {
        return markBookingBooked(tripId, bookingId, reference, null, null);
    }

    @Transactional
    public Trip markBookingBooked(String tripId, String bookingId, String reference, String paymentMethod, BigDecimal paidAmount) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        Booking target = trip.getBookings().stream()
                .filter(b -> b.getId().equals(bookingId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        target.setStatus(BookingStatus.BOOKED);
        target.setBookingReference(reference != null && !reference.isBlank() ? reference.trim() : null);
        String resolvedMethod = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.trim().toUpperCase() : "CARD";
        target.setPaymentMethod(resolvedMethod);
        target.setPaymentStatus("DEMO_PAID");

        String paymentNote = (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0)
                ? String.format(", paid Rs.%s via %s", paidAmount.toPlainString(), resolvedMethod)
                : "";

        // Record audit entry with actor USER
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKING_MARKED_BOOKED")
                .details(String.format("User confirmed booking %s (%s %s -> %s) with reference: %s%s",
                        target.getId(), target.getType(), target.getFrom(), target.getTo(),
                        target.getBookingReference() != null ? target.getBookingReference() : "None",
                        paymentNote))
                .build());

        // Check if all handoff legs are BOOKED
        List<HandoffLegDto> legs = getHandoffLegsForTrip(trip);
        boolean allBooked = !legs.isEmpty() && legs.stream().allMatch(HandoffLegDto::isBooked);
        if (allBooked) {
            trip.setStatus("COMPLETED_BOOKINGS");
        }

        return tripRepository.save(trip);
    }

    @Transactional
    public Trip unmarkBookingBooked(String tripId, String bookingId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        Booking target = trip.getBookings().stream()
                .filter(b -> b.getId().equals(bookingId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        target.setStatus(BookingStatus.REBOOKED);
        target.setBookingReference(null);

        // Record audit entry with actor USER
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKING_UNMARKED_BOOKED")
                .details(String.format("User reverted booking status for %s (%s)", target.getId(), target.getType()))
                .build());

        // If trip was COMPLETED_BOOKINGS, move back to RESOLVED
        if ("COMPLETED_BOOKINGS".equalsIgnoreCase(trip.getStatus())) {
            trip.setStatus("RESOLVED");
        }

        return tripRepository.save(trip);
    }

    private String cleanLocation(String loc) {
        if (loc == null) return "";
        // Strip IATA codes or paren notes like "BLR (Kempegowda...)" -> "BLR" or city name
        int idx = loc.indexOf('(');
        if (idx > 0) {
            return loc.substring(0, idx).trim();
        }
        return loc.trim();
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
