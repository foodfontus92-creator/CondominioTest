package com.accesolibre.dto.device;

import com.accesolibre.domain.enums.DeviceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank
        @Size(max = 64)
        String hardwareUid,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        DeviceType deviceType,

        @Size(max = 255)
        String locationDescription,

        @NotBlank
        @Size(max = 128)
        String mqttUsername,

        @Min(200) @Max(30000)
        Integer relayPulseMs
) {
}
