package com.odyssey.dto.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderBookingDto {
    private String bookingReference;
    private String passengerNames;
    private String seatNumbers;
    private String status;
    private LocalDateTime cancellationTimestamp;
    private String cancelledBy;
}
