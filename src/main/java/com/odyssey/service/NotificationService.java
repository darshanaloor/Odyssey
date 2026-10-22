package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.dto.response.ChannelStatusResponse;
import com.odyssey.dto.response.NotificationHistoryResponse;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.model.*;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.notification.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationService {

    private final EmailChannel emailChannel;
    private final SmsChannel smsChannel;
    private final SimulatedChannel simulatedChannel;
    private final NotificationJpaRepository notificationJpaRepository;
    private final TripJpaRepository tripJpaRepository;
    private final TripRepository tripRepository;
    private final RecoveryProperties recoveryProperties;

    @Autowired
    public NotificationService(
            EmailChannel emailChannel,
            SmsChannel smsChannel,
            SimulatedChannel simulatedChannel,
            NotificationJpaRepository notificationJpaRepository,
            TripJpaRepository tripJpaRepository,
            TripRepository tripRepository,
            RecoveryProperties recoveryProperties) {
        this.emailChannel = emailChannel;
        this.smsChannel = smsChannel;
        this.simulatedChannel = simulatedChannel;
        this.notificationJpaRepository = notificationJpaRepository;
        this.tripJpaRepository = tripJpaRepository;
        this.tripRepository = tripRepository;
        this.recoveryProperties = recoveryProperties;
    }

    public NotificationService() {
        this(null, null, null, null, null, null, new RecoveryProperties());
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Backward-compatible method used by existing tests and legacy callers.
     * Generates an alert payload without emoji or demo strings on non-demo trips.
     */
    public NotificationMessage generateAlert(Trip trip, Booking rootDisrupted, Disruption disruption, TripImpactReport impactReport) {
        String alertLevel = disruption.getType() == DisruptionType.CANCELLATION ||
                "true".equalsIgnoreCase(impactReport.getMustArriveByBreached()) ||
                "PENDING_RECOVERY".equalsIgnoreCase(impactReport.getMustArriveByBreached()) ?
                "CRITICAL" : "WARNING";

        String title = trip.getTitle() != null ? trip.getTitle() : "Your Trip";
        StringBuilder sb = new StringBuilder();

        sb.append("[ALERT] [ODYSSEY TRAVEL ALERT]\n");
        sb.append("Trip: ").append(title).append("\n");

        if (disruption.getType() == DisruptionType.CANCELLATION) {
            sb.append("[DISRUPTION] ").append(rootDisrupted.getType()).append(" ").append(rootDisrupted.getProvider())
                    .append(" has been CANCELLED. Reason: ").append(disruption.getReason()).append("\n");
        } else if (disruption.getType() == DisruptionType.FLIGHT_DELAY) {
            sb.append("[DISRUPTION] ").append(rootDisrupted.getProvider())
                    .append(" delayed by ").append(disruption.getDelayMinutes()).append(" mins. ")
                    .append("Reason: ").append(disruption.getReason()).append("\n");
        } else {
            sb.append("[DISRUPTION] ").append(rootDisrupted.getProvider())
                    .append(" overbooked. Reason: ").append(disruption.getReason()).append("\n");
        }

        if (impactReport.getSummary() != null && !impactReport.getSummary().isBlank()) {
            sb.append("Summary: ").append(impactReport.getSummary()).append("\n");
        }

        if (impactReport.getImpactedBookings() != null && !impactReport.getImpactedBookings().isEmpty()) {
            sb.append("Downstream Impacts:\n");
            for (BookingImpact bi : impactReport.getImpactedBookings()) {
                String tag = bi.getStatus() == BookingStatus.CANCELLED ? "[CANCELLED]" :
                        bi.getStatus() == BookingStatus.AT_RISK ? "[AT RISK]" : "[SAFE]";
                sb.append(String.format(" %s %s (%s): %s\n",
                        tag, bi.getBookingType(), bi.getBookingId(), bi.getReason()));
            }
        }

        if (impactReport.getMustArriveByBreached() != null) {
            sb.append("MustArriveBy Status: ").append(impactReport.getMustArriveByBreached()).append("\n");
        }

        if (impactReport.getHardCommitmentsBreached() != null && !impactReport.getHardCommitmentsBreached().isEmpty()) {
            sb.append("[WARNING] Hard Commitments at Risk: ")
                    .append(String.join(", ", impactReport.getHardCommitmentsBreached())).append("\n");
        }

        sb.append("Status: Odyssey Agent is evaluating automated re-routing options.");

        String travellerName = trip.getTravellerName() != null && !trip.getTravellerName().isBlank() ? trip.getTravellerName() : "Traveller";
        String travellerPhone = trip.getTravellerPhone() != null && !trip.getTravellerPhone().isBlank() ? trip.getTravellerPhone() : "";
        String recipient = travellerPhone.isEmpty() ? travellerName : String.format("%s (%s)", travellerPhone, travellerName);

        return NotificationMessage.builder()
                .channel("SMS")
                .recipient(travellerPhone.isEmpty() ? travellerName : travellerPhone)
                .alertLevel(alertLevel)
                .subject("[Odyssey Alert] Disruption on " + title)
                .message(sb.toString())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Dispatch Disruption Alert across enabled channels with de-duplication and quiet-hours handling.
     */
    @Transactional
    public void sendDisruptionAlert(Trip trip, Booking rootDisrupted, Disruption disruption, TripImpactReport impactReport) {
        if (trip == null || disruption == null) {
            return;
        }

        String dedupKey = trip.getId() + ":DISRUPTION_ALERT:" + disruption.getId();
        if (notificationJpaRepository.existsByTripIdAndDedupKey(trip.getId(), dedupKey)) {
            log.info("Duplicate disruption alert skipped for trip {} with dedupKey {}", trip.getId(), dedupKey);
            return;
        }

        boolean isCritical = disruption.getType() == DisruptionType.CANCELLATION ||
                "true".equalsIgnoreCase(impactReport.getMustArriveByBreached()) ||
                (impactReport.getHardCommitmentsBreached() != null && !impactReport.getHardCommitmentsBreached().isEmpty());

        if (!isCritical && isQuietHours(LocalTime.now())) {
            log.info("Non-critical disruption alert suppressed during quiet hours for trip {}", trip.getId());
            return;
        }

        NotificationMessage base = generateAlert(trip, rootDisrupted, disruption, impactReport);
        base.setTripId(trip.getId());
        base.setDisruptionId(disruption.getId());
        base.setEventType("DISRUPTION_ALERT");
        base.setDedupKey(dedupKey);

        dispatchToEnabledChannels(trip, base);
    }

    /**
     * Dispatch Recovery Plan Ready alert across enabled channels.
     */
    @Transactional
    public void sendPlanReadyAlert(Trip trip, Option option, RecoveryPlan plan) {
        if (trip == null || option == null) {
            return;
        }

        String disruptionId = (trip.getActiveDisruptions() != null && !trip.getActiveDisruptions().isEmpty())
                ? trip.getActiveDisruptions().get(0).getId()
                : "none";
        String dedupKey = trip.getId() + ":PLAN_READY:" + disruptionId + ":" + option.getId();

        if (notificationJpaRepository.existsByTripIdAndDedupKey(trip.getId(), dedupKey)) {
            log.info("Duplicate plan ready alert skipped for trip {} with dedupKey {}", trip.getId(), dedupKey);
            return;
        }

        if (isQuietHours(LocalTime.now())) {
            log.info("Plan ready alert suppressed during quiet hours for trip {}", trip.getId());
            return;
        }

        Booking firstLeg = (option.getLegs() != null && !option.getLegs().isEmpty()) ? option.getLegs().get(0) : null;
        String providerName = firstLeg != null ? firstLeg.getProvider() : (option.getLabel() != null ? option.getLabel().name() : "Replacement Route");
        String modeType = firstLeg != null && firstLeg.getType() != null ? firstLeg.getType().name() : "TRANSIT";
        LocalDateTime departureTime = firstLeg != null ? firstLeg.getStartTime() : null;
        LocalDateTime arrivalTime = option.getArrivalTime() != null ? option.getArrivalTime() : (firstLeg != null ? firstLeg.getEndTime() : null);

        BigDecimal netCost = (plan != null && plan.getCostBreakdown() != null && plan.getCostBreakdown().getNetTripCost() != null)
                ? plan.getCostBreakdown().getNetTripCost()
                : (option.getTotalCost() != null ? option.getTotalCost() : BigDecimal.ZERO);

        StringBuilder sb = new StringBuilder();
        sb.append("[ALERT] [ODYSSEY RECOVERY PLAN READY]\n");
        sb.append("Trip: ").append(trip.getTitle()).append("\n");
        sb.append("Chosen Option: ").append(providerName).append(" (").append(modeType).append(")\n");
        if (departureTime != null) {
            sb.append("Departure: ").append(departureTime.format(TIME_FMT)).append("\n");
        }
        if (arrivalTime != null) {
            sb.append("Arrival: ").append(arrivalTime.format(TIME_FMT)).append("\n");
        }
        sb.append("Net Cost: Rs ").append(netCost.toPlainString()).append("\n");

        if (plan != null && plan.getChecklist() != null && !plan.getChecklist().isEmpty()) {
            sb.append("Checklist Steps:\n");
            int count = Math.min(3, plan.getChecklist().size());
            for (int i = 0; i < count; i++) {
                sb.append(i + 1).append(". ").append(plan.getChecklist().get(i).getText()).append("\n");
            }
        }

        NotificationMessage msg = NotificationMessage.builder()
                .tripId(trip.getId())
                .disruptionId(disruptionId.equals("none") ? null : disruptionId)
                .eventType("PLAN_READY")
                .alertLevel("INFO")
                .subject("[Odyssey Alert] Recovery Plan Ready for " + trip.getTitle())
                .message(sb.toString())
                .dedupKey(dedupKey)
                .timestamp(LocalDateTime.now())
                .build();

        dispatchToEnabledChannels(trip, msg);
    }

    /**
     * Dispatch Plan Approved alert across enabled channels.
     */
    @Transactional
    public void sendPlanApprovedAlert(Trip trip, RecoveryPlan plan) {
        if (trip == null || plan == null) {
            return;
        }

        String disruptionId = (trip.getActiveDisruptions() != null && !trip.getActiveDisruptions().isEmpty())
                ? trip.getActiveDisruptions().get(0).getId()
                : "none";
        String optionId = plan.getChosenOptionId() != null ? plan.getChosenOptionId() : "opt";
        String dedupKey = trip.getId() + ":PLAN_APPROVED:" + disruptionId + ":" + optionId;

        if (notificationJpaRepository.existsByTripIdAndDedupKey(trip.getId(), dedupKey)) {
            log.info("Duplicate plan approved alert skipped for trip {} with dedupKey {}", trip.getId(), dedupKey);
            return;
        }

        if (isQuietHours(LocalTime.now())) {
            log.info("Plan approved alert suppressed during quiet hours for trip {}", trip.getId());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[ALERT] [ODYSSEY PLAN CONFIRMED]\n");
        sb.append("Trip: ").append(trip.getTitle()).append("\n");
        sb.append("Status: Your updated itinerary has been confirmed and locked.\n");
        sb.append("Confirmed Itinerary:\n");

        if (trip.getBookings() != null) {
            for (Booking b : trip.getBookings()) {
                sb.append(String.format(" - %s (%s): %s to %s [%s - %s]\n",
                        b.getType(), b.getProvider(), b.getFrom(), b.getTo(),
                        b.getStartTime() != null ? b.getStartTime().format(TIME_FMT) : "N/A",
                        b.getEndTime() != null ? b.getEndTime().format(TIME_FMT) : "N/A"));
            }
        }

        NotificationMessage msg = NotificationMessage.builder()
                .tripId(trip.getId())
                .disruptionId(disruptionId.equals("none") ? null : disruptionId)
                .eventType("PLAN_APPROVED")
                .alertLevel("INFO")
                .subject("[Odyssey Alert] Plan Confirmed for " + trip.getTitle())
                .message(sb.toString())
                .dedupKey(dedupKey)
                .timestamp(LocalDateTime.now())
                .build();

        dispatchToEnabledChannels(trip, msg);
    }

    /**
     * Send test alert on enabled channels, rate-limited to 3 per hour per trip.
     */
    @Transactional
    public List<NotificationHistoryResponse> sendTestAlert(Trip trip) {
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        long recentCount = notificationJpaRepository.countByTripIdAndEventTypeAndCreatedAtAfter(
                trip.getId(), "TEST_ALERT", LocalDateTime.now().minusHours(1));

        if (recentCount >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit reached: Maximum 3 test alerts per hour for this trip");
        }

        String body = "[TEST ALERT] Odyssey Notification Verification\n" +
                "Trip: " + trip.getTitle() + "\n" +
                "Status: Notification service verification alert delivered successfully.\n" +
                "Time: " + LocalDateTime.now().format(TIME_FMT);

        NotificationMessage msg = NotificationMessage.builder()
                .tripId(trip.getId())
                .eventType("TEST_ALERT")
                .alertLevel("INFO")
                .subject("[Odyssey Test] Alert Verification for " + trip.getTitle())
                .message(body)
                .timestamp(LocalDateTime.now())
                .build();

        List<NotificationEntity> saved = dispatchToEnabledChannels(trip, msg);
        return saved.stream().map(this::toHistoryResponse).collect(Collectors.toList());
    }

    /**
     * Send hotel message directly by email.
     */
    @Transactional
    public NotificationHistoryResponse sendHotelEmail(Trip trip, RecoveryPlan plan) {
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        Booking hotelBooking = trip.getBookings().stream()
                .filter(b -> b.getType() == BookingType.HOTEL)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No HOTEL booking found in this trip"));

        String hotelEmail = hotelBooking.getHotelContactEmail();
        if (hotelEmail == null || hotelEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hotel booking does not have a contact email configured");
        }

        if (!emailChannel.isActive()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email channel (SMTP) is not active");
        }

        String hotelMessageText = (plan != null && plan.getHotelMessage() != null && !plan.getHotelMessage().isBlank())
                ? plan.getHotelMessage()
                : "Dear Hotel Reservations,\nPlease be informed of travel updates for guest " + trip.getTravellerName() + ".";

        NotificationMessage emailMsg = NotificationMessage.builder()
                .tripId(trip.getId())
                .channel("EMAIL")
                .eventType("HOTEL_MESSAGE")
                .recipient(hotelEmail.trim())
                .replyTo(trip.getTravellerEmail())
                .subject("Urgent: Reservation Update for Traveller " + trip.getTravellerName())
                .message(hotelMessageText)
                .alertLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        SendResult result = emailChannel.send(emailMsg);

        TripEntity tripEntity = tripJpaRepository.findById(trip.getId()).orElse(null);
        NotificationEntity entity = NotificationEntity.builder()
                .id(IdGenerator.generate("NTF"))
                .trip(tripEntity)
                .eventType("HOTEL_MESSAGE")
                .channel("EMAIL")
                .recipientMasked(NotificationMasker.maskEmail(hotelEmail))
                .subject(emailMsg.getSubject())
                .body(hotelMessageText)
                .status(result.getStatus().name())
                .error(result.getError())
                .providerMessageId(result.getProviderMessageId())
                .createdAt(LocalDateTime.now())
                .build();

        if (tripEntity != null) {
            NotificationEntity saved = notificationJpaRepository.save(entity);
            if (saved != null) {
                entity = saved;
            }
        }

        // Add audit entry on trip
        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("HOTEL_EMAIL_SENT")
                .details("Sent hotel disruption notice to " + NotificationMasker.maskEmail(hotelEmail))
                .build());
        tripRepository.save(trip);

        return toHistoryResponse(entity);
    }

    /**
     * Get alert history for a trip, newest first.
     */
    public List<NotificationHistoryResponse> getNotificationHistory(String tripId) {
        return notificationJpaRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get overall status badges for all channels.
     */
    public ChannelStatusResponse getChannelStatus() {
        String emailStatus = emailChannel.isActive() ? "Active" : "Simulated";
        String smsStatus = smsChannel.isActive() ? "Active" : "Simulated";
        String whatsappStatus = smsChannel.isWhatsAppActive() ? "Active" : (smsChannel.isActive() ? "Simulated" : "Off");

        return ChannelStatusResponse.builder()
                .email(emailStatus)
                .sms(smsStatus)
                .whatsapp(whatsappStatus)
                .build();
    }

    public boolean isQuietHours(LocalTime now) {
        LocalTime start = recoveryProperties.getQuietHoursStart();
        LocalTime end = recoveryProperties.getQuietHoursEnd();
        if (start == null || end == null) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // Spans midnight (e.g., 22:00 to 07:00)
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    private List<NotificationEntity> dispatchToEnabledChannels(Trip trip, NotificationMessage baseMsg) {
        List<NotificationEntity> results = new ArrayList<>();
        TripEntity tripEntity = tripJpaRepository.findById(trip.getId()).orElse(null);

        // 1. Email Channel
        if (trip.getNotifyEmail() == null || trip.getNotifyEmail()) {
            NotificationMessage emailMsg = NotificationMessage.builder()
                    .channel("EMAIL")
                    .recipient(trip.getTravellerEmail())
                    .subject(baseMsg.getSubject())
                    .message(baseMsg.getMessage())
                    .alertLevel(baseMsg.getAlertLevel())
                    .tripId(trip.getId())
                    .disruptionId(baseMsg.getDisruptionId())
                    .eventType(baseMsg.getEventType())
                    .dedupKey(baseMsg.getDedupKey())
                    .build();

            SendResult res = safeSend(emailChannel, emailMsg);
            results.add(recordNotification(tripEntity, emailMsg, res,
                    NotificationMasker.maskEmail(trip.getTravellerEmail())));
        }

        // 2. SMS Channel
        if (trip.getNotifySms() == null || trip.getNotifySms()) {
            NotificationMessage smsMsg = NotificationMessage.builder()
                    .channel("SMS")
                    .recipient(trip.getTravellerPhone())
                    .subject(baseMsg.getSubject())
                    .message(baseMsg.getMessage())
                    .alertLevel(baseMsg.getAlertLevel())
                    .tripId(trip.getId())
                    .disruptionId(baseMsg.getDisruptionId())
                    .eventType(baseMsg.getEventType())
                    .dedupKey(baseMsg.getDedupKey())
                    .build();

            SendResult res = safeSend(smsChannel, smsMsg);
            results.add(recordNotification(tripEntity, smsMsg, res,
                    NotificationMasker.maskPhone(trip.getTravellerPhone())));
        }

        // 3. WhatsApp Channel
        if (Boolean.TRUE.equals(trip.getNotifyWhatsapp())) {
            NotificationMessage waMsg = NotificationMessage.builder()
                    .channel("WHATSAPP")
                    .recipient(trip.getTravellerPhone())
                    .subject(baseMsg.getSubject())
                    .message(baseMsg.getMessage())
                    .alertLevel(baseMsg.getAlertLevel())
                    .tripId(trip.getId())
                    .disruptionId(baseMsg.getDisruptionId())
                    .eventType(baseMsg.getEventType())
                    .dedupKey(baseMsg.getDedupKey())
                    .build();

            SendResult res = safeSend(smsChannel, waMsg);
            results.add(recordNotification(tripEntity, waMsg, res,
                    NotificationMasker.maskPhone(trip.getTravellerPhone())));
        }

        return results;
    }

    private SendResult safeSend(NotificationChannel channel, NotificationMessage message) {
        try {
            return channel.send(message);
        } catch (Exception e) {
            log.error("Unexpected error in channel {}: {}", channel.getChannelType(), e.getMessage());
            return SendResult.builder()
                    .status(SendStatus.FAILED)
                    .error(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build();
        }
    }

    private NotificationEntity recordNotification(TripEntity tripEntity, NotificationMessage msg,
                                                  SendResult result, String maskedRecipient) {
        NotificationEntity entity = NotificationEntity.builder()
                .id(IdGenerator.generate("NTF"))
                .trip(tripEntity)
                .disruptionId(msg.getDisruptionId())
                .eventType(msg.getEventType())
                .channel(msg.getChannel())
                .recipientMasked(maskedRecipient)
                .subject(msg.getSubject() != null ? msg.getSubject() : "Odyssey Alert")
                .body(msg.getMessage() != null ? msg.getMessage() : "")
                .status(result.getStatus().name())
                .error(result.getError())
                .providerMessageId(result.getProviderMessageId())
                .dedupKey(msg.getDedupKey())
                .createdAt(LocalDateTime.now())
                .build();

        if (tripEntity != null) {
            try {
                return notificationJpaRepository.save(entity);
            } catch (Exception e) {
                log.error("Failed to persist notification record: {}", e.getMessage());
            }
        }
        return entity;
    }

    private NotificationHistoryResponse toHistoryResponse(NotificationEntity entity) {
        return NotificationHistoryResponse.builder()
                .id(entity.getId())
                .tripId(entity.getTrip() != null ? entity.getTrip().getId() : null)
                .disruptionId(entity.getDisruptionId())
                .eventType(entity.getEventType())
                .channel(entity.getChannel())
                .recipientMasked(entity.getRecipientMasked())
                .subject(entity.getSubject())
                .body(entity.getBody())
                .status(entity.getStatus())
                .error(entity.getError())
                .providerMessageId(entity.getProviderMessageId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
