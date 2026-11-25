package com.accesolibre.dto.saas;

import java.util.List;

public record SaasOverviewResponse(
        long totalCondos,
        long activeCondos,
        long expiringSoonCondos,
        long expiredOrSuspendedCondos,
        long totalUnits,
        long totalResidents,
        long totalUsers,
        List<SaasCondoOverviewItem> condominiums
) {
}
