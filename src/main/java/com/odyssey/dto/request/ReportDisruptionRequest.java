package com.odyssey.dto.request;

import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.DisruptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDisruptionRequest {

    @NotNull(message = "Disruption type is required (FLIGHT_DELAY, CANCELLATION, HOTEL_OVERBOOKED)")
    private DisruptionType type;

    @NotBlank(message = "Booking ID is required")
    private String bookingId;

    private Integer delayMinutes;

    @NotBlank(message = "Reason for disruption is required")
    private String reason;

    private java.time.LocalDateTime disruptionTime;

    @Builder.Default
    private AuditActor actor = AuditActor.SYSTEM;

    @Builder.Default
    private String source = "MANUAL";
}
