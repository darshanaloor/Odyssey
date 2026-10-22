package com.odyssey.dto.response;

import com.odyssey.model.Booking;
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
public class ParseResult {

    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @Builder.Default
    private List<String> unparsedLines = new ArrayList<>();
}
