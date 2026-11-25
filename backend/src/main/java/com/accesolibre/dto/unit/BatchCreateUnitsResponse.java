package com.accesolibre.dto.unit;

import java.util.List;

public record BatchCreateUnitsResponse(
        String blockName,
        int createdCount,
        List<UnitResponse> units
) {
}
