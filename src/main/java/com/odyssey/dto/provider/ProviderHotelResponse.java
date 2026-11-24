package com.odyssey.dto.provider;

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
public class ProviderHotelResponse {
    private String id;
    private String providerId;
    private String name;
    private String city;
    private String address;
    private String description;
    private String contactEmail;
    private String checkInTime;
    private String checkOutTime;
    private BigDecimal rating;
    private Boolean active;
    private long roomTypesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
