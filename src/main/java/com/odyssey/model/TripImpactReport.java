package com.odyssey.model;

import com.odyssey.model.enums.DisruptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripImpactReport {
    private String tripId;
    private String disruptionId;
    private DisruptionType disruptionType;
    private String rootBookingId;
    private Integer delayMinutes;
    private String disruptionReason;
    private String summary;

    @Builder.Default
    private List<String> hardCommitmentsBreached = new ArrayList<>();

    // "PENDING_RECOVERY" until a recovery plan is chosen, then "true" or "false"
    private String mustArriveByBreached;

    @Builder.Default
    private List<BookingImpact> impactedBookings = new ArrayList<>();

    private NotificationMessage notification;

    @Builder.Default
    private LocalDateTime analyzedAt = LocalDateTime.now();
}
