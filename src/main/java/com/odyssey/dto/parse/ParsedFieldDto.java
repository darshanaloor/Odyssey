package com.odyssey.dto.parse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedFieldDto<T> {
    private T value;
    private FieldConfidence confidence;
    private String suggestionLabel;

    public static <T> ParsedFieldDto<T> extracted(T val) {
        return ParsedFieldDto.<T>builder()
                .value(val)
                .confidence(FieldConfidence.EXTRACTED)
                .build();
    }

    public static <T> ParsedFieldDto<T> missing() {
        return ParsedFieldDto.<T>builder()
                .value(null)
                .confidence(FieldConfidence.MISSING)
                .build();
    }

    public static <T> ParsedFieldDto<T> suggested(T val) {
        return ParsedFieldDto.<T>builder()
                .value(val)
                .confidence(FieldConfidence.SUGGESTED)
                .suggestionLabel("Suggested, please check")
                .build();
    }
}
