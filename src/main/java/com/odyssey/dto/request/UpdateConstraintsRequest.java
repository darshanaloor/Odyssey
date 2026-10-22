package com.odyssey.dto.request;

import com.odyssey.model.Constraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConstraintsRequest {

    @NotNull(message = "Constraints cannot be null")
    @Valid
    private Constraints constraints;
}
