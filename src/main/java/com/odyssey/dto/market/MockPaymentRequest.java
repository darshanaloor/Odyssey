package com.odyssey.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MockPaymentRequest {

    private Object bookingDraft;

    @Builder.Default
    private String paymentMethod = "CARD";

    // CARD fields (used when paymentMethod == "CARD")
    private String cardHolder;

    @ToString.Exclude
    private String cardNumber;

    @ToString.Exclude
    private String expiry;

    @ToString.Exclude
    private String cvv;

    // UPI fields (used when paymentMethod == "UPI")
    @ToString.Exclude
    private String upiId;

    // NETBANKING fields (used when paymentMethod == "NETBANKING")
    @ToString.Exclude
    private String bankName;
}
