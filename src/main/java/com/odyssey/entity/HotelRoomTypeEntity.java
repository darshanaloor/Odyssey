package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotel_room_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelRoomTypeEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "max_guests", nullable = false)
    private Integer maxGuests;

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "rooms_total", nullable = false)
    private Integer roomsTotal;

    @Column(name = "amenities", length = 200)
    private String amenities;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
