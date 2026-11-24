package com.accesolibre.dto.qr;

import com.accesolibre.domain.enums.PassType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateAccessPassRequest(
        UUID unitId,

        @NotBlank
        @Size(max = 150)
        String visitorName,

        @Size(max = 64)
        String visitorDocument,

        PassType passType,

        Instant validFrom,

        Instant expiresAt,

        @Min(1) @Max(100)
        Integer maxUses
) {
}
