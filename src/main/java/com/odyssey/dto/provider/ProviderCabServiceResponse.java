package com.odyssey.dto.provider;

import com.odyssey.model.enums.VehicleCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCabServiceResponse {
    private String id;
    private String providerId;
    private String providerName;
    private String city;
    private VehicleCategory vehicleCategory;
    private BigDecimal baseFare;
    private BigDecimal perKmRate;
    private Integer seats;
    private String operatingHours;
    private String vehicleNumber;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
