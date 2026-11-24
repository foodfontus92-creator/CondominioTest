package com.accesolibre.mqtt;

import java.util.UUID;

public record MqttTelemetryMessageEvent(
        UUID condominiumId,
        UUID deviceId,
        String topic,
        String payloadJson
) {
}
