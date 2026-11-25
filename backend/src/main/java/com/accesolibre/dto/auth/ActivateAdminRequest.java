package com.accesolibre.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAdminRequest(
        @NotBlank(message = "El código de activación es obligatorio")
        @Size(max = 16)
        String code,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe ser válido")
        @Size(max = 255)
        String email,

        @Size(max = 32)
        String phone,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 128)
        String password
) {
}
