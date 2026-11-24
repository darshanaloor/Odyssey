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
public class CabQuoteResponse {
    private String id;
    private String provider;
    private String city;
    private BigDecimal baseFare;
    private BigDecimal perKm;
    private double estimatedDistanceKm;
    private BigDecimal estimatedFare;
    private boolean estimated;
    private String source; // SEED, GENERATED, or PROVIDER
    private String listingLabel; // Generated listing, Seeded listing, or Provider-listed
    private String disclaimer;
    private String vehicleCategory;
    private Integer seats;
    private String operatingHours;
}
