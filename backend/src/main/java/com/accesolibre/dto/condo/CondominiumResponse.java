package com.accesolibre.dto.condo;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.enums.CommunityType;

import java.time.Instant;
import java.util.UUID;

public record CondominiumResponse(
        UUID id,
        String code,
        String name,
        CommunityType communityType,
        String address,
        String timezone,
        Integer hmacKeyVersion,
        Double latitude,
        Double longitude,
        Integer geofenceRadiusMeters,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CondominiumResponse from(Condominium entity) {
        return new CondominiumResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCommunityType() != null ? entity.getCommunityType() : CommunityType.CONDOMINIUM,
                entity.getAddress(),
                entity.getTimezone(),
                entity.getHmacKeyVersion(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getGeofenceRadiusMeters(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
