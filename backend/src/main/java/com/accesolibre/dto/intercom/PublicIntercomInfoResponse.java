package com.accesolibre.dto.intercom;

import java.util.List;
import java.util.UUID;

public record PublicIntercomInfoResponse(
        UUID condominiumId,
        String condominiumName,
        UUID deviceId,
        String deviceName,
        String locationDescription,
        List<CallableUnit> units
) {
    public record CallableUnit(
            UUID unitId,
            String unitNumber,
            String towerOrBlock,
            String floor,
            String intercomExtension
    ) {
    }
}
