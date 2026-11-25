package com.accesolibre.dto.unit;

import java.util.UUID;

public record RevokeUnitResidentsResponse(
        UUID unitId,
        String unitNumber,
        int deactivatedResidents,
        int invalidatedCodes
) {
}
