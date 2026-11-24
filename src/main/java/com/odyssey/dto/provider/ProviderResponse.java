package com.odyssey.dto.provider;

import com.odyssey.model.enums.BusinessType;
import com.odyssey.model.enums.ProviderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {
    private String id;
    private String userId;
    private String businessName;
    private BusinessType businessType;
    private String contactEmail;
    private String contactPhone;
    private String city;
    private String description;
    private ProviderStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String statusReason;
    private long vehicleCount;
    private long hotelCount;
    private long cabServiceCount;
    private long bookingCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
