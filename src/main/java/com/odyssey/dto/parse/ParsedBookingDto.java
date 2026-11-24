package com.odyssey.dto.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.odyssey.model.enums.BookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedBookingDto {
    private String id;
    private ParsedFieldDto<BookingType> type;
    private ParsedFieldDto<String> provider;
    private ParsedFieldDto<String> vehicleNumber;

    @JsonProperty("from")
    private ParsedFieldDto<String> from;

    @JsonProperty("to")
    private ParsedFieldDto<String> to;

    private ParsedFieldDto<LocalDateTime> startTime;
    private ParsedFieldDto<LocalDateTime> endTime;
    private ParsedFieldDto<String> bookingReference;
    private ParsedFieldDto<String> seatNumbers;
    private ParsedFieldDto<String> passengerNames;
    private ParsedFieldDto<BigDecimal> cost;

    private boolean duplicateWarning;
    private String duplicateReason;
}
