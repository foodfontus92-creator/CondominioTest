package com.accesolibre.dto.qr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ValidateQrRequest(
        @NotBlank
        @Size(max = 119, message = "Compact QR payload must be shorter than 120 characters")
        String qrPayload,

        UUID deviceId,

        Boolean triggerUnlock
) {
}
