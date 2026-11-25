package com.accesolibre.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateUnitRequest(
        @NotBlank(message = "activationCode is required")
        @Size(max = 80)
        String activationCode,

        @NotBlank(message = "fullName is required")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Size(max = 255)
        String email,

        @Size(max = 32)
        String phone,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 128)
        String password
) {
}
