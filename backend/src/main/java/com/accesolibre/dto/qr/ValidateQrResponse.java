package com.accesolibre.dto.qr;

import com.accesolibre.domain.enums.AccessResult;

import java.time.Instant;
import java.util.UUID;

public record ValidateQrResponse(
        boolean valid,
        AccessResult result,
        String message,
        UUID passId,
        UUID condominiumId,
        UUID unitId,
        String visitorName,
        Instant expiresAt,
        Integer currentUses,
        Integer maxUses,
        UUID mqttCommandId
) {
}
