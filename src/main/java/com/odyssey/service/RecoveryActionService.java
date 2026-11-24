package com.odyssey.service;

import com.odyssey.dto.response.RecoveryActionDto;
import com.odyssey.dto.response.RecoveryActionsResponse;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.PlanChecklistItemEntity;
import com.odyssey.entity.RecoveryPlanEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.RecoveryActionStatus;
import com.odyssey.model.enums.RecoveryActionType;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.PlanChecklistItemJpaRepository;
import com.odyssey.repository.jpa.RecoveryPlanJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persists and manages traveller-reported execution progress for approved recovery plan actions.
 * Does not execute purchases, cancellations, or external notifications.
 */
@Service
@RequiredArgsConstructor
public class RecoveryActionService {

    private static final String ORDER_PROTECTION_REASON =
            "Complete or skip the replacement booking action first so you are never left without transport.";

    private final RecoveryPlanJpaRepository recoveryPlanJpaRepository;
    private final PlanChecklistItemJpaRepository planChecklistItemJpaRepository;
    private final BookingJpaRepository bookingJpaRepository;
    private final AuditEntryJpaRepository auditEntryJpaRepository;
    private final TripJpaRepository tripJpaRepository;

    @Transactional(readOnly = true)
    public RecoveryActionsResponse getActions(Trip trip) {
        if (!isApprovedTrip(trip)) {
            return emptyResponse(trip.getId());
        }
        Optional<RecoveryPlanEntity> planOpt = recoveryPlanJpaRepository.findFirstByTripIdOrderByCreatedAtDesc(trip.getId());
        if (planOpt.isEmpty()) {
            return emptyResponse(trip.getId());
        }
        return buildResponse(trip.getId(), planOpt.get());
    }

    @Transactional
    public RecoveryActionsResponse completeAction(Trip trip, String actionId, String note) {
        RecoveryPlanEntity plan = requireApprovedPlan(trip);
        List<PlanChecklistItemEntity> allItems = planChecklistItemJpaRepository.findByRecoveryPlanIdOrderByItemOrderAsc(plan.getId());
        PlanChecklistItemEntity item = findActionInPlan(allItems, actionId);
        RecoveryActionType type = resolveType(item);

        if (isProtectedCleanupType(type) && hasPendingReplacementBooking(allItems)) {
            throw new ConflictException(ORDER_PROTECTION_REASON);
        }

        item.setStatus(RecoveryActionStatus.DONE);
        item.setDone(true);
        item.setCompletedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            item.setNote(note.trim());
        }
        if (item.getTripId() == null) {
            item.setTripId(trip.getId());
        }
        planChecklistItemJpaRepository.save(item);

