package com.odyssey.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionChecklistItem {
    private String id;
    private String text;
    @Builder.Default
    private boolean done = false;
}
