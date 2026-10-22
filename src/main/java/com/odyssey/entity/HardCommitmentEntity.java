package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hard_commitments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardCommitmentEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "commitment_time", nullable = false)
    private LocalDateTime commitmentTime;

    @Column(name = "location")
    private String location;
}
