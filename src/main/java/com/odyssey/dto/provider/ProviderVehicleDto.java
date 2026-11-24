package com.odyssey.dto.provider;

import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVehicleDto {
    private String id;
    private String providerId;
    private BookingType mode;
    private String vehicleNumber;
    private String vehicleType;
    private Integer seatsTotal;
    private String amenities;
    private Boolean active;
    private LocalDateTime createdAt;
    private Integer maxBookedSeatsInFuture;
}
