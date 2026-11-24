package com.accesolibre.mqtt;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MqttTopicBuilderTest {

    @Test
    void shouldBuildAndParseCanonicalTopics() {
        UUID condoId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        String cmdTopic = MqttTopicBuilder.commandTopic(condoId, deviceId);
        String telTopic = MqttTopicBuilder.telemetryTopic(condoId, deviceId);

        assertThat(cmdTopic).isEqualTo("condo/" + condoId + "/device/" + deviceId + "/command");
        assertThat(telTopic).isEqualTo("condo/" + condoId + "/device/" + deviceId + "/telemetry");

        Optional<MqttTopicBuilder.ParsedTopic> parsed = MqttTopicBuilder.parseTelemetryTopic(telTopic);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().condominiumId()).isEqualTo(condoId);
        assertThat(parsed.get().deviceId()).isEqualTo(deviceId);

        assertThat(MqttTopicBuilder.parseTelemetryTopic(cmdTopic)).isEmpty();
    }
}
