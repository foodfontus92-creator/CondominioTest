package com.accesolibre.dto.unit;

import com.accesolibre.domain.entity.Unit;

import java.time.Instant;
import java.util.UUID;

public record UnitResponse(
        UUID id,
        UUID condominiumId,
        String unitNumber,
        String blockName,
        String towerOrBlock,
        String floor,
        String intercomExtension,
        Boolean active,
        Boolean blocked,
        String blockReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static UnitResponse from(Unit entity) {
        String effectiveBlock = entity.effectiveBlockName();
        return new UnitResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getUnitNumber(),
                effectiveBlock,
                effectiveBlock,
                entity.getFloor(),
                entity.getIntercomExtension(),
                entity.getActive(),
                entity.getBlocked(),
                entity.getBlockReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
