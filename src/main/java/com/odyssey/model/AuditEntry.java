package com.odyssey.model;

import com.odyssey.model.enums.AuditActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    private String id;
    private LocalDateTime timestamp;
    private AuditActor actor;
    private String action;
    private String details;
}
