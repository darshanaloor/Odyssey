package com.odyssey.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Separate block representing the last known status of a booking.
 * Explicitly distinguished from the stored booking details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastKnownStatusDto {
    private String state;
    private Integer delayMinutes;
    private LocalDateTime checkedAt;
    private String source; // LIVE, SIMULATED, UNKNOWN
}

