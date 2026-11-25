package com.accesolibre.dto.unit;

import jakarta.validation.constraints.Size;

public record UpdateUnitRequest(
        @Size(max = 32)
        String unitNumber,

        @Size(max = 64)
        String blockName,

        @Size(max = 64)
        String towerOrBlock,

        @Size(max = 16)
        String floor,

        @Size(max = 32)
        String intercomExtension,

        Boolean active,

        Boolean blocked,

        @Size(max = 255)
        String blockReason
) {
}
