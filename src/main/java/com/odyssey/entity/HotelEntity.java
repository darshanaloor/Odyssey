package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hotels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "city", nullable = false, length = 50)
    private String city;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "price_per_night", precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "rooms_total")
    private Integer roomsTotal;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "SEED";

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "check_in_time", length = 10)
    @Builder.Default
    private String checkInTime = "14:00";

    @Column(name = "check_out_time", length = 10)
    @Builder.Default
    private String checkOutTime = "11:00";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
