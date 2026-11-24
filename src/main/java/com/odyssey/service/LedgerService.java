package com.odyssey.service;

import com.odyssey.config.RefundProperties;
import com.odyssey.dto.request.RecordRefundRequest;
import com.odyssey.dto.request.RefundDeductionRequest;
import com.odyssey.dto.request.ShareRefundRequest;
import com.odyssey.dto.response.*;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.TripLedgerEntryEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.exception.RateLimitException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.Booking;
import com.odyssey.model.CostBreakdown;
import com.odyssey.model.CostBreakdownLine;
import com.odyssey.model.NotificationMessage;
import com.odyssey.model.RecoveryPlan;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.*;
import com.odyssey.notification.EmailChannel;
import com.odyssey.notification.SendResult;
import com.odyssey.notification.SmsChannel;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.repository.jpa.TripLedgerEntryJpaRepository;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing the cost and refund ledger, synchronizing plan breakdown figures,
 * validating and recording user-reported refunds with deductions (append-only),
 * generating per-booking breakdowns, sharing refund summaries, and exporting CSV reports.
 */
@Slf4j
@Service
public class LedgerService {

    private final TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository;
    private final TripJpaRepository tripJpaRepository;
    private final AuditEntryJpaRepository auditEntryJpaRepository;

    @Autowired(required = false)
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired(required = false)
    private EmailChannel emailChannel;

    @Autowired(required = false)
    private SmsChannel smsChannel;

    @Autowired(required = false)
    private RefundProperties refundProperties = new RefundProperties();

    public LedgerService(
            TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository,
            TripJpaRepository tripJpaRepository,
            AuditEntryJpaRepository auditEntryJpaRepository) {
        this.tripLedgerEntryJpaRepository = tripLedgerEntryJpaRepository;
        this.tripJpaRepository = tripJpaRepository;
        this.auditEntryJpaRepository = auditEntryJpaRepository;
    }

    @Autowired
    public LedgerService(
            TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository,
            TripJpaRepository tripJpaRepository,
            AuditEntryJpaRepository auditEntryJpaRepository,
            @Autowired(required = false) NotificationJpaRepository notificationJpaRepository,
            @Autowired(required = false) EmailChannel emailChannel,
            @Autowired(required = false) SmsChannel smsChannel,
            @Autowired(required = false) RefundProperties refundProperties) {
        this.tripLedgerEntryJpaRepository = tripLedgerEntryJpaRepository;
        this.tripJpaRepository = tripJpaRepository;
        this.auditEntryJpaRepository = auditEntryJpaRepository;
        this.notificationJpaRepository = notificationJpaRepository;
        this.emailChannel = emailChannel;
        this.smsChannel = smsChannel;
        this.refundProperties = refundProperties != null ? refundProperties : new RefundProperties();
    }

    private RefundProperties getRefundProps() {
        return this.refundProperties != null ? this.refundProperties : new RefundProperties();
    }

    @Transactional
    public TripLedgerResponse getLedger(Trip trip) {
        syncPlanLedger(trip, trip.getLatestPlan());
        return assembleResponse(trip);
    }

    @Transactional
    public void syncPlanLedger(Trip trip, RecoveryPlan plan) {
        if (trip == null) return;
        List<TripLedgerEntryEntity> existing = tripLedgerEntryJpaRepository.findByTripIdOrderByCreatedAtAsc(trip.getId());

        List<TripLedgerEntryEntity> previousPlanEntries = existing.stream()
                .filter(e -> e.getType() != LedgerEntryType.REFUND_RECORDED
                        && e.getType() != LedgerEntryType.REFUND
                        && e.getType() != LedgerEntryType.REFUND_DEDUCTION)
                .collect(Collectors.toList());

        TripEntity tripRef = tripJpaRepository.getReferenceById(trip.getId());
        List<TripLedgerEntryEntity> newPlanEntries = (plan != null && plan.getCostBreakdown() != null
                && plan.getCostBreakdown().getLines() != null && !plan.getCostBreakdown().getLines().isEmpty())
                ? buildPlanDerivedEntities(trip, plan, tripRef)
                : buildOriginalBookingEntities(trip, tripRef);

        if (previousPlanEntries.isEmpty()) {
            for (TripLedgerEntryEntity pe : newPlanEntries) {
                pe.setId(IdGenerator.generate("LED"));
            }
            tripLedgerEntryJpaRepository.saveAll(newPlanEntries);
            tripLedgerEntryJpaRepository.flush();
        } else {
            boolean matches = previousPlanEntries.size() == newPlanEntries.size();
            if (matches) {
                for (int i = 0; i < previousPlanEntries.size(); i++) {
                    TripLedgerEntryEntity oldE = previousPlanEntries.get(i);
                    TripLedgerEntryEntity newE = newPlanEntries.get(i);
                    if (oldE.getType() != newE.getType()
                            || oldE.getAmount().compareTo(newE.getAmount()) != 0
                            || oldE.getBasis() != newE.getBasis()) {
                        matches = false;
                        break;
                    }
                }
            }
            if (!matches) {
                tripLedgerEntryJpaRepository.deleteAll(previousPlanEntries);
                tripLedgerEntryJpaRepository.flush();
                for (TripLedgerEntryEntity pe : newPlanEntries) {
                    pe.setId(IdGenerator.generate("LED"));
                }
                tripLedgerEntryJpaRepository.saveAll(newPlanEntries);
                tripLedgerEntryJpaRepository.flush();
            }
        }
    }

