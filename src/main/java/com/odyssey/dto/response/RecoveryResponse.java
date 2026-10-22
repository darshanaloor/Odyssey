package com.odyssey.dto.response;

import com.odyssey.model.Option;
import com.odyssey.model.RejectedOption;
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
public class RecoveryResponse {
    private String tripId;
    private String disruptedBookingId;
    private boolean noPerfectOption;
    private String explanation;
    private String relaxationApplied;

    @Builder.Default
    private List<Option> options = new ArrayList<>();

    @Builder.Default
    private List<RejectedOption> rejectedOptions = new ArrayList<>();
}
