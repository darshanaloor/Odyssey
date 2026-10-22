package com.odyssey.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.odyssey.entity.*;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TripEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(TripEntityMapper.class);
    private final ObjectMapper objectMapper;

    public TripEntityMapper() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public Trip toModel(TripEntity entity) {
        if (entity == null) return null;

        List<HardCommitment> structuredCommitments = entity.getHardCommitments() != null
                ? entity.getHardCommitments().stream()
                        .map(h -> HardCommitment.builder()
                                .id(h.getId())
                                .name(h.getName())
                                .commitmentTime(h.getCommitmentTime())
                                .location(h.getLocation())
                                .build())
                        .collect(Collectors.toList())
                : new ArrayList<>();

        Constraints constraints = null;
        if (entity.getConstraints() != null) {
            TripConstraintsEntity c = entity.getConstraints();
            List<BookingType> preferredModes = new ArrayList<>();
            if (c.getPreferredModes() != null && !c.getPreferredModes().isBlank()) {
                preferredModes = Arrays.stream(c.getPreferredModes().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> {
                            try {
                                return BookingType.valueOf(s);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            List<String> hardCommitmentNames = entity.getHardCommitments().stream()
                    .map(HardCommitmentEntity::getName)
                    .collect(Collectors.toList());

            constraints = Constraints.builder()
                    .maxBudget(c.getMaxBudget())
                    .mustArriveBy(c.getMustArriveBy())
                    .avoidOvernightTravel(c.getAvoidOvernightTravel())
                    .preferredModes(preferredModes)
                    .hardCommitments(hardCommitmentNames)
                    .structuredCommitments(structuredCommitments)
                    .build();
        }

        List<Booking> bookings = entity.getBookings().stream()
                .map(this::toBookingModel)
                .collect(Collectors.toList());

        List<AuditEntry> auditLog = entity.getAuditEntries().stream()
                .map(this::toAuditEntryModel)
                .sorted(Comparator.comparing(AuditEntry::getTimestamp))
                .collect(Collectors.toList());

        List<Disruption> disruptions = entity.getDisruptions().stream()
                .map(this::toDisruptionModel)
                .collect(Collectors.toList());

        List<Option> options = entity.getRecoveryOptions().stream()
                .map(this::toOptionModel)
                .collect(Collectors.toList());

        RecoveryPlan latestPlan = null;
        if (!entity.getRecoveryPlans().isEmpty()) {
            RecoveryPlanEntity latestPlanEntity = entity.getRecoveryPlans().stream()
                    .max(Comparator.comparing(RecoveryPlanEntity::getCreatedAt))
                    .orElse(null);
            if (latestPlanEntity != null) {
                latestPlan = toRecoveryPlanModel(latestPlanEntity);
            }
        }

        return Trip.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .travellerName(entity.getTravellerName())
                .travellerPhone(entity.getTravellerPhone())
                .travellerEmail(entity.getTravellerEmail())
                .needsDestinationCab(Boolean.TRUE.equals(entity.getNeedsDestinationCab()))
                .destinationCabDropoff(entity.getDestinationCabDropoff())
                .needsHotel(Boolean.TRUE.equals(entity.getNeedsHotel()))
                .notifyEmail(entity.getNotifyEmail() == null || entity.getNotifyEmail())
                .notifySms(entity.getNotifySms() == null || entity.getNotifySms())
                .notifyWhatsapp(Boolean.TRUE.equals(entity.getNotifyWhatsapp()))
                .status(entity.getStatus())
                .userId(entity.getUserId())
                .constraints(constraints)
                .bookings(bookings)
                .auditLog(auditLog)
                .activeDisruptions(disruptions)
                .hardCommitments(structuredCommitments)
                .options(options)
                .latestPlan(latestPlan)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public TripEntity toEntity(Trip model) {
        if (model == null) return null;

        TripEntity entity = TripEntity.builder()
                .id(model.getId())
                .title(model.getTitle())
                .travellerName(model.getTravellerName() != null ? model.getTravellerName() : "Traveller")
                .travellerPhone(model.getTravellerPhone())
                .travellerEmail(model.getTravellerEmail())
                .userId(model.getUserId())
                .needsDestinationCab(Boolean.TRUE.equals(model.getNeedsDestinationCab()))
                .destinationCabDropoff(model.getDestinationCabDropoff())
                .needsHotel(Boolean.TRUE.equals(model.getNeedsHotel()))
                .notifyEmail(model.getNotifyEmail() == null || model.getNotifyEmail())
                .notifySms(model.getNotifySms() == null || model.getNotifySms())
                .notifyWhatsapp(Boolean.TRUE.equals(model.getNotifyWhatsapp()))
                .status(model.getStatus() != null ? model.getStatus() : "CONFIRMED")
                .createdAt(model.getCreatedAt() != null ? model.getCreatedAt() : LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (model.getConstraints() != null) {
            Constraints c = model.getConstraints();
            String preferredModes = c.getPreferredModes() != null
                    ? c.getPreferredModes().stream().map(Enum::name).collect(Collectors.joining(","))
                    : "";
            TripConstraintsEntity ce = TripConstraintsEntity.builder()
                    .id("CONSTR-" + entity.getId())
                    .trip(entity)
                    .maxBudget(c.getMaxBudget())
                    .mustArriveBy(c.getMustArriveBy())
                    .avoidOvernightTravel(c.getAvoidOvernightTravel())
                    .preferredModes(preferredModes)
                    .build();
            entity.setConstraints(ce);

            List<HardCommitmentEntity> commitments = new ArrayList<>();
            if (model.getHardCommitments() != null && !model.getHardCommitments().isEmpty()) {
                for (int i = 0; i < model.getHardCommitments().size(); i++) {
                    HardCommitment hc = model.getHardCommitments().get(i);
                    commitments.add(HardCommitmentEntity.builder()
                            .id(hc.getId() != null ? hc.getId() : "HC-" + entity.getId() + "-" + (i + 1))
                            .trip(entity)
                            .name(hc.getName())
                            .commitmentTime(hc.getCommitmentTime() != null ? hc.getCommitmentTime() : (c.getMustArriveBy() != null ? c.getMustArriveBy() : LocalDateTime.now()))
                            .location(hc.getLocation() != null ? hc.getLocation() : "")
                            .build());
                }
            } else if (c.getStructuredCommitments() != null && !c.getStructuredCommitments().isEmpty()) {
                for (int i = 0; i < c.getStructuredCommitments().size(); i++) {
                    HardCommitment hc = c.getStructuredCommitments().get(i);
                    commitments.add(HardCommitmentEntity.builder()
                            .id(hc.getId() != null ? hc.getId() : "HC-" + entity.getId() + "-" + (i + 1))
                            .trip(entity)
                            .name(hc.getName())
                            .commitmentTime(hc.getCommitmentTime() != null ? hc.getCommitmentTime() : (c.getMustArriveBy() != null ? c.getMustArriveBy() : LocalDateTime.now()))
                            .location(hc.getLocation() != null ? hc.getLocation() : "")
                            .build());
                }
            } else if (c.getHardCommitments() != null) {
                for (int i = 0; i < c.getHardCommitments().size(); i++) {
                    String commName = c.getHardCommitments().get(i);
                    commitments.add(HardCommitmentEntity.builder()
                            .id("HC-" + entity.getId() + "-" + (i + 1))
                            .trip(entity)
                            .name(commName)
                            .commitmentTime(c.getMustArriveBy() != null ? c.getMustArriveBy() : LocalDateTime.now())
                            .location("")
                            .build());
                }
            }
            entity.setHardCommitments(commitments);
        }

        if (model.getBookings() != null) {
            List<BookingEntity> bookingEntities = model.getBookings().stream()
                    .map(b -> toBookingEntity(b, entity))
                    .collect(Collectors.toList());
            entity.setBookings(bookingEntities);
        }

        if (model.getAuditLog() != null) {
            List<AuditEntryEntity> auditEntities = model.getAuditLog().stream()
                    .map(a -> toAuditEntryEntity(a, entity))
                    .collect(Collectors.toList());
            entity.setAuditEntries(auditEntities);
        }

        if (model.getActiveDisruptions() != null) {
            List<DisruptionEntity> disruptionEntities = model.getActiveDisruptions().stream()
                    .map(d -> toDisruptionEntity(d, entity))
                    .collect(Collectors.toList());
            entity.setDisruptions(disruptionEntities);
        }

        if (model.getOptions() != null) {
            List<RecoveryOptionEntity> optionEntities = model.getOptions().stream()
                    .map(o -> toOptionEntity(o, entity))
                    .collect(Collectors.toList());
            entity.setRecoveryOptions(optionEntities);
        }

        if (model.getLatestPlan() != null) {
            entity.getRecoveryPlans().add(toRecoveryPlanEntity(model.getLatestPlan(), entity));
        }

        return entity;
    }

    public Booking toBookingModel(BookingEntity entity) {
        if (entity == null) return null;
        BookingType type;
        try {
            type = BookingType.valueOf(entity.getType());
        } catch (Exception e) {
            type = BookingType.FLIGHT;
        }
        BookingStatus status;
        try {
            status = BookingStatus.valueOf(entity.getStatus());
        } catch (Exception e) {
            status = BookingStatus.CONFIRMED;
        }

        return Booking.builder()
                .id(entity.getId())
                .type(type)
                .provider(entity.getProvider())
                .from(entity.getFromLocation())
                .to(entity.getToLocation())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .cost(entity.getCost())
                .status(status)
                .source(entity.getType() != null ? "INVENTORY" : "INVENTORY")
                .dependsOn(entity.getDependsOn() != null ? new ArrayList<>(entity.getDependsOn()) : new ArrayList<>())
                .lastStatusState(entity.getLastStatusState())
                .lastDelayMinutes(entity.getLastDelayMinutes())
                .lastCheckedAt(entity.getLastCheckedAt())
                .lastStatusSource(entity.getLastStatusSource())
                .statusCheckError(entity.getStatusCheckError())
                .lastDedupKey(entity.getLastDedupKey())
                .hotelContactEmail(entity.getHotelContactEmail())
                .bookingReference(entity.getBookingReference())
                .build();
    }

    public BookingEntity toBookingEntity(Booking model, TripEntity trip) {
        if (model == null) return null;
        return BookingEntity.builder()
                .id(model.getId())
                .trip(trip)
                .type(model.getType() != null ? model.getType().name() : BookingType.FLIGHT.name())
                .provider(model.getProvider())
                .fromLocation(model.getFrom())
                .toLocation(model.getTo())
                .startTime(model.getStartTime())
                .endTime(model.getEndTime())
                .cost(model.getCost())
                .status(model.getStatus() != null ? model.getStatus().name() : BookingStatus.CONFIRMED.name())
                .dependsOn(model.getDependsOn() != null ? new ArrayList<>(model.getDependsOn()) : new ArrayList<>())
                .lastStatusState(model.getLastStatusState())
                .lastDelayMinutes(model.getLastDelayMinutes())
                .lastCheckedAt(model.getLastCheckedAt())
                .lastStatusSource(model.getLastStatusSource())
                .statusCheckError(model.getStatusCheckError())
                .lastDedupKey(model.getLastDedupKey())
                .hotelContactEmail(model.getHotelContactEmail())
                .bookingReference(model.getBookingReference())
                .build();
    }

    public AuditEntry toAuditEntryModel(AuditEntryEntity entity) {
        if (entity == null) return null;
        AuditActor actor;
        try {
            actor = AuditActor.valueOf(entity.getActor());
        } catch (Exception e) {
            actor = AuditActor.SYSTEM;
        }
        return AuditEntry.builder()
                .id(entity.getId())
                .timestamp(entity.getTimestamp())
                .actor(actor)
                .action(entity.getAction())
                .details(entity.getDetails())
                .build();
    }

    public AuditEntryEntity toAuditEntryEntity(AuditEntry model, TripEntity trip) {
        if (model == null) return null;
        return AuditEntryEntity.builder()
                .id(model.getId())
                .trip(trip)
                .timestamp(model.getTimestamp() != null ? model.getTimestamp() : LocalDateTime.now())
                .actor(model.getActor() != null ? model.getActor().name() : AuditActor.SYSTEM.name())
                .action(model.getAction())
                .details(model.getDetails())
                .build();
    }

    public Disruption toDisruptionModel(DisruptionEntity entity) {
        if (entity == null) return null;
        DisruptionType type;
        try {
            type = DisruptionType.valueOf(entity.getType());
        } catch (Exception e) {
            type = DisruptionType.FLIGHT_DELAY;
        }
        return Disruption.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .type(type)
                .delayMinutes(entity.getDelayMinutes() != null ? entity.getDelayMinutes() : 0)
                .reason(entity.getReason())
                .disruptionTime(entity.getDisruptionTime())
                .source(entity.getSource())
                .actor(entity.getActor())
                .build();
    }

    public DisruptionEntity toDisruptionEntity(Disruption model, TripEntity trip) {
        if (model == null) return null;
        return DisruptionEntity.builder()
                .id(model.getId())
                .trip(trip)
                .bookingId(model.getBookingId())
                .type(model.getType() != null ? model.getType().name() : DisruptionType.FLIGHT_DELAY.name())
                .delayMinutes(model.getDelayMinutes())
                .reason(model.getReason())
                .disruptionTime(model.getDisruptionTime() != null ? model.getDisruptionTime() : LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .source(model.getSource() != null ? model.getSource() : "MANUAL")
                .actor(model.getActor() != null ? model.getActor() : "USER")
                .build();
    }

    public Option toOptionModel(RecoveryOptionEntity entity) {
        if (entity == null) return null;
        OptionLabel label;
        try {
            label = OptionLabel.valueOf(entity.getLabel());
        } catch (Exception e) {
            label = OptionLabel.SAFEST;
        }
        List<String> met = deserializeStringList(entity.getConstraintsMet());
        List<String> violated = deserializeStringList(entity.getConstraintsViolated());

        List<Booking> legs = entity.getLegs().stream()
                .sorted(Comparator.comparing(OptionLegEntity::getLegOrder))
                .map(leg -> {
                    BookingType type;
                    try {
                        type = BookingType.valueOf(leg.getType());
                    } catch (Exception e) {
                        type = BookingType.FLIGHT;
                    }
                    return Booking.builder()
                            .id(leg.getId())
                            .type(type)
                            .provider(leg.getProvider())
                            .from(leg.getFromLocation())
                            .to(leg.getToLocation())
                            .startTime(leg.getStartTime())
                            .endTime(leg.getEndTime())
                            .cost(leg.getCost())
                            .status(BookingStatus.PROPOSED)
                            .source(leg.getSource() != null ? leg.getSource() : "INVENTORY")
                            .build();
                })
                .collect(Collectors.toList());

        return Option.builder()
                .id(entity.getId())
                .label(label)
                .score(entity.getScore())
                .totalCost(entity.getTotalCost())
                .arrivalTime(entity.getArrivalTime())
                .explanation(entity.getExplanation())
                .source(entity.getSource() != null ? entity.getSource() : "INVENTORY")
                .constraintsMet(met)
                .constraintsViolated(violated)
                .legs(legs)
                .build();
    }

    public RecoveryOptionEntity toOptionEntity(Option model, TripEntity trip) {
        if (model == null) return null;
        RecoveryOptionEntity entity = RecoveryOptionEntity.builder()
                .id(model.getId())
                .trip(trip)
                .label(model.getLabel() != null ? model.getLabel().name() : OptionLabel.SAFEST.name())
                .score(model.getScore() != null ? model.getScore() : 0.0)
                .totalCost(model.getTotalCost())
                .arrivalTime(model.getArrivalTime() != null ? model.getArrivalTime() : LocalDateTime.now())
                .explanation(model.getExplanation())
                .source(model.getSource() != null ? model.getSource() : "INVENTORY")
                .constraintsMet(serializeStringList(model.getConstraintsMet()))
                .constraintsViolated(serializeStringList(model.getConstraintsViolated()))
                .createdAt(LocalDateTime.now())
                .build();

        if (model.getLegs() != null) {
            List<OptionLegEntity> legs = new ArrayList<>();
            for (int i = 0; i < model.getLegs().size(); i++) {
                Booking leg = model.getLegs().get(i);
                legs.add(OptionLegEntity.builder()
                        .id(leg.getId() != null ? leg.getId() : "LEG-" + entity.getId() + "-" + (i + 1))
                        .recoveryOption(entity)
                        .type(leg.getType() != null ? leg.getType().name() : BookingType.FLIGHT.name())
                        .provider(leg.getProvider())
                        .fromLocation(leg.getFrom())
                        .toLocation(leg.getTo())
                        .startTime(leg.getStartTime())
                        .endTime(leg.getEndTime())
                        .cost(leg.getCost())
                        .source(leg.getSource() != null ? leg.getSource() : model.getSource())
                        .legOrder(i)
                        .build());
            }
            entity.setLegs(legs);
        }
        return entity;
    }

    public RecoveryPlan toRecoveryPlanModel(RecoveryPlanEntity entity) {
        if (entity == null) return null;
        CostBreakdown costBreakdown = null;
        if (entity.getCostBreakdownJson() != null) {
            try {
                costBreakdown = objectMapper.readValue(entity.getCostBreakdownJson(), CostBreakdown.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize costBreakdownJson: {}", e.getMessage());
            }
        }
        List<ItineraryDiffItem> diff = new ArrayList<>();
        if (entity.getItineraryDiffJson() != null) {
            try {
                diff = objectMapper.readValue(entity.getItineraryDiffJson(), new TypeReference<List<ItineraryDiffItem>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize itineraryDiffJson: {}", e.getMessage());
            }
        }
        List<ActionChecklistItem> checklist = entity.getChecklistItems().stream()
                .sorted(Comparator.comparing(PlanChecklistItemEntity::getItemOrder))
                .map(item -> ActionChecklistItem.builder()
                        .id(item.getId())
                        .text(item.getText())
                        .done(item.getDone())
                        .build())
                .collect(Collectors.toList());

        OptionLabel chosenOptionLabel = null;
        Booking newLeg = null;
        Booking replacementCab = null;

        if (entity.getTrip() != null) {
            if (entity.getOptionId() != null && entity.getTrip().getRecoveryOptions() != null) {
                for (RecoveryOptionEntity ro : entity.getTrip().getRecoveryOptions()) {
                    if (entity.getOptionId().equalsIgnoreCase(ro.getId())) {
                        try {
                            chosenOptionLabel = OptionLabel.valueOf(ro.getLabel());
                        } catch (Exception ignored) {}
                        break;
                    }
                }
            }
            if (entity.getTrip().getBookings() != null) {
                for (BookingEntity b : entity.getTrip().getBookings()) {
                    if (entity.getNewLegId() != null && entity.getNewLegId().equals(b.getId())) {
                        newLeg = toBookingModel(b);
                    }
                    if (entity.getReplacementCabId() != null && entity.getReplacementCabId().equals(b.getId())) {
                        replacementCab = toBookingModel(b);
                    }
                }
            }
        }

        return RecoveryPlan.builder()
                .id(entity.getId())
                .tripId(entity.getTrip() != null ? entity.getTrip().getId() : null)
                .chosenOptionId(entity.getOptionId())
                .chosenOptionLabel(chosenOptionLabel)
                .newLeg(newLeg)
                .replacementCab(replacementCab)
                .tradeOffExplanation(entity.getTradeOffExplanation())
                .hotelMessage(entity.getHotelMessage())
                .costBreakdown(costBreakdown)
                .itineraryDiff(diff)
                .checklist(checklist)
                .generatedAt(entity.getCreatedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public RecoveryPlanEntity toRecoveryPlanEntity(RecoveryPlan model, TripEntity trip) {
        if (model == null) return null;
        String costJson = null;
        if (model.getCostBreakdown() != null) {
            try {
                costJson = objectMapper.writeValueAsString(model.getCostBreakdown());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize costBreakdown: {}", e.getMessage());
            }
        }
        String diffJson = null;
        if (model.getItineraryDiff() != null) {
            try {
                diffJson = objectMapper.writeValueAsString(model.getItineraryDiff());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize itineraryDiff: {}", e.getMessage());
            }
        }
        BigDecimal netCost = model.getCostBreakdown() != null && model.getCostBreakdown().getNetTripCost() != null
                ? model.getCostBreakdown().getNetTripCost() : java.math.BigDecimal.ZERO;
        BigDecimal maxBudget = model.getCostBreakdown() != null && model.getCostBreakdown().getMaxBudget() != null
                ? model.getCostBreakdown().getMaxBudget() : java.math.BigDecimal.ZERO;
        BigDecimal remainingBudget = model.getCostBreakdown() != null && model.getCostBreakdown().getRemainingBudget() != null
                ? model.getCostBreakdown().getRemainingBudget() : java.math.BigDecimal.ZERO;

        RecoveryPlanEntity entity = RecoveryPlanEntity.builder()
                .id(model.getId())
                .trip(trip)
                .optionId(model.getChosenOptionId())
                .newLegId(model.getNewLeg() != null ? model.getNewLeg().getId() : null)
                .replacementCabId(model.getReplacementCab() != null ? model.getReplacementCab().getId() : null)
                .tradeOffExplanation(model.getTradeOffExplanation())
                .hotelMessage(model.getHotelMessage())
                .netTripCost(netCost)
                .maxBudget(maxBudget)
                .remainingBudget(remainingBudget)
                .costBreakdownJson(costJson)
                .itineraryDiffJson(diffJson)
                .createdAt(model.getCreatedAt() != null ? model.getCreatedAt() : LocalDateTime.now())
                .build();

        if (model.getChecklist() != null) {
            List<PlanChecklistItemEntity> items = new ArrayList<>();
            for (int i = 0; i < model.getChecklist().size(); i++) {
                ActionChecklistItem item = model.getChecklist().get(i);
                items.add(PlanChecklistItemEntity.builder()
                        .id(item.getId() != null ? item.getId() : "CHK-" + entity.getId() + "-" + (i + 1))
                        .recoveryPlan(entity)
                        .text(item.getText())
                        .done(item.isDone())
                        .itemOrder(i)
                        .build());
            }
            entity.setChecklistItems(items);
        }
        return entity;
    }

    private String serializeStringList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return String.join("||", list);
        }
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Arrays.stream(json.split("\\|\\|")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
    }
}
