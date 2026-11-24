package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotel_room_availabilities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelRoomAvailabilityEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "room_type_id", nullable = false, length = 64)
    private String roomTypeId;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(name = "rooms_total", nullable = false)
    private Integer roomsTotal;

    @Column(name = "rooms_booked", nullable = false)
    @Builder.Default
    private Integer roomsBooked = 0;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
