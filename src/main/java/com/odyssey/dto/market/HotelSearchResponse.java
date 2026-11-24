package com.odyssey.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchResponse {
    private String id;
    private String name;
    private String city;
    private String address;
    private BigDecimal pricePerNight;
    private BigDecimal rating;
    private int roomsTotal;
    private String contactEmail;
    private int nights;
    private int rooms;
    private BigDecimal estimatedTotal;
    private String source; // SEED, GENERATED, or PROVIDER
    private String listingLabel; // Generated listing, Seeded listing, or Provider-listed
    private String disclaimer;
    private String roomTypeId;
    private String roomTypeName;
    private Integer maxGuests;
    private Integer roomsAvailable;
    private String amenities;
    private String providerName;
}
