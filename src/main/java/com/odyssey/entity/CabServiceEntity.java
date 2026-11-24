package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "cab_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabServiceEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "city", nullable = false, length = 50)
    private String city;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKm;

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "SEED";

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "vehicle_category", length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private com.odyssey.model.enums.VehicleCategory vehicleCategory = com.odyssey.model.enums.VehicleCategory.SEDAN;

    @Column(name = "seats")
    @Builder.Default
    private Integer seats = 4;

    @Column(name = "operating_hours", length = 100)
    @Builder.Default
    private String operatingHours = "06:00 - 23:00";

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
