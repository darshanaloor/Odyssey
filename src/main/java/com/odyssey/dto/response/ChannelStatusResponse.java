package com.odyssey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelStatusResponse {
    private String email;    // "Active" | "Simulated"
    private String sms;      // "Active" | "Simulated"
    private String whatsapp; // "Off" | "Active" | "Simulated"
}
