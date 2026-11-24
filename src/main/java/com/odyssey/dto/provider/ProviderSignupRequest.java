package com.odyssey.dto.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.odyssey.model.enums.BusinessType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderSignupRequest {

    // User Account Details
    @NotBlank(message = "Contact name is required")
    @Size(min = 2, max = 100, message = "Contact name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Account email is required")
    @Email(message = "Invalid account email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one digit")
    private String password;

    // Business Details
    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 150, message = "Business name must be between 2 and 150 characters")
    private String businessName;

    @NotNull(message = "Business type is required (TRANSPORT, HOTEL, BOTH)")
    private BusinessType businessType;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    @Email(message = "Invalid contact email format")
    private String contactEmail;

    @NotBlank(message = "City is required")
    private String city;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
