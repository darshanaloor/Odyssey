package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_holds", uniqueConstraints = {
    @UniqueConstraint(name = "uq_departure_seat", columnNames = {"departure_id", "seat_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_id", nullable = false)
    private ServiceDepartureEntity departure;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "CONFIRMED"; // HELD, CONFIRMED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
