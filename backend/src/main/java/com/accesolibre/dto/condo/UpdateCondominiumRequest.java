package com.accesolibre.dto.condo;

import com.accesolibre.domain.enums.CommunityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateCondominiumRequest(
        @Size(max = 150)
        String name,

        @Size(max = 255)
        String address,

        @Size(max = 64)
        String timezone,

        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        @Min(10) @Max(5000)
        Integer geofenceRadiusMeters,

        Boolean active,

        CommunityType communityType
) {
}
