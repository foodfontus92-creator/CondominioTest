package com.accesolibre.mqtt;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MqttTopicBuilder {

    public static final String TELEMETRY_WILDCARD = "condo/+/device/+/telemetry";

    private static final Pattern TELEMETRY_PATTERN = Pattern.compile(
            "^condo/([0-9a-fA-F\\-]{36})/device/([0-9a-fA-F\\-]{36})/telemetry$"
    );

    private MqttTopicBuilder() {
    }

    public static String commandTopic(UUID condominiumId, UUID deviceId) {
        return "condo/" + condominiumId + "/device/" + deviceId + "/command";
    }

    public static String telemetryTopic(UUID condominiumId, UUID deviceId) {
        return "condo/" + condominiumId + "/device/" + deviceId + "/telemetry";
    }

    public static Optional<ParsedTopic> parseTelemetryTopic(String topic) {
        if (topic == null) {
            return Optional.empty();
        }
        Matcher matcher = TELEMETRY_PATTERN.matcher(topic);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            UUID condoId = UUID.fromString(matcher.group(1));
            UUID deviceId = UUID.fromString(matcher.group(2));
            return Optional.of(new ParsedTopic(condoId, deviceId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record ParsedTopic(UUID condominiumId, UUID deviceId) {
    }
}
