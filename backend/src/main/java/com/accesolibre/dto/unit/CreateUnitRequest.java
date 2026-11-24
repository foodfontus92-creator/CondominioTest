package com.accesolibre.dto.unit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUnitRequest(
        @NotBlank
        @Size(max = 32)
        String unitNumber,

        @Size(max = 64)
        String towerOrBlock,

        @Size(max = 16)
        String floor,

        @Size(max = 32)
        String intercomExtension
) {
}
