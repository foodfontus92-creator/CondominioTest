package com.accesolibre.dto.access;

import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AccessLogResponse(
        UUID id,
        UUID condominiumId,
        UUID deviceId,
        UUID unitId,
        UUID accessPassId,
        UUID triggeredByUserId,
        AccessMethod accessMethod,
        AccessResult result,
        Boolean offlineValidated,
        String qrPassIdRaw,
        Map<String, Object> details,
        Instant occurredAt,
        Instant syncedAt
) {
    public static AccessLogResponse from(AccessLog entity) {
        return new AccessLogResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getDeviceId(),
                entity.getUnitId(),
                entity.getAccessPassId(),
                entity.getTriggeredByUserId(),
                entity.getAccessMethod(),
                entity.getResult(),
                entity.getOfflineValidated(),
                entity.getQrPassIdRaw(),
                entity.getDetails(),
                entity.getOccurredAt(),
                entity.getSyncedAt()
        );
    }
}
