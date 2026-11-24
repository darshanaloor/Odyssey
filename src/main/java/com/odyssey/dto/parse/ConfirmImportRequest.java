package com.odyssey.dto.parse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmImportRequest {

    @NotEmpty(message = "At least one booking is required to import")
    @Valid
    @Builder.Default
    private List<ImportBookingDraftDto> bookings = new ArrayList<>();
}
