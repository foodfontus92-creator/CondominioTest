package com.accesolibre.dto.device;

import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.enums.DeviceStatus;
import com.accesolibre.domain.enums.DeviceType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        UUID condominiumId,
        String hardwareUid,
        String name,
        DeviceType deviceType,
        String locationDescription,
        String mqttUsername,
        String publicQrToken,
        String firmwareVersion,
        String ipAddress,
        DeviceStatus status,
        Integer relayPulseMs,
        Integer hmacKeyVersionSynced,
        Instant lastSeenAt,
        Map<String, Object> lastTelemetry,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeviceResponse from(IotDevice entity) {
        return new DeviceResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getHardwareUid(),
                entity.getName(),
                entity.getDeviceType(),
                entity.getLocationDescription(),
                entity.getMqttUsername(),
                entity.getPublicQrToken(),
                entity.getFirmwareVersion(),
                entity.getIpAddress() != null ? entity.getIpAddress().getHostAddress() : null,
                entity.getStatus(),
                entity.getRelayPulseMs(),
                entity.getHmacKeyVersionSynced(),
                entity.getLastSeenAt(),
                entity.getLastTelemetry(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
