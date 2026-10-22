package com.odyssey.dto.request;

import com.odyssey.model.Booking;
import com.odyssey.model.Constraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateTripRequest {

    @NotBlank(message = "Trip title cannot be blank")
    private String title;

    private String description;
    private String travellerName;
    private String travellerPhone;
    private String travellerEmail;

    @Builder.Default
    private Boolean needsDestinationCab = false;

    private String destinationCabDropoff;

    @Builder.Default
    private Boolean needsHotel = false;

    @Builder.Default
    private Boolean notifyEmail = true;

    @Builder.Default
    private Boolean notifySms = true;

    @Builder.Default
    private Boolean notifyWhatsapp = false;

    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @NotNull(message = "Constraints are required")
    @Valid
    private Constraints constraints;

    @Builder.Default
    private List<com.odyssey.model.HardCommitment> hardCommitments = new ArrayList<>();
}
