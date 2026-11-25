package com.accesolibre.dto.setup;

import com.accesolibre.domain.enums.CommunityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupInitializeRequest(
        @NotBlank(message = "condoCode is required")
        @Size(min = 2, max = 32)
        @Pattern(regexp = "^[a-z0-9\\-_]+$", message = "condoCode must be lowercase alphanumeric, hyphen or underscore")
        String condoCode,

        @NotBlank(message = "condoName is required")
        @Size(max = 150)
        String condoName,

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

        @NotBlank(message = "adminFullName is required")
        @Size(max = 150)
        String adminFullName,

        @NotBlank(message = "adminEmail is required")
        @Email(message = "adminEmail must be valid")
        @Size(max = 255)
        String adminEmail,

        @NotBlank(message = "adminPassword is required")
        @Size(min = 8, max = 128)
        String adminPassword,

        CommunityType communityType
) {
}
