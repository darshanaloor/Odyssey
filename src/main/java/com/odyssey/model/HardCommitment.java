package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardCommitment {
    private String id;
    private String name;
    private LocalDateTime commitmentTime;
    private String location;
}
