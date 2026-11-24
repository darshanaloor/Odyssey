package com.odyssey.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "operators")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatorEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "mode", nullable = false, length = 50)
    private String mode;

    @Column(name = "contact", length = 100)
    private String contact;
}
