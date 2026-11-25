package com.odyssey.service;

import com.odyssey.dto.request.DraftMessageRequest;
import com.odyssey.dto.request.EditMessageRequest;
import com.odyssey.dto.request.SendMessageRequest;
import com.odyssey.dto.response.TravellerMessageDto;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.HotelEntity;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.TravellerMessageEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.exception.RateLimitException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.notification.EmailChannel;
import com.odyssey.notification.SendResult;
import com.odyssey.notification.SmsChannel;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.HotelJpaRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
import com.odyssey.repository.jpa.TravellerMessageJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravellerMessageService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String MARKETPLACE_DISCLAIMER =
            "Demo marketplace. No real ticket is issued and no money was charged.";
    private static final String ESTIMATED_DISCLAIMER =
            "Schedule is estimated, please verify.";

    private final TravellerMessageJpaRepository travellerMessageJpaRepository;
    private final TripJpaRepository tripJpaRepository;
    private final BookingJpaRepository bookingJpaRepository;
    private final HotelJpaRepository hotelJpaRepository;
    private final NotificationJpaRepository notificationJpaRepository;
    private final AuditEntryJpaRepository auditEntryJpaRepository;
    private final TripAccessService tripAccessService;
    private final AuthService authService;
    private final LlmService llmService;
    private final EmailChannel emailChannel;
    private final SmsChannel smsChannel;

    /**
     * Generates a new message draft for the specified trip.
     */
    @Transactional
    public TravellerMessageDto draftMessage(String tripId, DraftMessageRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        TripEntity tripRef = tripJpaRepository.getReferenceById(tripId);

        Booking booking = resolveBooking(trip, request.getBookingId(), request.getRecipientType());

        // 1. Build deterministic template draft
        DraftContent template = buildDeterministicTemplate(trip, booking, request.getRecipientType(), request.getDraftKind());

        // 2. Collect all valid source facts for strict verification
        SourceFacts facts = extractSourceFacts(trip, booking, template);

        // 3. Attempt LLM rewording with verification and 5s timeout fallback
        String finalBody = template.body;
        GeneratedBy generatedBy = GeneratedBy.TEMPLATE;

        try {
            String llmCandidate = llmService.rewordDraft(trip, template.body, request.getDraftKind().name());
            if (llmCandidate != null && !llmCandidate.trim().isEmpty() && !llmCandidate.trim().equals(template.body.trim())) {
                boolean verified = verifyLlmOutput(llmCandidate, facts);
                if (verified) {
                    finalBody = llmCandidate.trim();
                    generatedBy = GeneratedBy.LLM;
                } else {
                    log.info("LLM rewording rejected: failed strict source verification. Using template text.");
                    generatedBy = GeneratedBy.TEMPLATE;
                }
            }
        } catch (Exception e) {
            log.warn("LLM draft rewording failed or timed out ({}). Using template text.", e.getMessage());
            generatedBy = GeneratedBy.TEMPLATE;
        }

        // Determine prefilled recipient and masking if available
        String prefilledRecipient = resolveDefaultRecipient(booking, trip, request.getRecipientType());
        String recipientMasked = null;
        if (prefilledRecipient != null && !prefilledRecipient.isBlank()) {
            if (NotificationMasker.isValidEmail(prefilledRecipient)) {
                recipientMasked = NotificationMasker.maskEmail(prefilledRecipient);
            } else if (NotificationMasker.isValidPhone(prefilledRecipient)) {
                recipientMasked = NotificationMasker.maskPhone(prefilledRecipient);
            } else {
                recipientMasked = prefilledRecipient;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String messageId = IdGenerator.generate("MSG");

        TravellerMessageEntity entity = TravellerMessageEntity.builder()
                .id(messageId)
                .trip(tripRef)
                .recipientType(request.getRecipientType())
                .draftKind(request.getDraftKind())
                .bookingId(booking != null ? booking.getId() : (request.getBookingId() != null ? request.getBookingId() : null))
                .subject(template.subject)
                .body(finalBody)
                .status(MessageStatus.DRAFT)
                .generatedBy(generatedBy)
                .recipientMasked(recipientMasked)
                .createdAt(now)
                .updatedAt(now)
                .build();

        travellerMessageJpaRepository.save(entity);

        // Record audit entry
        try {
            auditEntryJpaRepository.save(AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .trip(tripRef)
                    .bookingId(booking != null ? booking.getId() : null)
                    .userId(trip.getUserId())
                    .timestamp(now)
                    .actor(AuditActor.USER.name())
                    .action("TRAVELLER_MESSAGE_DRAFTED")
                    .details(String.format("Drafted %s message for %s (generatedBy: %s)",
                            request.getDraftKind(), request.getRecipientType(), generatedBy))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record audit entry for drafted message: {}", e.getMessage());
        }

        return toDto(entity, prefilledRecipient);
    }

    /**
     * Edits the subject and body of an existing message while in DRAFT or EDITED state.
     */
    @Transactional
    public TravellerMessageDto editMessage(String tripId, String messageId, EditMessageRequest request) {
        tripAccessService.requireOwner(tripId);
        TravellerMessageEntity entity = travellerMessageJpaRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!entity.getTrip().getId().equals(tripId)) {
            throw new ResourceNotFoundException("Message does not belong to trip " + tripId);
        }

        if (entity.getStatus() != MessageStatus.DRAFT && entity.getStatus() != MessageStatus.EDITED) {
            throw new IllegalStateException("Cannot edit a message that has already been approved or sent (current status: "
                    + entity.getStatus() + ")");
        }

        if (request.getBody() != null && request.getBody().length() > 4000) {
            throw new IllegalArgumentException("Message body cannot exceed 4000 characters");
        }

        entity.setSubject(request.getSubject().trim());
        entity.setBody(request.getBody().trim());
        entity.setStatus(MessageStatus.EDITED);
        entity.setUpdatedAt(LocalDateTime.now());

        travellerMessageJpaRepository.save(entity);

        // Record audit entry
        try {
            TripEntity tripRef = tripJpaRepository.getReferenceById(tripId);
            auditEntryJpaRepository.save(AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .trip(tripRef)
                    .bookingId(entity.getBookingId())
                    .userId(tripRef.getUser() != null ? tripRef.getUser().getId() : null)
                    .timestamp(LocalDateTime.now())
                    .actor(AuditActor.USER.name())
                    .action("TRAVELLER_MESSAGE_EDITED")
                    .details(String.format("Edited %s message %s", entity.getDraftKind(), entity.getId()))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record audit entry for edited message: {}", e.getMessage());
        }

        return toDto(entity, null);
    }

    /**
     * Approves and sends the message across the chosen communication channel.
     */
    @Transactional
    public TravellerMessageDto sendMessage(String tripId, String messageId, SendMessageRequest request) {
        Trip trip = tripAccessService.requireOwner(tripId);
        TripEntity tripRef = tripJpaRepository.getReferenceById(tripId);

        if (request.getConfirm() == null || !request.getConfirm()) {
            throw new IllegalArgumentException("Confirmation is required: confirm must be set to true to approve and send a message");
        }

        TravellerMessageEntity entity = travellerMessageJpaRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!entity.getTrip().getId().equals(tripId)) {
            throw new ResourceNotFoundException("Message does not belong to trip " + tripId);
        }

        // Rate limit: 10 sends per hour per trip
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentSends = travellerMessageJpaRepository.countByTripIdAndSentAtAfter(tripId, oneHourAgo);
        if (recentSends >= 10) {
            throw new RateLimitException("Rate limit reached: Maximum 10 messages can be sent per hour for this trip");
        }

        ShareChannel channel = request.getChannel();
        Booking booking = resolveBooking(trip, entity.getBookingId(), entity.getRecipientType());
        UserEntity currentUser = authService.getCurrentUserEntity();

        String rawRecipient = (request.getRecipient() != null && !request.getRecipient().isBlank())
                ? request.getRecipient().trim()
                : resolveDefaultRecipient(booking, trip, entity.getRecipientType());

        String recipient = null;
        String recipientMasked = "N/A";
        MessageStatus status = MessageStatus.SENT;
        String mailtoUrl = null;
        String sendError = null;

        if (channel == ShareChannel.EMAIL) {
            if (rawRecipient != null && !rawRecipient.isBlank() && !NotificationMasker.isValidEmail(rawRecipient)) {
                throw new IllegalArgumentException("Invalid email format: " + rawRecipient);
            }

            if (rawRecipient == null || rawRecipient.isBlank()) {
                status = MessageStatus.SKIPPED;
                sendError = "No valid email address available";
                recipientMasked = "N/A";
            } else {
                recipient = rawRecipient;
                recipientMasked = NotificationMasker.maskEmail(recipient);

                if (emailChannel != null) {
                    try {
                        NotificationMessage msg = NotificationMessage.builder()
                                .tripId(trip.getId())
                                .eventType("TRAVELLER_MESSAGE")
                                .channel("EMAIL")
                                .recipient(recipient)
                                .subject(entity.getSubject())
                                .message(entity.getBody())
                                .build();
                        SendResult result = emailChannel.send(msg);
                        status = "SIMULATED".equalsIgnoreCase(result.getStatus() != null ? result.getStatus().name() : "")
                                ? MessageStatus.SIMULATED
                                : MessageStatus.SENT;
                        sendError = result.getError();
                    } catch (Exception e) {
                        log.warn("Email dispatch failed for traveller message: {}", e.getMessage());
                        status = MessageStatus.FAILED;
                        sendError = e.getMessage();
                    }
                } else {
                    status = MessageStatus.SIMULATED;
                }
            }
        } else if (channel == ShareChannel.SMS) {
            if (rawRecipient != null && !rawRecipient.isBlank() && NotificationMasker.normalizePhone(rawRecipient) == null) {
                throw new IllegalArgumentException("Invalid phone number format: " + rawRecipient + ". Must be 10 digits");
            }

            String normalizedPhone = NotificationMasker.normalizePhone(rawRecipient);
            if (normalizedPhone == null) {
                status = MessageStatus.SKIPPED;
                sendError = "No valid phone number available";
                recipientMasked = "N/A";
            } else {
                recipient = normalizedPhone;
                recipientMasked = NotificationMasker.maskPhone(recipient);

                if (smsChannel != null) {
                    try {
                        NotificationMessage msg = NotificationMessage.builder()
                                .tripId(trip.getId())
                                .eventType("TRAVELLER_MESSAGE")
                                .channel("SMS")
                                .recipient(recipient)
                                .subject(entity.getSubject())
                                .message(entity.getBody())
                                .build();
                        SendResult result = smsChannel.send(msg);
                        status = "SIMULATED".equalsIgnoreCase(result.getStatus() != null ? result.getStatus().name() : "")
                                ? MessageStatus.SIMULATED
                                : MessageStatus.SENT;
                        sendError = result.getError();
                    } catch (Exception e) {
                        log.warn("SMS dispatch failed for traveller message: {}", e.getMessage());
                        status = MessageStatus.FAILED;
                        sendError = e.getMessage();
                    }
                } else {
                    status = MessageStatus.SIMULATED;
                }
            }
        } else if (channel == ShareChannel.MAILTO) {
            recipient = (rawRecipient != null && !rawRecipient.isBlank()) ? rawRecipient.trim() : "";
            recipientMasked = NotificationMasker.isValidEmail(recipient)
                    ? NotificationMasker.maskEmail(recipient)
                    : (recipient != null && !recipient.isBlank() ? recipient : "N/A");
            mailtoUrl = "mailto:" + recipient + "?subject="
                    + URLEncoder.encode(entity.getSubject(), StandardCharsets.UTF_8)
                    + "&body=" + URLEncoder.encode(entity.getBody(), StandardCharsets.UTF_8);
            status = MessageStatus.LINK_CREATED;
        } else if (channel == ShareChannel.COPY) {
            status = MessageStatus.COPIED;
            recipientMasked = "Clipboard";
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setChannel(channel.name());
        entity.setStatus(status);
        entity.setRecipientMasked(recipientMasked);
        entity.setSentAt(now);
        entity.setUpdatedAt(now);

        travellerMessageJpaRepository.save(entity);

        // When a LATE_CHECKIN message is sent for a hotel booking, surface it as a live pending request
        // on the Hotel Provider Dashboard so the hotel can accept or decline the late check-in.
        if (entity.getDraftKind() == DraftKind.LATE_CHECKIN
                && entity.getRecipientType() == RecipientType.HOTEL
                && booking != null
                && booking.getId() != null) {
            bookingJpaRepository.findById(booking.getId()).ifPresent(hotelBooking -> {
                if (hotelBooking.getProviderId() == null || hotelBooking.getProviderId().isBlank()) {
                    if (hotelBooking.getServiceId() != null && !hotelBooking.getServiceId().isBlank()) {
                        hotelJpaRepository.findById(hotelBooking.getServiceId())
                                .ifPresent(h -> hotelBooking.setProviderId(h.getProviderId()));
                    }
                    if ((hotelBooking.getProviderId() == null || hotelBooking.getProviderId().isBlank())
                            && hotelBooking.getProvider() != null) {
                        String targetName = hotelBooking.getProvider().trim();
                        hotelJpaRepository.findAll().stream()
                                .filter(h -> h.getName() != null && h.getName().trim().equalsIgnoreCase(targetName))
                                .findFirst()
                                .ifPresent(h -> hotelBooking.setProviderId(h.getProviderId()));
                    }
                }
                hotelBooking.setStatus("PENDING_PROVIDER_CONFIRMATION");
                hotelBooking.setProviderConfirmationStatus("PENDING");
                hotelBooking.setConfirmationExpiresAt(now.plusSeconds(180));
                String subjectSummary = entity.getSubject() != null && !entity.getSubject().isBlank()
                        ? entity.getSubject().trim()
                        : "Late Check-in Request";
                hotelBooking.setVerificationNote("Late Check-in Request: " + subjectSummary);
                bookingJpaRepository.save(hotelBooking);
            });
        }

        // Record in notifications table
        if (notificationJpaRepository != null) {
            try {
                notificationJpaRepository.save(NotificationEntity.builder()
                        .id(IdGenerator.generate("NOTIF"))
                        .trip(tripRef)
                        .eventType("TRAVELLER_MESSAGE")
                        .channel(channel.name())
                        .recipientMasked(recipientMasked)
                        .subject(entity.getSubject())
                        .body(entity.getBody())
                        .status(status.name())
                        .error(sendError)
                        .createdAt(now)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to record traveller message notification: {}", e.getMessage());
            }
        }

        // Record in audit log
        try {
            auditEntryJpaRepository.save(AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .trip(tripRef)
                    .bookingId(entity.getBookingId())
                    .userId(trip.getUserId())
                    .timestamp(now)
                    .actor(AuditActor.USER.name())
                    .action("TRAVELLER_MESSAGE_SENT")
                    .details(String.format("Approved and sent %s message via %s (status: %s, recipient: %s)",
                            entity.getDraftKind(), channel, status, recipientMasked))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record audit entry for sent message: {}", e.getMessage());
        }

        TravellerMessageDto dto = toDto(entity, rawRecipient);
        dto.setMailtoUrl(mailtoUrl);
        return dto;
    }

    /**
     * Retrieves all messages for a given trip.
     */
    @Transactional(readOnly = true)
    public List<TravellerMessageDto> getMessages(String tripId) {
        tripAccessService.requireReadAccess(tripId);
        List<TravellerMessageEntity> list = travellerMessageJpaRepository.findByTripIdOrderByCreatedAtDesc(tripId);
        List<TravellerMessageDto> result = new ArrayList<>();
        for (TravellerMessageEntity e : list) {
            result.add(toDto(e, null));
        }
        return result;
    }

    /**
     * Retrieves a single message.
     */
    @Transactional(readOnly = true)
    public TravellerMessageDto getMessage(String tripId, String messageId) {
        tripAccessService.requireReadAccess(tripId);
        TravellerMessageEntity entity = travellerMessageJpaRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
        if (!entity.getTrip().getId().equals(tripId)) {
            throw new ResourceNotFoundException("Message does not belong to trip " + tripId);
        }
        return toDto(entity, null);
    }

    // =========================================================================
    // Deterministic Templates
    // =========================================================================

    private static class DraftContent {
        final String subject;
        final String body;
        DraftContent(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }

    DraftContent buildDeterministicTemplate(Trip trip, Booking booking, RecipientType recipientType, DraftKind draftKind) {
        String travellerName = getNonBlank(trip.getTravellerName(),
                authService.getCurrentUserEntity() != null ? authService.getCurrentUserEntity().getName() : null);
        String travellerPhone = getNonBlank(trip.getTravellerPhone(),
                authService.getCurrentUserEntity() != null ? authService.getCurrentUserEntity().getPhone() : null);
        String tripTitle = getNonBlank(trip.getTitle(), "Trip " + trip.getId());

        String bookingRef = (booking != null && booking.getBookingReference() != null && !booking.getBookingReference().isBlank())
                ? booking.getBookingReference().trim()
                : (booking != null ? booking.getId() : null);

        String providerName = (booking != null && booking.getProvider() != null && !booking.getProvider().isBlank())
                ? booking.getProvider().trim()
                : null;

        String vehicleNumber = extractVehicleNumber(booking);

        String route = (booking != null && booking.getFrom() != null && booking.getTo() != null)
                ? (booking.getFrom().trim() + " -> " + booking.getTo().trim())
                : null;

        String origTime = (booking != null && booking.getStartTime() != null)
                ? booking.getStartTime().format(DATE_TIME_FMT)
                : null;

        String disruptionReason = extractDisruptionReason(trip, booking);

        // Recovery plan details
        RecoveryPlan plan = trip.getLatestPlan();
        String newItinerary = null;
        String newArrivalTime = null;
        if (plan != null) {
            if (plan.getNewLeg() != null) {
                Booking leg = plan.getNewLeg();
                String legProv = leg.getProvider() != null ? leg.getProvider() : "Replacement Service";
                String legTime = leg.getStartTime() != null ? leg.getStartTime().format(DATE_TIME_FMT) : null;
                newItinerary = legTime != null ? String.format("%s departing at %s", legProv, legTime) : legProv;
                if (leg.getEndTime() != null) {
                    newArrivalTime = leg.getEndTime().format(DATE_TIME_FMT);
                }
            } else if (plan.getChosenOptionLabel() != null) {
                newItinerary = plan.getChosenOptionLabel().name();
            }
        }

        String subject;
        StringBuilder sb = new StringBuilder();

        switch (draftKind) {
            case LATE_CHECKIN -> {
                String hotel = providerName != null ? providerName : "Hotel";
                subject = String.format("Late Check-in Notice: %s%s", tripTitle, bookingRef != null ? " - Ref " + bookingRef : "");
                sb.append("Dear ").append(hotel).append(" Reservations Team,\n\n");
                sb.append("I am holding a reservation for check-in today.");
                if (bookingRef != null) {
                    sb.append(" Reservation Reference: ").append(bookingRef).append(".");
                }
                sb.append("\n");

                if (disruptionReason != null) {
                    sb.append("Due to a travel disruption (").append(disruptionReason).append("), my arrival schedule has been revised.\n");
                } else {
                    sb.append("Due to travel disruption on my inbound journey, my arrival schedule has been revised.\n");
                }

                if (origTime != null) {
                    sb.append("Original scheduled time: ").append(origTime).append("\n");
                }
                if (newArrivalTime != null) {
                    sb.append("Estimated revised arrival at hotel: ").append(newArrivalTime).append("\n");
                }
                if (newItinerary != null) {
                    sb.append("Inbound transport: travelling via ").append(newItinerary).append("\n");
                }

                sb.append("\nKindly hold my room for late check-in. Please reply or message to confirm receipt.\n\n");
                sb.append("Warm regards,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
                sb.append("Trip: ").append(tripTitle).append(" (").append(trip.getId()).append(")");
            }

            case DELAY_NOTICE -> {
                String recipientGreeting = recipientType == RecipientType.FAMILY ? "Family"
                        : (providerName != null ? providerName : "Customer Support");
                subject = String.format("Travel Delay Notice: %s%s", tripTitle, bookingRef != null ? " - " + bookingRef : "");
                sb.append("Dear ").append(recipientGreeting).append(",\n\n");
                sb.append("I am writing to provide an update regarding travel delays for ").append(tripTitle).append(".\n\n");

                if (bookingRef != null) sb.append("Booking Reference: ").append(bookingRef).append("\n");
                if (providerName != null) sb.append("Provider: ").append(providerName).append("\n");
                if (vehicleNumber != null) sb.append("Vehicle / Flight: ").append(vehicleNumber).append("\n");
                if (route != null) sb.append("Route: ").append(route).append("\n");
                if (disruptionReason != null) sb.append("Reason: ").append(disruptionReason).append("\n");
                if (origTime != null) sb.append("Original Schedule: ").append(origTime).append("\n");
                if (newArrivalTime != null) sb.append("Revised Arrival: ").append(newArrivalTime).append("\n");

                sb.append("\nI will keep you informed of any further developments.\n\n");
                sb.append("Regards,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
            }

            case CANCELLATION_AND_REFUND_REQUEST -> {
                String target = providerName != null ? providerName : "Carrier";
                subject = String.format("Cancellation and Refund Request: %s - %s",
                        bookingRef != null ? bookingRef : trip.getId(),
                        travellerName != null ? travellerName : "Traveller");
                sb.append("Dear ").append(target).append(" Customer Service,\n\n");
                sb.append("I am writing to formally request the cancellation and applicable refund processing for my scheduled booking.\n\n");

                if (bookingRef != null) sb.append("Booking Reference: ").append(bookingRef).append("\n");
                if (travellerName != null) sb.append("Passenger / Guest Name: ").append(travellerName).append("\n");
                if (route != null) sb.append("Service Route: ").append(route).append("\n");
                if (vehicleNumber != null) sb.append("Service / Flight Number: ").append(vehicleNumber).append("\n");
                if (origTime != null) sb.append("Scheduled Departure: ").append(origTime).append("\n");
                if (disruptionReason != null) sb.append("Disruption Reason: ").append(disruptionReason).append("\n");

                sb.append("\nKindly process the entitled refund according to carrier cancellation rules and confirm the refund reference number.\n\n");
                sb.append("Sincerely,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
            }

            case REBOOKING_NOTICE -> {
                subject = String.format("Rebooking Notice: %s - %s", tripTitle, travellerName != null ? travellerName : "Traveller");
                sb.append("Hello,\n\n");
                sb.append("Please be advised that my travel schedule for ").append(tripTitle).append(" has been rebooked.\n\n");

                if (bookingRef != null) sb.append("Previous Booking: ").append(bookingRef).append("\n");
                if (providerName != null) sb.append("Previous Carrier: ").append(providerName).append("\n");
                if (disruptionReason != null) sb.append("Cause of Change: ").append(disruptionReason).append("\n");
                if (newItinerary != null) sb.append("New Route / Service: ").append(newItinerary).append("\n");
                if (newArrivalTime != null) sb.append("Expected Final Arrival: ").append(newArrivalTime).append("\n");

                sb.append("\nPlease update your records accordingly.\n\n");
                sb.append("Best regards,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
            }

            case ARRIVAL_UPDATE -> {
                String greeting = recipientType == RecipientType.FAMILY ? "Hi everyone,"
                        : (recipientType == RecipientType.EVENT_ORGANISER ? "Dear Organising Team," : "Hello,");
                subject = String.format("Arrival Time Update: %s - %s", tripTitle, travellerName != null ? travellerName : "Traveller");
                sb.append(greeting).append("\n\n");
                sb.append("Here is an update regarding my arrival time for ").append(tripTitle).append(".\n\n");

                if (disruptionReason != null) sb.append("Due to ").append(disruptionReason).append(", arrival timing has shifted.\n");
                if (origTime != null) sb.append("Original Expected Time: ").append(origTime).append("\n");
                if (newArrivalTime != null) sb.append("New Expected Arrival Time: ").append(newArrivalTime).append("\n");
                if (route != null) sb.append("Destination: ").append(booking != null && booking.getTo() != null ? booking.getTo() : route).append("\n");

                sb.append("\nLooking forward to seeing you soon.\n\n");
                sb.append("Warm regards,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
            }

            case ASSISTANCE_REQUEST -> {
                String target = providerName != null ? providerName : (recipientType == RecipientType.HOTEL ? "Hotel" : "Service Provider");
                subject = String.format("Special Assistance Request: %s%s",
                        tripTitle, bookingRef != null ? " - Ref " + bookingRef : "");
                sb.append("Dear ").append(target).append(" Support Team,\n\n");
                sb.append("I am holding a booking with your service and would like to request assistance for the following declared travel needs:\n\n");

                if (bookingRef != null) sb.append("Booking Reference: ").append(bookingRef).append("\n");
                if (travellerName != null) sb.append("Passenger / Guest Name: ").append(travellerName).append("\n");
                if (vehicleNumber != null) sb.append("Service / Flight Number: ").append(vehicleNumber).append("\n");
                if (route != null) sb.append("Route / Location: ").append(route).append("\n");
                if (origTime != null) sb.append("Scheduled Time: ").append(origTime).append("\n");

                sb.append("\nDeclared Assistance Needs:\n");
                boolean anyNeed = false;
                if (Boolean.TRUE.equals(trip.getHasMobilityAssistance())) {
                    sb.append("- Mobility assistance / wheelchair boarding support\n");
                    anyNeed = true;
                }
                if (Boolean.TRUE.equals(trip.getHasElderlyTraveller())) {
                    sb.append("- Senior traveller assistance (step-free access and minimal walking distance)\n");
                    anyNeed = true;
                }
                if (Boolean.TRUE.equals(trip.getHasTravellingWithChildren())) {
                    sb.append("- Travelling with children (adjacent seating / family accommodation)\n");
                    anyNeed = true;
                }
                if (Boolean.TRUE.equals(trip.getHasMedicalNeeds())) {
                    sb.append("- Medical requirements accommodation\n");
                    anyNeed = true;
                }
                if (!anyNeed) {
                    sb.append("- General travel assistance\n");
                }

                sb.append("\nKindly confirm if these accommodations can be supported for this journey.\n\n");
                sb.append("Sincerely,\n");
                if (travellerName != null) sb.append(travellerName).append("\n");
                if (travellerPhone != null) sb.append("Phone: ").append(travellerPhone).append("\n");
            }

            default -> {
                subject = String.format("Update regarding %s", tripTitle);
                sb.append("Hello,\n\nUpdate regarding ").append(tripTitle).append(".\n\n");
                if (travellerName != null) sb.append("Regards,\n").append(travellerName);
            }
        }

        // Apply mandatory disclaimers when applicable
        if (isMarketplaceBookingOrTrip(booking, trip)) {
            sb.append("\n\n").append(MARKETPLACE_DISCLAIMER);
        }
        if (isEstimatedOrGeneratedSchedule(booking, trip)) {
            sb.append("\n\n").append(ESTIMATED_DISCLAIMER);
        }

        return new DraftContent(subject.trim(), sb.toString().trim());
    }

    // =========================================================================
    // Source Facts Extraction & Strict LLM Output Verification
    // =========================================================================

    static class SourceFacts {
        final Set<String> numbers = new HashSet<>();
        final Set<String> tokens = new HashSet<>();
        final String templateText;

        SourceFacts(String templateText) {
            this.templateText = templateText != null ? templateText : "";
        }
    }

    SourceFacts extractSourceFacts(Trip trip, Booking booking, DraftContent template) {
        SourceFacts facts = new SourceFacts(template.body);

        // Ingest template text
        ingestTextFacts(facts, template.subject);
        ingestTextFacts(facts, template.body);

        // Ingest Trip fields
        if (trip != null) {
            ingestTextFacts(facts, trip.getId());
            ingestTextFacts(facts, trip.getTitle());
            ingestTextFacts(facts, trip.getTravellerName());
            ingestTextFacts(facts, trip.getTravellerPhone());
            ingestTextFacts(facts, trip.getTravellerEmail());
            if (trip.getActiveDisruptions() != null) {
                for (Disruption d : trip.getActiveDisruptions()) {
                    ingestTextFacts(facts, d.getReason());
                    if (d.getDelayMinutes() != null && d.getDelayMinutes() > 0) facts.numbers.add(String.valueOf(d.getDelayMinutes()));
                }
            }
        }

        // Ingest Booking fields
        if (booking != null) {
            ingestTextFacts(facts, booking.getId());
            ingestTextFacts(facts, booking.getBookingReference());
            ingestTextFacts(facts, booking.getProvider());
            ingestTextFacts(facts, booking.getFrom());
            ingestTextFacts(facts, booking.getTo());
            ingestTextFacts(facts, booking.getHotelContactEmail());
            if (booking.getCost() != null) facts.numbers.add(booking.getCost().toPlainString());
            if (booking.getStartTime() != null) {
                ingestDateTimeFacts(facts, booking.getStartTime());
            }
            if (booking.getEndTime() != null) {
                ingestDateTimeFacts(facts, booking.getEndTime());
            }
        }

        return facts;
    }

    private void ingestTextFacts(SourceFacts facts, String text) {
        if (text == null || text.isBlank()) return;
        // Ingest digit sequences
        Matcher mDigits = Pattern.compile("\\b\\d+\\b").matcher(text);
        while (mDigits.find()) {
            facts.numbers.add(mDigits.group());
        }
        // Ingest words/tokens
        for (String word : text.split("[\\s,;:.()\\[\\]\\-_/\\\\\"']+")) {
            if (!word.isBlank()) {
                facts.tokens.add(word.toLowerCase());
            }
        }
    }

    private void ingestDateTimeFacts(SourceFacts facts, LocalDateTime ldt) {
        facts.numbers.add(String.valueOf(ldt.getYear()));
        facts.numbers.add(String.valueOf(ldt.getMonthValue()));
        facts.numbers.add(String.format("%02d", ldt.getMonthValue()));
        facts.numbers.add(String.valueOf(ldt.getDayOfMonth()));
        facts.numbers.add(String.format("%02d", ldt.getDayOfMonth()));
        facts.numbers.add(String.valueOf(ldt.getHour()));
        facts.numbers.add(String.format("%02d", ldt.getHour()));
        facts.numbers.add(String.valueOf(ldt.getMinute()));
        facts.numbers.add(String.format("%02d", ldt.getMinute()));
        facts.tokens.add(ldt.getMonth().name().toLowerCase());
        facts.tokens.add(ldt.format(DATE_TIME_FMT));
        facts.tokens.add(ldt.format(TIME_FMT));
    }

    /**
     * Verifies that every number, time, and key reference in llmText appears in source facts.
     * If ANY number or time is invented by the LLM, verification returns false.
     */
    boolean verifyLlmOutput(String llmText, SourceFacts facts) {
        if (llmText == null || llmText.isBlank()) return false;

        // 1. Every number in LLM text must exist in source facts
        Matcher mNum = Pattern.compile("\\b\\d+\\b").matcher(llmText);
        while (mNum.find()) {
            String num = mNum.group();
            if (!facts.numbers.contains(num)) {
                log.debug("LLM verification failed: number '{}' does not appear in source facts", num);
                return false;
            }
        }

        // 2. Strict time pattern check: HH:mm
        Matcher mTime = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b").matcher(llmText);
        while (mTime.find()) {
            String timeStr = mTime.group();
            if (!facts.templateText.contains(timeStr) && !facts.tokens.contains(timeStr)) {
                log.debug("LLM verification failed: time '{}' not found in source facts", timeStr);
                return false;
            }
        }

        // 3. Mandatory disclaimers check if present in template
        if (facts.templateText.contains(MARKETPLACE_DISCLAIMER) && !llmText.contains(MARKETPLACE_DISCLAIMER)) {
            log.debug("LLM verification failed: mandatory marketplace disclaimer was omitted by LLM");
            return false;
        }
        if (facts.templateText.contains(ESTIMATED_DISCLAIMER) && !llmText.contains(ESTIMATED_DISCLAIMER)) {
            log.debug("LLM verification failed: mandatory estimated disclaimer was omitted by LLM");
            return false;
        }

        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Booking resolveBooking(Trip trip, String bookingId, RecipientType recipientType) {
        if (trip == null || trip.getBookings() == null) return null;
        if (bookingId != null && !bookingId.isBlank()) {
            for (Booking b : trip.getBookings()) {
                if (bookingId.equalsIgnoreCase(b.getId()) || bookingId.equalsIgnoreCase(b.getBookingReference())) {
                    return b;
                }
            }
        }
        // Fallback by recipient type
        if (recipientType == RecipientType.HOTEL) {
            return trip.getBookings().stream()
                    .filter(b -> b.getType() == BookingType.HOTEL)
                    .findFirst()
                    .orElse(null);
        }
        if (recipientType == RecipientType.OPERATOR) {
            return trip.getBookings().stream()
                    .filter(b -> b.getType() == BookingType.FLIGHT || b.getType() == BookingType.TRAIN || b.getType() == BookingType.BUS)
                    .findFirst()
                    .orElse(null);
        }
        return trip.getBookings().isEmpty() ? null : trip.getBookings().get(0);
    }

    private String resolveDefaultRecipient(Booking booking, Trip trip, RecipientType recipientType) {
        if (recipientType == RecipientType.HOTEL && booking != null && booking.getHotelContactEmail() != null && !booking.getHotelContactEmail().isBlank()) {
            return booking.getHotelContactEmail().trim();
        }
        if (booking != null && booking.getHotelContactEmail() != null && !booking.getHotelContactEmail().isBlank()) {
            return booking.getHotelContactEmail().trim();
        }
        if (trip != null) {
            if (trip.getTravellerEmail() != null && !trip.getTravellerEmail().isBlank()) {
                return trip.getTravellerEmail().trim();
            }
            if (trip.getTravellerPhone() != null && !trip.getTravellerPhone().isBlank()) {
                return trip.getTravellerPhone().trim();
            }
        }
        UserEntity user = authService.getCurrentUserEntity();
        if (user != null) {
            if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
            if (user.getPhone() != null && !user.getPhone().isBlank()) return user.getPhone();
        }
        return null;
    }

    private String extractVehicleNumber(Booking booking) {
        if (booking == null) return null;
        String ref = booking.getBookingReference();
        if (ref != null && (ref.toUpperCase().contains("AI-") || ref.toUpperCase().contains("6E-") || ref.toUpperCase().contains("KA-"))) {
            return ref;
        }
        return null;
    }

    private String extractDisruptionReason(Trip trip, Booking booking) {
        if (trip == null || trip.getActiveDisruptions() == null) return null;
        if (booking != null) {
            for (Disruption d : trip.getActiveDisruptions()) {
                if (booking.getId().equalsIgnoreCase(d.getBookingId())) {
                    if (d.getReason() != null && !d.getReason().isBlank()) return d.getReason();
                }
            }
        }
        for (Disruption d : trip.getActiveDisruptions()) {
            if (d.getReason() != null && !d.getReason().isBlank()) return d.getReason();
        }
        return null;
    }

    public boolean isMarketplaceBookingOrTrip(Booking booking, Trip trip) {
        if (booking != null) {
            if ("DEMO_REFUNDED".equalsIgnoreCase(booking.getPaymentStatus())) return true;
            if (booking.getProvider() != null && booking.getProvider().toUpperCase().contains("DEMO")) return true;
            if (booking.getBookingReference() != null && booking.getBookingReference().toUpperCase().contains("DEMO")) return true;
            if (booking.getSource() != null && (booking.getSource().toUpperCase().contains("MARKET") || booking.getSource().toUpperCase().contains("DEMO"))) return true;
        }
        if (trip != null && trip.getId() != null && trip.getId().toUpperCase().contains("DEMO")) return true;
        return false;
    }

    public boolean isEstimatedOrGeneratedSchedule(Booking booking, Trip trip) {
        if (booking != null && booking.getSource() != null) {
            String src = booking.getSource().toUpperCase();
            if (src.contains("ESTIMATED") || src.contains("GENERATED") || src.contains("SYNTHETIC")) {
                return true;
            }
        }
        if (trip != null && trip.getLatestPlan() != null && trip.getLatestPlan().getNewLeg() != null) {
            Booking leg = trip.getLatestPlan().getNewLeg();
            if (leg.getSource() != null) {
                String src = leg.getSource().toUpperCase();
                if (src.contains("ESTIMATED") || src.contains("GENERATED") || src.contains("SYNTHETIC")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary.trim();
        if (fallback != null && !fallback.isBlank()) return fallback.trim();
        return null;
    }

    private TravellerMessageDto toDto(TravellerMessageEntity e, String rawRecipient) {
        String statusLabel = switch (e.getStatus()) {
            case SIMULATED -> "Simulated, not sent";
            case LINK_CREATED, COPIED -> "Not sent by Odyssey";
            case SKIPPED -> "Skipped";
            case FAILED -> "Failed";
            case SENT -> "Sent";
            case DRAFT -> "Draft";
            case EDITED -> "Edited";
            case APPROVED -> "Approved";
        };

        String generatedByLabel = e.getGeneratedBy() == GeneratedBy.LLM
                ? "Generated by Verified LLM"
                : "Generated by Template";

        return TravellerMessageDto.builder()
                .id(e.getId())
                .tripId(e.getTrip().getId())
                .recipientType(e.getRecipientType())
                .draftKind(e.getDraftKind())
                .bookingId(e.getBookingId())
                .subject(e.getSubject())
                .body(e.getBody())
                .channel(e.getChannel())
                .status(e.getStatus())
                .statusLabel(statusLabel)
                .generatedBy(e.getGeneratedBy())
                .generatedByLabel(generatedByLabel)
                .recipientMasked(e.getRecipientMasked())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .sentAt(e.getSentAt())
                .build();
    }
}
