package com.accesolibre.dto.qr;

import com.accesolibre.domain.entity.AccessPass;
import com.accesolibre.domain.enums.PassStatus;
import com.accesolibre.domain.enums.PassType;

import java.time.Instant;
import java.util.UUID;

public record AccessPassResponse(
        UUID id,
        UUID condominiumId,
        UUID unitId,
        UUID createdByUserId,
        String visitorName,
        String visitorDocument,
        PassType passType,
        String qrPayload,
        Integer hmacKeyVersion,
        Instant validFrom,
        Instant expiresAt,
        Integer maxUses,
        Integer currentUses,
        PassStatus status,
        Instant revokedAt,
        Instant createdAt
) {
    public static AccessPassResponse from(AccessPass entity) {
        return new AccessPassResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getUnitId(),
                entity.getCreatedByUserId(),
                entity.getVisitorName(),
                entity.getVisitorDocument(),
                entity.getPassType(),
                entity.getQrPayload(),
                entity.getHmacKeyVersion(),
                entity.getValidFrom(),
                entity.getExpiresAt(),
                entity.getMaxUses(),
                entity.getCurrentUses(),
                entity.getStatus(),
                entity.getRevokedAt(),
                entity.getCreatedAt()
        );
    }
}
