package com.accesolibre.dto.device;

import com.accesolibre.domain.enums.DeviceStatus;
import com.accesolibre.domain.enums.DeviceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateDeviceRequest(
        @Size(max = 120)
        String name,

        DeviceType deviceType,

        @Size(max = 255)
        String locationDescription,

        @Min(200) @Max(30000)
        Integer relayPulseMs,

        DeviceStatus status,

        Boolean active
) {
}
