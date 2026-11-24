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
public class ProviderRoomTypeResponse {
    private String id;
    private String hotelId;
    private String name;
    private Integer maxGuests;
    private BigDecimal pricePerNight;
    private Integer roomsTotal;
    private String amenities;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
