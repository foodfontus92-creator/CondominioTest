package com.accesolibre.dto.unit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateUnitActivationCodeRequest(
        @Min(1) @Max(10)
        Integer maxUses,

        @Min(1) @Max(720)
        Integer expiresInHours,

        Boolean revokePrevious
) {
}
