package com.odyssey.dto.provider;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderActionRequest {
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
