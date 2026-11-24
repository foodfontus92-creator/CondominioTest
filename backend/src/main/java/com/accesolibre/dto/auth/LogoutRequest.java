package com.accesolibre.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
