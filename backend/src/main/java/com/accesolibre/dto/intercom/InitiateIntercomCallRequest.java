package com.accesolibre.dto.intercom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InitiateIntercomCallRequest(
        @NotBlank(message = "publicQrToken is required")
        String publicQrToken,

        UUID unitId,

        @Size(max = 32)
        String intercomExtension,

        @Size(max = 150)
        String visitorName,

        @Size(max = 255)
        String visitorNote
) {
}