        recordAudit(trip, plan, item, "RECOVERY_ACTION_COMPLETED", buildAuditDetails("Completed", item));
        return buildResponse(trip.getId(), plan);
    }

    @Transactional
    public RecoveryActionsResponse skipAction(Trip trip, String actionId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A note explaining why the action is skipped is required.");
        }
        RecoveryPlanEntity plan = requireApprovedPlan(trip);
        List<PlanChecklistItemEntity> allItems = planChecklistItemJpaRepository.findByRecoveryPlanIdOrderByItemOrderAsc(plan.getId());
        PlanChecklistItemEntity item = findActionInPlan(allItems, actionId);

        item.setStatus(RecoveryActionStatus.SKIPPED);
        item.setDone(false);
        item.setCompletedAt(null);
        item.setNote(note.trim());
        if (item.getTripId() == null) {
            item.setTripId(trip.getId());
        }
        planChecklistItemJpaRepository.save(item);

        recordAudit(trip, plan, item, "RECOVERY_ACTION_SKIPPED", buildAuditDetails("Skipped", item));
        return buildResponse(trip.getId(), plan);
    }

    @Transactional
    public RecoveryActionsResponse reopenAction(Trip trip, String actionId, String note) {
        RecoveryPlanEntity plan = requireApprovedPlan(trip);
        List<PlanChecklistItemEntity> allItems = planChecklistItemJpaRepository.findByRecoveryPlanIdOrderByItemOrderAsc(plan.getId());
        PlanChecklistItemEntity item = findActionInPlan(allItems, actionId);

        item.setStatus(RecoveryActionStatus.PENDING);
        item.setDone(false);
        item.setCompletedAt(null);
        if (note != null && !note.isBlank()) {
            item.setNote(note.trim());
        }
        if (item.getTripId() == null) {
            item.setTripId(trip.getId());
        }
        planChecklistItemJpaRepository.save(item);

        recordAudit(trip, plan, item, "RECOVERY_ACTION_REOPENED", buildAuditDetails("Reopened", item));
        return buildResponse(trip.getId(), plan);
    }

    private boolean isApprovedTrip(Trip trip) {
        if (trip == null || trip.getStatus() == null) {
            return false;
        }
        return "RESOLVED".equalsIgnoreCase(trip.getStatus())
                || "COMPLETED_BOOKINGS".equalsIgnoreCase(trip.getStatus());
    }

    private RecoveryPlanEntity requireApprovedPlan(Trip trip) {
        if (!isApprovedTrip(trip)) {
            throw new ConflictException("No approved recovery plan exists for trip " + trip.getId());
        }
        return recoveryPlanJpaRepository.findFirstByTripIdOrderByCreatedAtDesc(trip.getId())
                .orElseThrow(() -> new ConflictException("No approved recovery plan exists for trip " + trip.getId()));
    }

    private PlanChecklistItemEntity findActionInPlan(List<PlanChecklistItemEntity> items, String actionId) {
        return items.stream()
                .filter(i -> i.getId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Recovery action not found with ID: " + actionId));
    }

    private boolean hasPendingReplacementBooking(List<PlanChecklistItemEntity> items) {
        return items.stream()
                .anyMatch(item -> resolveType(item) == RecoveryActionType.BOOK_REPLACEMENT
                        && resolveStatus(item) == RecoveryActionStatus.PENDING);
    }

    private boolean isProtectedCleanupType(RecoveryActionType type) {
        return type == RecoveryActionType.CANCEL_OLD_BOOKING || type == RecoveryActionType.REQUEST_REFUND;
    }

    private RecoveryActionStatus resolveStatus(PlanChecklistItemEntity item) {
        if (item.getStatus() != null) {
            return item.getStatus();
        }
        return Boolean.TRUE.equals(item.getDone()) ? RecoveryActionStatus.DONE : RecoveryActionStatus.PENDING;
    }

    private RecoveryActionType resolveType(PlanChecklistItemEntity item) {
        if (item.getType() != null) {
            return item.getType();
        }
        String id = item.getId() != null ? item.getId().toUpperCase() : "";
        if (id.contains("BOOK_NEW_LEG")) return RecoveryActionType.BOOK_REPLACEMENT;
        if (id.contains("BOOK_REPLACEMENT_CAB")) return RecoveryActionType.BOOK_CAB;
        if (id.contains("MESSAGE_HOTEL")) return RecoveryActionType.MESSAGE_HOTEL;
        if (id.contains("CANCEL_OLD")) return RecoveryActionType.CANCEL_OLD_BOOKING;
        if (id.contains("REQUEST_") && id.contains("REFUND")) return RecoveryActionType.REQUEST_REFUND;
        return RecoveryActionType.CONFIRM_ARRIVAL;
    }

    private RecoveryActionsResponse buildResponse(String tripId, RecoveryPlanEntity plan) {
        List<PlanChecklistItemEntity> items = planChecklistItemJpaRepository.findByRecoveryPlanIdOrderByItemOrderAsc(plan.getId());
        Map<String, BookingEntity> bookingMap = bookingJpaRepository.findByTripIdOrderByStartTimeAsc(tripId).stream()
                .collect(Collectors.toMap(BookingEntity::getId, b -> b, (a, b) -> a));

        boolean blockCleanup = hasPendingReplacementBooking(items);
        int doneCount = 0;
        int skippedCount = 0;
        int pendingCount = 0;
        List<RecoveryActionDto> dtos = new ArrayList<>();

        for (PlanChecklistItemEntity item : items) {
            RecoveryActionType type = resolveType(item);
            RecoveryActionStatus status = resolveStatus(item);
            switch (status) {
                case DONE -> doneCount++;
                case SKIPPED -> skippedCount++;
                case PENDING -> pendingCount++;
            }

            String bookingId = resolveRelatedBookingId(item, type, plan, bookingMap);
            BookingEntity relatedBooking = bookingId != null ? bookingMap.get(bookingId) : null;
            String bookingReference = relatedBooking != null && hasText(relatedBooking.getBookingReference())
                    ? relatedBooking.getBookingReference()
                    : item.getBookingReference();
            String contactInfo = relatedBooking != null && hasText(relatedBooking.getHotelContactEmail())
                    ? relatedBooking.getHotelContactEmail()
                    : item.getContactInfo();

            boolean blocked = blockCleanup && isProtectedCleanupType(type);
            dtos.add(RecoveryActionDto.builder()
                    .id(item.getId())
                    .type(type)
                    .text(item.getText())
                    .status(status)
                    .note(item.getNote())
                    .completedAt(item.getCompletedAt())
                    .relatedBookingId(bookingId)
                    .bookingReference(bookingReference)
                    .contactInfo(contactInfo)
                    .canComplete(!blocked)
                    .disabledReason(blocked ? ORDER_PROTECTION_REASON : null)
                    .order(item.getItemOrder() != null ? item.getItemOrder() : 0)
                    .build());
        }

        int totalCount = dtos.size();
        return RecoveryActionsResponse.builder()
                .tripId(tripId)
                .planId(plan.getId())
                .approved(true)
                .doneCount(doneCount)
                .skippedCount(skippedCount)
                .pendingCount(pendingCount)
                .totalCount(totalCount)
                .progressText(doneCount + " of " + totalCount + " done")
                .actions(dtos)
                .build();
    }

    private String resolveRelatedBookingId(
            PlanChecklistItemEntity item,
            RecoveryActionType type,
            RecoveryPlanEntity plan,
            Map<String, BookingEntity> bookingMap) {
        String bookingId = item.getRelatedBookingId();
        if (type == RecoveryActionType.BOOK_REPLACEMENT
                && (!hasText(bookingId) || !bookingMap.containsKey(bookingId))
                && hasText(plan.getNewLegId())) {
            return plan.getNewLegId();
        }
        return bookingId;
    }

    private RecoveryActionsResponse emptyResponse(String tripId) {
        return RecoveryActionsResponse.builder()
                .tripId(tripId)
                .planId(null)
                .approved(false)
                .doneCount(0)
                .skippedCount(0)
                .pendingCount(0)
                .totalCount(0)
                .progressText("0 of 0 done")
                .actions(List.of())
                .build();
    }

    private void recordAudit(Trip trip, RecoveryPlanEntity plan, PlanChecklistItemEntity item, String actionName, String details) {
        TripEntity tripRef = tripJpaRepository.getReferenceById(trip.getId());
        String bookingId = hasText(item.getRelatedBookingId()) ? item.getRelatedBookingId()
                : (resolveType(item) == RecoveryActionType.BOOK_REPLACEMENT ? plan.getNewLegId() : null);
        auditEntryJpaRepository.save(AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(tripRef)
                .bookingId(bookingId)
                .userId(trip.getUserId())
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER.name())
                .action(actionName)
                .details(details)
                .build());
    }

    private String buildAuditDetails(String verb, PlanChecklistItemEntity item) {
        StringBuilder sb = new StringBuilder();
        sb.append(verb).append(" action ").append(resolveType(item)).append(" (").append(item.getId()).append(")");
        if (hasText(item.getNote())) {
            sb.append(". Note: ").append(item.getNote());
        }
        return sb.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

