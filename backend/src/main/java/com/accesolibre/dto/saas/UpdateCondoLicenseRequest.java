package com.accesolibre.dto.saas;

import com.accesolibre.domain.enums.SubscriptionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCondoLicenseRequest(
        SubscriptionStatus subscriptionStatus,

        @Size(max = 64)
        String planName,

        Instant licenseExpiresAt,

        Boolean unlimitedLicense,

        @Min(1) @Max(50000)
        Integer maxUnits,

        String quickAction
) {
}
