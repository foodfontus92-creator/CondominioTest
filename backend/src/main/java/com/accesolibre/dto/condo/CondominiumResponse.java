package com.accesolibre.dto.condo;

import com.accesolibre.domain.entity.Condominium;

import java.time.Instant;
import java.util.UUID;

public record CondominiumResponse(
        UUID id,
        String code,
        String name,
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
