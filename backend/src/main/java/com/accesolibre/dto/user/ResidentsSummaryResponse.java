package com.accesolibre.dto.user;

public record ResidentsSummaryResponse(
        long totalResidents,
        long activeResidents,
        long blockedResidents,
        long inactiveResidents,
        long blockedOrInactiveResidents,
        long occupiedUnits,
        long totalUnits
) {
}
