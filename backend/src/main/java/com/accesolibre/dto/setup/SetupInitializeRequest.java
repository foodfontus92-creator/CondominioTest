package com.accesolibre.dto.setup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupInitializeRequest(
        @NotBlank(message = "condoCode is required")
        @Size(min = 2, max = 32)
        @Pattern(regexp = "^[a-z0-9\\-_]+$", message = "condoCode must be lowercase alphanumeric, hyphen or underscore")
        String condoCode,

        @NotBlank(message = "condoName is required")
        @Size(max = 150)
        String condoName,

        @Size(max = 255)
        String address,

        @Size(max = 64)
        String timezone,

        @NotBlank(message = "adminFullName is required")
        @Size(max = 150)
        String adminFullName,

        @NotBlank(message = "adminEmail is required")
        @Email(message = "adminEmail must be valid")
        @Size(max = 255)
        String adminEmail,

        @NotBlank(message = "adminPassword is required")
        @Size(min = 8, max = 128)
        String adminPassword
) {
}
