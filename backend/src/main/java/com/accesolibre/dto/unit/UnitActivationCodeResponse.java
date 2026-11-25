package com.accesolibre.dto.unit;

import java.time.Instant;
import java.util.UUID;

public record UnitActivationCodeResponse(
        UUID id,
        UUID condominiumId,
        String condominiumName,
        UUID unitId,
        String unitNumber,
        String code,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        boolean active,
        Instant createdAt
) {
}
