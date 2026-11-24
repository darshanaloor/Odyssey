package com.odyssey.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CannotHonourRequest {

    @NotBlank(message = "Reason is required")
    @Size(min = 5, max = 200, message = "Reason must be between 5 and 200 characters")
    private String reason;
}
