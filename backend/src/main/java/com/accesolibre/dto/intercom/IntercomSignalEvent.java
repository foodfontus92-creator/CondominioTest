package com.accesolibre.dto.intercom;

import com.accesolibre.domain.enums.IntercomCallStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntercomSignalEvent(
        String eventType,
        UUID callId,
        UUID condominiumId,
        UUID deviceId,
        String deviceName,
        UUID unitId,
        String unitNumber,
        IntercomCallStatus status,
        String signalingSessionId,
        String visitorName,
        String visitorNote,
        Boolean doorUnlocked,
        UUID mqttCommandId,
        Map<String, Object> signalPayload,
        Instant timestamp
) {
}
