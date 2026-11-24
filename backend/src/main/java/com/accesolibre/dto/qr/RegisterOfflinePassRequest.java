package com.accesolibre.dto.qr;

import com.accesolibre.domain.enums.PassType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOfflinePassRequest(
        @NotBlank
        @Size(max = 119, message = "Compact QR payload must be shorter than 120 characters")
        String qrPayload,

        @NotBlank
        @Size(max = 150)
        String visitorName,

        @Size(max = 64)
        String visitorDocument,

        PassType passType,

        @Min(1) @Max(100)
        Integer maxUses
) {
}
