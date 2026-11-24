package com.odyssey.entity;

import com.odyssey.model.enums.BookingType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_vehicles", uniqueConstraints = {
    @UniqueConstraint(name = "uq_provider_vehicle_number", columnNames = {"provider_id", "vehicle_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVehicleEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private BookingType mode;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(name = "vehicle_type", nullable = false, length = 100)
    private String vehicleType;

    @Column(name = "seats_total", nullable = false)
    private Integer seatsTotal;

    @Column(name = "amenities", length = 200)
    private String amenities;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }
}
