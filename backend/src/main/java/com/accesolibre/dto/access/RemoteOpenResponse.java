package com.accesolibre.dto.access;

import com.accesolibre.domain.enums.AccessResult;

import java.time.Instant;
import java.util.UUID;

public record RemoteOpenResponse(
        boolean granted,
        AccessResult result,
        UUID accessLogId,
        UUID mqttCommandId,
        String mqttTopic,
        UUID deviceId,
        String deviceName,
        double distanceMeters,
        int allowedRadiusMeters,
        int pulseMs,
        Instant occurredAt
) {
}
