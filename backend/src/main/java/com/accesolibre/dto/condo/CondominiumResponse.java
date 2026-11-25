package com.accesolibre.dto.condo;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.SubscriptionStatus;

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
        SubscriptionStatus subscriptionStatus,
        SubscriptionStatus effectiveSubscriptionStatus,
        String planName,
        Instant licenseExpiresAt,
        Integer maxUnits,
        Boolean licenseActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static CondominiumResponse from(Condominium entity) {
        Instant now = Instant.now();
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
                entity.getSubscriptionStatus() != null ? entity.getSubscriptionStatus() : SubscriptionStatus.ACTIVE,
                entity.effectiveSubscriptionStatus(now),
                entity.getPlanName() != null ? entity.getPlanName() : "Mensual",
                entity.getLicenseExpiresAt(),
                entity.getMaxUnits() != null ? entity.getMaxUnits() : 200,
                entity.isLicenseActiveAt(now),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
