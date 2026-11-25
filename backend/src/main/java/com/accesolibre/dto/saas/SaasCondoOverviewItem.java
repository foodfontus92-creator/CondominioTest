package com.accesolibre.dto.saas;

import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SaasCondoOverviewItem(
        UUID id,
        String code,
        String name,
        CommunityType communityType,
        String address,
        SubscriptionStatus subscriptionStatus,
        SubscriptionStatus effectiveStatus,
        String planName,
        Instant licenseExpiresAt,
        Long daysRemaining,
        Integer maxUnits,
        long unitCount,
        long residentCount,
        long adminCount,
        boolean active,
        boolean licenseActive,
        Instant createdAt
) {
}
