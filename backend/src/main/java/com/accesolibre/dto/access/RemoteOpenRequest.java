package com.accesolibre.dto.access;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RemoteOpenRequest(
        @NotNull(message = "deviceId is required")
        UUID deviceId,

        @NotNull(message = "latitude is required for geofencing validation")
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90")
        Double latitude,

        @NotNull(message = "longitude is required for geofencing validation")
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180")
        Double longitude,

        @Min(200) @Max(30000)
        Integer pulseMs
) {
}