    private List<TripLedgerEntryEntity> buildOriginalBookingEntities(Trip trip, TripEntity tripRef) {
        List<TripLedgerEntryEntity> list = new ArrayList<>();
        for (Booking b : trip.getBookings()) {
            if (b.getStatus() != BookingStatus.CANCELLED) {
                BigDecimal cost = b.getCost() != null ? b.getCost().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                list.add(TripLedgerEntryEntity.builder()
                        .id(IdGenerator.generate("LED"))
                        .trip(tripRef)
                        .bookingId(b.getId())
                        .type(LedgerEntryType.ORIGINAL_COST)
                        .amount(cost)
                        .basis(LedgerBasis.RECORDED)
                        .note("Booking: " + b.getProvider() + " (" + b.getType() + ")")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
        return list;
    }

    @Transactional
    public TripLedgerResponse recordRefund(Trip trip, RecordRefundRequest request) {
        BigDecimal gross = request.resolveGrossAmount();
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gross refund amount must be greater than zero");
        }

        LocalDate receivedOn = request.getReceivedOn() != null ? request.getReceivedOn() : LocalDate.now();
        if (receivedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Refund received date cannot be in the future");
        }

        if (request.getReference() != null && request.getReference().trim().length() > 60) {
            throw new IllegalArgumentException("Reference must not exceed 60 characters");
        }

        Booking booking = trip.getBookings().stream()
                .filter(b -> b.getId().equalsIgnoreCase(request.getBookingId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found on trip: " + request.getBookingId()));

        BigDecimal bookingCost = booking.getCost() != null
                ? booking.getCost().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        if (bookingCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Booking has no recorded cost for refund");
        }

        gross = gross.setScale(2, RoundingMode.HALF_UP);
        if (gross.compareTo(bookingCost) > 0) {
            throw new IllegalArgumentException(String.format("Gross refund amount Rs %s cannot exceed booking cost of Rs %s",
                    gross, bookingCost));
        }

        // Deductions validation
        List<RefundDeductionRequest> deductions = request.getDeductions() != null
                ? request.getDeductions()
                : Collections.emptyList();

        BigDecimal totalDeductions = BigDecimal.ZERO;
        for (RefundDeductionRequest d : deductions) {
            if (d.getCategory() == null) {
                throw new IllegalArgumentException("Deduction category is required");
            }
            if (d.getAmount() == null || d.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Each deduction amount must be greater than zero");
            }
            if (d.getDescription() == null || d.getDescription().trim().length() < 5 || d.getDescription().trim().length() > 200) {
                throw new IllegalArgumentException("Deduction description is required (5 to 200 characters)");
            }
            totalDeductions = totalDeductions.add(d.getAmount());
        }
        totalDeductions = totalDeductions.setScale(2, RoundingMode.HALF_UP);

        if (totalDeductions.compareTo(gross) > 0) {
            throw new IllegalArgumentException("Total deductions cannot exceed gross refund amount");
        }

        BigDecimal netRefund = gross.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);
        if (netRefund.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Net refund cannot be negative");
        }

        // A reduced refund must always be explained: if net is less than the booking cost, at least one deduction with a description is required.
        boolean isExtendedRefundApi = request.getGrossAmount() != null;
        if (isExtendedRefundApi && netRefund.compareTo(bookingCost) < 0 && deductions.isEmpty()) {
            throw new IllegalArgumentException("A reduced refund must be explained: at least one deduction with a description is required when net refund is less than the booking cost");
        }

        // Cumulative validation: total net recorded refunds cannot exceed booking cost
        List<TripLedgerEntryEntity> existingEntries = tripLedgerEntryJpaRepository.findByTripIdOrderByCreatedAtAsc(trip.getId());
        BigDecimal cumulativeNetRecorded = BigDecimal.ZERO;
        for (TripLedgerEntryEntity e : existingEntries) {
            if (booking.getId().equalsIgnoreCase(e.getBookingId())) {
                if (e.getType() == LedgerEntryType.REFUND || e.getType() == LedgerEntryType.REFUND_RECORDED) {
                    cumulativeNetRecorded = cumulativeNetRecorded.add(e.getAmount());
                } else if (e.getType() == LedgerEntryType.REFUND_DEDUCTION) {
                    cumulativeNetRecorded = cumulativeNetRecorded.subtract(e.getAmount());
                }
            }
        }

        if (cumulativeNetRecorded.add(netRefund).compareTo(bookingCost) > 0) {
            throw new IllegalArgumentException(String.format("Total recorded refunds (Rs %s) cannot exceed booking cost of Rs %s",
                    cumulativeNetRecorded.add(netRefund).setScale(2, RoundingMode.HALF_UP), bookingCost));
        }

        RefundStatus status = request.getStatus() != null ? request.getStatus() : RefundStatus.RECEIVED;
        String statusLabel = status == RefundStatus.RECEIVED ? "User-reported, not verified" : status.name();

        TripEntity tripRef = tripJpaRepository.getReferenceById(trip.getId());
        String mainEntryId = IdGenerator.generate("LED");
        String note = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : statusLabel;
        String reference = (request.getReference() != null && !request.getReference().isBlank())
                ? request.getReference().trim()
                : null;

        LocalDateTime now = LocalDateTime.now();

        // Save main refund entry (gross amount)
        TripLedgerEntryEntity mainEntry = TripLedgerEntryEntity.builder()
                .id(mainEntryId)
                .trip(tripRef)
                .bookingId(booking.getId())
                .type(LedgerEntryType.REFUND_RECORDED)
                .amount(gross)
                .basis(LedgerBasis.USER_REPORTED)
                .refundStatus(status.name())
                .note(note)
                .reference(reference)
                .receivedOn(receivedOn)
                .createdAt(now)
                .build();
        tripLedgerEntryJpaRepository.save(mainEntry);

        // Save deduction entries in stable creation order
        for (int i = 0; i < deductions.size(); i++) {
            RefundDeductionRequest d = deductions.get(i);
            TripLedgerEntryEntity dedEntry = TripLedgerEntryEntity.builder()
                    .id(IdGenerator.generate("LED"))
                    .trip(tripRef)
                    .bookingId(booking.getId())
                    .type(LedgerEntryType.REFUND_DEDUCTION)
                    .amount(d.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .basis(LedgerBasis.USER_REPORTED)
                    .category(d.getCategory().name())
                    .description(d.getDescription().trim())
                    .parentEntryId(mainEntryId)
                    .refundStatus(status.name())
                    .createdAt(now.plusNanos((i + 1) * 1_000_000L))
                    .build();
            tripLedgerEntryJpaRepository.save(dedEntry);
        }

        // Audit entry
        auditEntryJpaRepository.save(AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(tripRef)
                .bookingId(booking.getId())
                .userId(trip.getUserId())
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER.name())
                .action("REFUND_RECORDED")
                .details(String.format("Recorded refund for booking %s: Gross Rs %s, Net Rs %s (%d deductions), status=%s",
                        booking.getId(), gross, netRefund, deductions.size(), status.name()))
                .build());

        return assembleResponse(trip);
    }

    @Transactional
    public ShareRefundResponse shareRefund(Trip trip, String entryId, ShareRefundRequest request, UserEntity currentUser) {
        if (request == null || !Boolean.TRUE.equals(request.getConfirm())) {
            throw new IllegalArgumentException("confirm must be true to share refund summary");
        }

        // Rate limit: 10 shares per hour per trip
        if (notificationJpaRepository != null) {
            long count = notificationJpaRepository.countByTripIdAndEventTypeAndCreatedAtAfter(
                    trip.getId(), "REFUND_SUMMARY", LocalDateTime.now().minusHours(1));
            if (count >= 10) {
                throw new RateLimitException("Rate limit reached: Maximum 10 refund shares per hour for this trip");
            }
        }

        TripLedgerEntryEntity rawEntry = tripLedgerEntryJpaRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found: " + entryId));

        if (!rawEntry.getTrip().getId().equals(trip.getId())) {
            throw new ResourceNotFoundException("Ledger entry not found on this trip: " + entryId);
        }

        final TripLedgerEntryEntity entry = (rawEntry.getType() == LedgerEntryType.REFUND_DEDUCTION && rawEntry.getParentEntryId() != null)
                ? tripLedgerEntryJpaRepository.findById(rawEntry.getParentEntryId()).orElse(rawEntry)
                : rawEntry;

        final String targetBookingId = entry.getBookingId();
        Booking booking = (targetBookingId != null) ? trip.getBookings().stream()
                .filter(b -> targetBookingId.equalsIgnoreCase(b.getId()))
                .findFirst()
                .orElse(null) : null;

        List<TripLedgerEntryEntity> deductionEntities = (entry.getId() != null)
                ? tripLedgerEntryJpaRepository.findByParentEntryId(entry.getId()).stream()
                        .sorted(Comparator.comparing(TripLedgerEntryEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(TripLedgerEntryEntity::getId))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        BigDecimal grossAmount = entry.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = deductionEntities.stream()
                .map(TripLedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netRefund = grossAmount.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);

        boolean isDemo = isDemoOrMarketplaceBooking(booking, trip);
        String timingNote = isDemo ? getRefundProps().getDemoNote() : getRefundProps().getNote();

        String bookingRef = booking != null && booking.getBookingReference() != null
                ? booking.getBookingReference()
                : (entry.getReference() != null ? entry.getReference() : "N/A");
        String provider = booking != null ? booking.getProvider() : "Operator";
        String route = (booking != null && booking.getFrom() != null && booking.getTo() != null)
                ? (booking.getFrom() + " -> " + booking.getTo())
                : "N/A";
        BigDecimal origCost = booking != null && booking.getCost() != null ? booking.getCost() : grossAmount;
        String travellerName = currentUser != null && currentUser.getName() != null ? currentUser.getName() : "Traveller";
        String refundStatus = entry.getRefundStatus() != null ? entry.getRefundStatus() : "RECEIVED";

        // Build plain text message (no emoji, deductions listed in stable creation order)
        StringBuilder sb = new StringBuilder();
        sb.append("Odyssey Refund Summary\n");
        sb.append("Trip: ").append(trip.getTitle()).append("\n");
        sb.append("Traveller: ").append(travellerName).append("\n");
        sb.append("Booking Reference: ").append(bookingRef).append("\n");
        sb.append("Provider: ").append(provider).append("\n");
        sb.append("Route: ").append(route).append("\n");
        sb.append("Original Amount: Rs ").append(origCost.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Gross Refund: Rs ").append(grossAmount.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Deductions:\n");
        if (deductionEntities.isEmpty()) {
            sb.append("None\n");
        } else {
            for (TripLedgerEntryEntity d : deductionEntities) {
                sb.append(String.format("- %s: Rs %s (%s)\n",
                        d.getCategory(), d.getAmount().setScale(2, RoundingMode.HALF_UP), d.getDescription()));
            }
        }
        sb.append("Net Refund: Rs ").append(netRefund.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Status: ").append(refundStatus).append(" (User-reported, not verified)\n");
        sb.append("Timing: ").append(timingNote).append("\n");
        sb.append("Amounts are user-reported and not verified by the operator.\n");
        if (isDemo) {
            sb.append("Demo refund. No real money is moved. Demo marketplace. No real ticket is issued and no money was charged.\n");
        }
        String fullMessage = sb.toString().trim();

        // Condensed SMS message (<= 320 chars, stable deduction listing)
        String smsTiming = isDemo
                ? "Demo refund, no real money moved."
                : "Refund typically takes a few working days, estimate only.";
        List<String> dedList = new ArrayList<>();
        for (TripLedgerEntryEntity d : deductionEntities) {
            dedList.add(d.getCategory() + " (Rs " + d.getAmount().setScale(0, RoundingMode.HALF_UP) + ")");
        }
        String dedSummary = dedList.isEmpty() ? "0" : String.join(", ", dedList);
        String smsMessage = String.format("Odyssey Refund: %s, Net: Rs %s, Deductions: [%s]. %s User-reported, not verified.",
                bookingRef, netRefund.setScale(2, RoundingMode.HALF_UP), dedSummary, smsTiming);

        if (smsMessage.length() > 320) {
            smsMessage = String.format("Odyssey Refund: %s, Net: Rs %s, Deductions: %d. %s User-reported, not verified.",
                    bookingRef, netRefund.setScale(2, RoundingMode.HALF_UP), deductionEntities.size(), smsTiming);
            if (smsMessage.length() > 320) {
                smsMessage = smsMessage.substring(0, 317) + "...";
            }
        }

        String recipient = null;
        String recipientMasked = "N/A";
        String status = "SENT";
        String statusLabel = "Sent";
        String mailtoUrl = null;
        String sendError = null;

        ShareChannel channel = request.getChannel();
        if (channel == ShareChannel.EMAIL) {
            String rawEmail = (request.getRecipient() != null && !request.getRecipient().isBlank())
                    ? request.getRecipient().trim()
                    : (currentUser != null ? currentUser.getEmail() : null);

            if (request.getRecipient() != null && !request.getRecipient().isBlank() && !NotificationMasker.isValidEmail(rawEmail)) {
                throw new IllegalArgumentException("Invalid email format: " + rawEmail);
            }

            if (rawEmail == null || !NotificationMasker.isValidEmail(rawEmail)) {
                status = "SKIPPED";
                statusLabel = "Skipped - no valid recipient";
                sendError = "No valid email address available";
            } else {
                recipient = rawEmail;
                recipientMasked = NotificationMasker.maskEmail(recipient);

                if (emailChannel != null) {
                    try {
                        NotificationMessage msg = NotificationMessage.builder()
                                .tripId(trip.getId())
                                .eventType("REFUND_SUMMARY")
                                .channel("EMAIL")
                                .recipient(recipient)
                                .subject("Odyssey Refund Summary: " + bookingRef)
                                .message(fullMessage)
                                .build();
                        SendResult sendResult = emailChannel.send(msg);
                        status = sendResult.getStatus() != null ? sendResult.getStatus().name() : "SENT";
                        statusLabel = "SIMULATED".equalsIgnoreCase(status) ? "Simulated, not sent" : status;
                        sendError = sendResult.getError();
                    } catch (Exception e) {
                        log.warn("Email dispatch failed for {}: {}", recipientMasked, e.getMessage());
                        status = "FAILED";
                        statusLabel = "Failed";
                        sendError = e.getMessage();
                    }
                } else {
                    status = "SIMULATED";
                    statusLabel = "Simulated, not sent";
                }
            }
        } else if (channel == ShareChannel.SMS) {
            String rawPhone = (request.getRecipient() != null && !request.getRecipient().isBlank())
                    ? request.getRecipient().trim()
                    : (currentUser != null ? currentUser.getPhone() : null);

            if (request.getRecipient() != null && !request.getRecipient().isBlank() && NotificationMasker.normalizePhone(rawPhone) == null) {
                throw new IllegalArgumentException("Invalid phone number format: " + rawPhone + ". Must be 10 digits");
            }

            String normalizedPhone = NotificationMasker.normalizePhone(rawPhone);
            if (normalizedPhone == null) {
                status = "SKIPPED";
                statusLabel = "Skipped - no valid phone number";
                sendError = "No valid phone number available";
            } else {
                recipient = normalizedPhone;
                recipientMasked = NotificationMasker.maskPhone(recipient);

                if (smsChannel != null) {
                    try {
                        NotificationMessage msg = NotificationMessage.builder()
                                .tripId(trip.getId())
                                .eventType("REFUND_SUMMARY")
                                .channel("SMS")
                                .recipient(recipient)
                                .subject("Odyssey Refund Summary: " + bookingRef)
                                .message(smsMessage)
                                .build();
                        SendResult sendResult = smsChannel.send(msg);
                        status = sendResult.getStatus() != null ? sendResult.getStatus().name() : "SENT";
                        statusLabel = "SIMULATED".equalsIgnoreCase(status) ? "Simulated, not sent" : status;
                        sendError = sendResult.getError();
                    } catch (Exception e) {
                        log.warn("SMS dispatch failed for {}: {}", recipientMasked, e.getMessage());
                        status = "FAILED";
                        statusLabel = "Failed";
                        sendError = e.getMessage();
                    }
                } else {
                    status = "SIMULATED";
                    statusLabel = "Simulated, not sent";
                }
            }
        } else if (channel == ShareChannel.MAILTO) {
            recipient = (request.getRecipient() != null && !request.getRecipient().isBlank())
                    ? request.getRecipient().trim()
                    : (currentUser != null ? currentUser.getEmail() : "");
            recipientMasked = NotificationMasker.isValidEmail(recipient) ? NotificationMasker.maskEmail(recipient) : (recipient != null && !recipient.isBlank() ? recipient : "N/A");
            mailtoUrl = "mailto:" + recipient + "?subject="
                    + URLEncoder.encode("Odyssey Refund Summary: " + bookingRef, StandardCharsets.UTF_8)
                    + "&body=" + URLEncoder.encode(fullMessage, StandardCharsets.UTF_8);
            status = "LINK_CREATED";
            statusLabel = "Not sent by Odyssey";
        } else if (channel == ShareChannel.COPY) {
            status = "COPIED";
            statusLabel = "Not sent by Odyssey";
            recipientMasked = "Clipboard";
        }

        // Record in notifications table
        if (notificationJpaRepository != null) {
            try {
                TripEntity tripRef = tripJpaRepository.getReferenceById(trip.getId());
                NotificationEntity notif = NotificationEntity.builder()
                        .id(IdGenerator.generate("NOTIF"))
                        .trip(tripRef)
                        .eventType("REFUND_SUMMARY")
                        .channel(channel.name())
                        .recipientMasked(recipientMasked)
                        .subject("Odyssey Refund Summary: " + bookingRef)
                        .body(channel == ShareChannel.SMS ? smsMessage : fullMessage)
                        .status(status)
                        .error(sendError)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationJpaRepository.save(notif);
            } catch (Exception e) {
                log.warn("Failed to persist notification for refund summary share: {}", e.getMessage());
            }
        }

        // Record in audit log
        try {
            TripEntity tripRef = tripJpaRepository.getReferenceById(trip.getId());
            auditEntryJpaRepository.save(AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .trip(tripRef)
                    .bookingId(booking != null ? booking.getId() : null)
                    .userId(trip.getUserId())
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.USER.name())
                    .action("REFUND_SHARED")
                    .details(String.format("Shared refund summary for entry %s via %s to %s (status: %s)",
                            entryId, channel.name(), recipientMasked, status))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist audit entry for refund summary share: {}", e.getMessage());
        }

        String contentText = (channel == ShareChannel.SMS) ? smsMessage : fullMessage;
        return ShareRefundResponse.builder()
                .channel(channel.name())
                .recipientMasked(recipientMasked)
                .status(status)
                .statusLabel(statusLabel)
                .mailtoUrl(mailtoUrl)
                .message(contentText)
                .content(contentText)
                .error(sendError)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public String exportCsv(Trip trip) {
        TripLedgerResponse ledger = getLedger(trip);
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Trip ID,Booking ID,Type,Amount (INR),Basis,Note,Reference,Received On,Created At,Category,Description,Status\r\n");

        for (LedgerEntryDto e : ledger.getEntries()) {
            sb.append(escapeCsv(e.getId())).append(",")
                    .append(escapeCsv(trip.getId())).append(",")
                    .append(escapeCsv(e.getBookingId())).append(",")
                    .append(escapeCsv(e.getType() != null ? e.getType().name() : "")).append(",")
                    .append(e.getAmount() != null ? e.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00").append(",")
                    .append(escapeCsv(e.getBasis() != null ? e.getBasis().name() : "")).append(",")
                    .append(escapeCsv(e.getNote())).append(",")
                    .append(escapeCsv(e.getReference())).append(",")
                    .append(escapeCsv(e.getReceivedOn() != null ? e.getReceivedOn().toString() : "")).append(",")
                    .append(escapeCsv(e.getCreatedAt() != null ? e.getCreatedAt().toString() : "")).append(",")
                    .append(escapeCsv(e.getCategory())).append(",")
                    .append(escapeCsv(e.getDescription())).append(",")
                    .append(escapeCsv(e.getRefundStatus()))
                    .append("\r\n");
        }
        return sb.toString();
    }

    private List<TripLedgerEntryEntity> buildPlanDerivedEntities(Trip trip, RecoveryPlan plan, TripEntity tripRef) {
        List<TripLedgerEntryEntity> result = new ArrayList<>();
        CostBreakdown cb = plan.getCostBreakdown();
        if (cb == null || cb.getLines() == null) return result;

        int idx = 1;
        for (CostBreakdownLine line : cb.getLines()) {
            BigDecimal amt = line.getAmount() != null
                    ? line.getAmount().abs().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);

            if (line.isDeduction()) {
                // Expected refund
                Booking matchedBooking = findCancelledBookingForRefund(trip.getBookings(), line, amt);
                String bookingId = matchedBooking != null ? matchedBooking.getId() : null;
                LedgerBasis basis = (matchedBooking != null && "DEMO_REFUNDED".equalsIgnoreCase(matchedBooking.getPaymentStatus()))
                        ? LedgerBasis.DEMO
                        : LedgerBasis.ESTIMATED;

                String note = (line.getAssumptionNote() != null && !line.getAssumptionNote().isBlank())
                        ? line.getAssumptionNote()
                        : (line.getLabel() != null ? line.getLabel() : "Expected refund");

                result.add(TripLedgerEntryEntity.builder()
                        .id("LED-" + trip.getId() + "-REF-" + (idx++))
                        .trip(tripRef)
                        .bookingId(bookingId)
                        .type(LedgerEntryType.REFUND_EXPECTED)
                        .amount(amt)
                        .basis(basis)
                        .refundStatus(RefundStatus.EXPECTED.name())
                        .note(note)
                        .createdAt(LocalDateTime.now())
                        .build());
            } else {
                // Cost item
                String labelLower = line.getLabel() != null ? line.getLabel().toLowerCase() : "";
                if (labelLower.contains("replacement cab")
                        || (labelLower.contains("cab") && plan.getReplacementCab() != null && plan.getReplacementCab().getCost() != null && plan.getReplacementCab().getCost().setScale(2, RoundingMode.HALF_UP).equals(amt))) {
                    result.add(TripLedgerEntryEntity.builder()
                        .id("LED-" + trip.getId() + "-CAB-" + (idx++))
                        .trip(tripRef)
                        .bookingId(plan.getReplacementCab() != null ? plan.getReplacementCab().getId() : null)
                        .type(LedgerEntryType.EXTRA_COST)
                        .amount(amt)
                        .basis(LedgerBasis.ESTIMATED)
                        .note(line.getAssumptionNote() != null ? line.getAssumptionNote() : line.getLabel())
                        .createdAt(LocalDateTime.now())
                        .build());
                } else if (labelLower.contains("new")
                        || (plan.getNewLeg() != null && plan.getNewLeg().getCost() != null && plan.getNewLeg().getCost().setScale(2, RoundingMode.HALF_UP).equals(amt))) {
                    Booking newLeg = plan.getNewLeg();
                    LedgerBasis repBasis = determineReplacementCostBasis(trip, newLeg);
                    result.add(TripLedgerEntryEntity.builder()
                        .id("LED-" + trip.getId() + "-REP-" + (idx++))
                        .trip(tripRef)
                        .bookingId(newLeg != null ? newLeg.getId() : null)
                        .type(LedgerEntryType.REPLACEMENT_COST)
                        .amount(amt)
                        .basis(repBasis)
                        .note(line.getAssumptionNote() != null ? line.getAssumptionNote() : line.getLabel())
                        .createdAt(LocalDateTime.now())
                        .build());
                } else {
                    // Original confirmed booking (Hotel, Event, etc.)
                    Booking matched = trip.getBookings().stream()
                            .filter(b -> b.getCost() != null
                                    && b.getCost().setScale(2, RoundingMode.HALF_UP).equals(amt)
                                    && b.getStatus() != BookingStatus.CANCELLED
                                    && (plan.getNewLeg() == null || !b.getId().equalsIgnoreCase(plan.getNewLeg().getId()))
                                    && (plan.getReplacementCab() == null || !b.getId().equalsIgnoreCase(plan.getReplacementCab().getId())))
                            .findFirst().orElse(null);

                    result.add(TripLedgerEntryEntity.builder()
                        .id("LED-" + trip.getId() + "-ORIG-" + (idx++))
                        .trip(tripRef)
                        .bookingId(matched != null ? matched.getId() : null)
                        .type(LedgerEntryType.ORIGINAL_COST)
                        .amount(amt)
                        .basis(LedgerBasis.RECORDED)
                        .note(line.getAssumptionNote() != null ? line.getAssumptionNote() : line.getLabel())
                        .createdAt(LocalDateTime.now())
                        .build());
                }
            }
        }
        return result;
    }

    private LedgerBasis determineReplacementCostBasis(Trip trip, Booking newLeg) {
        if (newLeg == null) {
            return LedgerBasis.ESTIMATED;
        }

        String source = newLeg.getSource() != null ? newLeg.getSource().toUpperCase() : "";
        String paymentStatus = newLeg.getPaymentStatus() != null ? newLeg.getPaymentStatus().toUpperCase() : "";
        String ref = newLeg.getBookingReference() != null ? newLeg.getBookingReference().toUpperCase() : "";

        // 1. DEMO for marketplace bookings
        if (source.contains("MARKET") || source.contains("DEMO")
                || paymentStatus.contains("DEMO") || ref.contains("MKT") || ref.contains("DEMO")
                || isDemoTripOrBooking(trip)) {
            return LedgerBasis.DEMO;
        }

        // 2. ESTIMATED for estimated or generated options
        if (source.contains("ESTIMATED") || source.contains("GENERATED") || source.contains("SYNTHETIC")) {
            return LedgerBasis.ESTIMATED;
        }

        // 3. RECORDED only for an existing booking
        boolean isExistingBooking = trip != null && trip.getBookings() != null && trip.getBookings().stream()
                .anyMatch(b -> b.getId().equalsIgnoreCase(newLeg.getId()) && b.getStatus() != BookingStatus.CANCELLED);

        if (isExistingBooking || "INVENTORY".equals(source) || "DIRECT".equals(source) || "RECORDED".equals(source)) {
            return LedgerBasis.RECORDED;
        }

        return LedgerBasis.ESTIMATED;
    }

    private Booking findCancelledBookingForRefund(List<Booking> bookings, CostBreakdownLine line, BigDecimal amt) {
        String labelLower = line.getLabel() != null ? line.getLabel().toLowerCase() : "";
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.CANCELLED && b.getCost() != null
                    && b.getCost().setScale(2, RoundingMode.HALF_UP).equals(amt)) {
                if (b.getProvider() != null && labelLower.contains(b.getProvider().toLowerCase())) {
                    return b;
                }
                if (b.getType() != null && labelLower.contains(b.getType().name().toLowerCase())) {
                    return b;
                }
            }
        }
        return bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED && b.getCost() != null
                        && b.getCost().setScale(2, RoundingMode.HALF_UP).equals(amt))
                .findFirst()
                .orElse(null);
    }

    private TripLedgerResponse assembleResponse(Trip trip) {
        List<TripLedgerEntryEntity> entities = tripLedgerEntryJpaRepository.findByTripIdOrderByCreatedAtAsc(trip.getId());
        Map<String, Booking> bookingMap = trip.getBookings().stream()
                .collect(Collectors.toMap(Booking::getId, b -> b, (a, b) -> a));

        BigDecimal originalCost = BigDecimal.ZERO.setScale(2);
        BigDecimal replacementCost = BigDecimal.ZERO.setScale(2);
        BigDecimal estimatedExtraCost = BigDecimal.ZERO.setScale(2);
        BigDecimal expectedRefunds = BigDecimal.ZERO.setScale(2);
        BigDecimal recordedRefunds = BigDecimal.ZERO.setScale(2);

        List<LedgerEntryDto> dtos = new ArrayList<>();
        for (TripLedgerEntryEntity e : entities) {
            BigDecimal amt = e.getAmount().setScale(2, RoundingMode.HALF_UP);
            boolean isDeduction = e.getType() == LedgerEntryType.REFUND_EXPECTED
                    || e.getType() == LedgerEntryType.REFUND_RECORDED
                    || e.getType() == LedgerEntryType.REFUND;

            switch (e.getType()) {
                case ORIGINAL_COST -> originalCost = originalCost.add(amt);
                case REPLACEMENT_COST -> replacementCost = replacementCost.add(amt);
                case EXTRA_COST -> estimatedExtraCost = estimatedExtraCost.add(amt);
                case REFUND_EXPECTED -> expectedRefunds = expectedRefunds.add(amt);
                case REFUND, REFUND_RECORDED -> recordedRefunds = recordedRefunds.add(amt);
                case REFUND_DEDUCTION -> recordedRefunds = recordedRefunds.subtract(amt);
            }

            Booking b = e.getBookingId() != null ? bookingMap.get(e.getBookingId()) : null;
            String desc = b != null ? (b.getProvider() + " (" + b.getType() + ")") : null;

            String statusLabel;
            if (e.getBasis() == LedgerBasis.PROVIDER_REPORTED) {
                statusLabel = "Provider-reported, not independently verified — Odyssey does not process real payments.";
            } else if ("RECEIVED".equalsIgnoreCase(e.getRefundStatus())) {
                statusLabel = "User-reported, not verified";
            } else {
                statusLabel = e.getRefundStatus();
            }

            dtos.add(LedgerEntryDto.builder()
                    .id(e.getId())
                    .bookingId(e.getBookingId())
                    .bookingDescription(desc)
                    .type(e.getType())
                    .amount(amt)
                    .deduction(isDeduction)
                    .basis(e.getBasis())
                    .category(e.getCategory())
                    .description(e.getDescription())
                    .parentEntryId(e.getParentEntryId())
                    .refundStatus(e.getRefundStatus())
                    .statusLabel(statusLabel)
                    .note(e.getNote())
                    .reference(e.getReference())
                    .receivedOn(e.getReceivedOn())
                    .createdAt(e.getCreatedAt())
                    .build());
        }

        List<BookingRefundSummaryDto> bookingRefunds = buildBookingRefundSummaries(trip, entities);

        BigDecimal outstandingRefunds;
        if (!bookingRefunds.isEmpty()) {
            outstandingRefunds = bookingRefunds.stream()
                    .map(BookingRefundSummaryDto::getOutstandingAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            outstandingRefunds = expectedRefunds.subtract(recordedRefunds);
            if (outstandingRefunds.compareTo(BigDecimal.ZERO) < 0) {
                outstandingRefunds = BigDecimal.ZERO.setScale(2);
            }
        }

        BigDecimal ledgerTotal = originalCost.add(replacementCost).add(estimatedExtraCost).subtract(expectedRefunds)
                .setScale(2, RoundingMode.HALF_UP);

        boolean isDemo = isDemoTripOrBooking(trip);
        String timingNote = isDemo ? getRefundProps().getDemoNote() : getRefundProps().getNote();

        LedgerSummaryDto summary = LedgerSummaryDto.builder()
                .originalCost(originalCost.setScale(2, RoundingMode.HALF_UP))
                .replacementCost(replacementCost.setScale(2, RoundingMode.HALF_UP))
                .estimatedExtraCost(estimatedExtraCost.setScale(2, RoundingMode.HALF_UP))
                .expectedRefunds(expectedRefunds.setScale(2, RoundingMode.HALF_UP))
                .recordedRefunds(recordedRefunds.setScale(2, RoundingMode.HALF_UP))
                .outstandingRefunds(outstandingRefunds.setScale(2, RoundingMode.HALF_UP))
                .ledgerTotal(ledgerTotal)
                .netTotal(ledgerTotal)
                .refundReceiptLabel("User-reported, not verified")
                .disclaimer("Estimates are not confirmed amounts")
                .timingNote(timingNote)
                .typicalDaysMin(getRefundProps().getTypicalDaysMin())
                .typicalDaysMax(getRefundProps().getTypicalDaysMax())
                .isDemo(isDemo)
                .build();

        return TripLedgerResponse.builder()
                .tripId(trip.getId())
                .planId(trip.getLatestPlan() != null ? trip.getLatestPlan().getId() : null)
                .summary(summary)
                .ledgerTotal(ledgerTotal)
                .netTotal(ledgerTotal)
                .entries(dtos)
                .bookingRefunds(bookingRefunds)
                .timingNote(timingNote)
                .typicalDaysMin(getRefundProps().getTypicalDaysMin())
                .typicalDaysMax(getRefundProps().getTypicalDaysMax())
                .isDemo(isDemo)
                .build();
    }

    private List<BookingRefundSummaryDto> buildBookingRefundSummaries(Trip trip, List<TripLedgerEntryEntity> entities) {
        List<BookingRefundSummaryDto> summaries = new ArrayList<>();
        Map<String, List<TripLedgerEntryEntity>> entriesByBooking = entities.stream()
                .filter(e -> e.getBookingId() != null)
                .collect(Collectors.groupingBy(TripLedgerEntryEntity::getBookingId));

        for (Booking b : trip.getBookings()) {
            boolean isCancelled = b.getStatus() == BookingStatus.CANCELLED;
            List<TripLedgerEntryEntity> bkgEntries = entriesByBooking.getOrDefault(b.getId(), Collections.emptyList());

            boolean hasRefundEntries = bkgEntries.stream()
                    .anyMatch(e -> e.getType() == LedgerEntryType.REFUND
                            || e.getType() == LedgerEntryType.REFUND_RECORDED
                            || e.getType() == LedgerEntryType.REFUND_EXPECTED);

            if (!isCancelled && !hasRefundEntries) {
                continue;
            }

            BigDecimal origCost = b.getCost() != null ? b.getCost().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

            // Find recorded refund entries
            List<TripLedgerEntryEntity> recordedRefunds = bkgEntries.stream()
                    .filter(e -> e.getType() == LedgerEntryType.REFUND || e.getType() == LedgerEntryType.REFUND_RECORDED)
                    .collect(Collectors.toList());

            // Find deduction entries in stable creation order
            List<TripLedgerEntryEntity> deductionEntities = bkgEntries.stream()
                    .filter(e -> e.getType() == LedgerEntryType.REFUND_DEDUCTION)
                    .sorted(Comparator.comparing(TripLedgerEntryEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TripLedgerEntryEntity::getId))
                    .collect(Collectors.toList());

            BigDecimal grossRefund = recordedRefunds.stream()
                    .map(TripLedgerEntryEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalDeductions = deductionEntities.stream()
                    .map(TripLedgerEntryEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal netRefund = grossRefund.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);

            List<RefundDeductionDto> deductionDtos = new ArrayList<>();
            for (TripLedgerEntryEntity de : deductionEntities) {
                DeductionCategory cat = DeductionCategory.OTHER;
                try {
                    if (de.getCategory() != null) cat = DeductionCategory.valueOf(de.getCategory());
                } catch (Exception ignored) {}

                deductionDtos.add(RefundDeductionDto.builder()
                        .category(cat)
                        .amount(de.getAmount().setScale(2, RoundingMode.HALF_UP))
                        .description(de.getDescription())
                        .build());
            }

            RefundStatus status = RefundStatus.EXPECTED;
            if (!recordedRefunds.isEmpty()) {
                TripLedgerEntryEntity latest = recordedRefunds.get(recordedRefunds.size() - 1);
                try {
                    if (latest.getRefundStatus() != null) {
                        status = RefundStatus.valueOf(latest.getRefundStatus());
                    } else {
                        status = RefundStatus.RECEIVED;
                    }
                } catch (Exception ignored) {
                    status = RefundStatus.RECEIVED;
                }
            }

            BigDecimal expectedGross = bkgEntries.stream()
                    .filter(e -> e.getType() == LedgerEntryType.REFUND_EXPECTED)
                    .map(TripLedgerEntryEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            if (expectedGross.compareTo(BigDecimal.ZERO) == 0) {
                expectedGross = grossRefund.compareTo(BigDecimal.ZERO) > 0 ? grossRefund : origCost;
            }

            // Requirement 1:
            // For a booking with status RECEIVED, outstanding = 0 (deductions are explained, not owed).
            // For EXPECTED or REQUESTED, outstanding = expected net amount (expected gross minus any recorded deductions).
            // Show "Reduction: Rs X (explained by deductions)" separately from "Outstanding".
            BigDecimal outstanding;
            if (status == RefundStatus.RECEIVED) {
                if (grossRefund.compareTo(expectedGross) >= 0 || totalDeductions.compareTo(BigDecimal.ZERO) > 0) {
                    outstanding = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                } else {
                    BigDecimal remainingGross = expectedGross.subtract(grossRefund);
                    if (remainingGross.compareTo(BigDecimal.ZERO) < 0) {
                        remainingGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                    outstanding = remainingGross.setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                BigDecimal expectedNet = expectedGross.subtract(totalDeductions);
                if (expectedNet.compareTo(BigDecimal.ZERO) < 0) {
                    expectedNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                outstanding = expectedNet.setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal reduction = totalDeductions.setScale(2, RoundingMode.HALF_UP);

            boolean hasProviderReported = recordedRefunds.stream().anyMatch(e -> e.getBasis() == LedgerBasis.PROVIDER_REPORTED);
            String statusLabel = hasProviderReported
                    ? "Provider-reported, not independently verified — Odyssey does not process real payments."
                    : (status == RefundStatus.RECEIVED ? "User-reported, not verified" : status.name());

            boolean isDemo = isDemoOrMarketplaceBooking(b, trip);
            String timingNote = isDemo ? getRefundProps().getDemoNote() : getRefundProps().getNote();

            summaries.add(BookingRefundSummaryDto.builder()
                    .bookingId(b.getId())
                    .bookingDescription(b.getProvider() + " (" + b.getType() + ")")
                    .originalCost(origCost)
                    .grossRefund(grossRefund)
                    .deductions(deductionDtos)
                    .totalDeductions(totalDeductions)
                    .netRefund(netRefund)
                    .reduction(reduction)
                    .status(status)
                    .statusLabel(statusLabel)
                    .outstandingAmount(outstanding)
                    .timingNote(timingNote)
                    .build());
        }
        return summaries;
    }

    private boolean isDemoTripOrBooking(Trip trip) {
        if (trip != null && trip.getId() != null && trip.getId().toUpperCase().contains("DEMO")) {
            return true;
        }
        if (trip != null && trip.getBookings() != null) {
            for (Booking b : trip.getBookings()) {
                if (isDemoOrMarketplaceBooking(b, trip)) return true;
            }
        }
        return false;
    }

    private boolean isDemoOrMarketplaceBooking(Booking booking, Trip trip) {
        if (booking != null) {
            if ("DEMO_REFUNDED".equalsIgnoreCase(booking.getPaymentStatus())) return true;
            if (booking.getProvider() != null && booking.getProvider().toUpperCase().contains("DEMO")) return true;
            if (booking.getBookingReference() != null && booking.getBookingReference().toUpperCase().contains("DEMO")) return true;
            if (booking.getSource() != null && (booking.getSource().toUpperCase().contains("MARKET") || booking.getSource().toUpperCase().contains("DEMO"))) return true;
        }
        if (trip != null && trip.getId() != null && trip.getId().toUpperCase().contains("DEMO")) return true;
        return false;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
