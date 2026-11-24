package com.accesolibre.mqtt;

import com.accesolibre.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MqttGatewayService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayService.class);

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private MqttClient mqttClient;
    private MqttConnectOptions connectOptions;

    public MqttGatewayService(
            AppProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT integration is disabled via configuration");
            return;
        }
        try {
            String clientId = properties.getMqtt().getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
            this.mqttClient = new MqttClient(
                    properties.getMqtt().getBrokerUrl(),
                    clientId,
                    new MemoryPersistence()
            );
            this.mqttClient.setCallback(this);

            this.connectOptions = new MqttConnectOptions();
            this.connectOptions.setAutomaticReconnect(true);
            this.connectOptions.setCleanSession(true);
            this.connectOptions.setConnectionTimeout(properties.getMqtt().getConnectionTimeoutSeconds());
            this.connectOptions.setKeepAliveInterval(properties.getMqtt().getKeepAliveIntervalSeconds());

            if (properties.getMqtt().getUsername() != null && !properties.getMqtt().getUsername().isBlank()) {
                this.connectOptions.setUserName(properties.getMqtt().getUsername());
            }
            if (properties.getMqtt().getPassword() != null && !properties.getMqtt().getPassword().isBlank()) {
                this.connectOptions.setPassword(properties.getMqtt().getPassword().toCharArray());
            }

            connectIfNecessary();
        } catch (MqttException ex) {
            log.warn("Could not connect to local MQTT broker {} at startup (will retry on demand): {}",
                    properties.getMqtt().getBrokerUrl(), ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
            } catch (MqttException ex) {
                log.debug("Error closing MQTT client: {}", ex.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public UUID publishUnlockCommand(
            UUID condominiumId,
            UUID deviceId,
            int pulseMs,
            String reason,
            UUID triggeredByUserId,
            UUID unitId,
            UUID intercomCallId
    ) {
        UUID commandId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pulse_ms", pulseMs);
        payload.put("reason", reason);
        payload.put("triggered_by_user_id", triggeredByUserId != null ? triggeredByUserId.toString() : null);
        payload.put("unit_id", unitId != null ? unitId.toString() : null);
        payload.put("intercom_call_id", intercomCallId != null ? intercomCallId.toString() : null);

        publishCommandEnvelope(condominiumId, deviceId, commandId, "UNLOCK", payload);
        return commandId;
    }

    public UUID publishSyncConfigCommand(
            UUID condominiumId,
            UUID deviceId,
            String condoIdB64,
            String hmacSecretHex,
            int hmacKeyVersion,
            int relayPulseMs
    ) {
        UUID commandId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("condo_id_b64", condoIdB64);
        payload.put("hmac_secret_hex", hmacSecretHex);
        payload.put("hmac_key_version", hmacKeyVersion);
        payload.put("server_unix_time", Instant.now().getEpochSecond());
        payload.put("relay_pulse_ms", relayPulseMs);

        publishCommandEnvelope(condominiumId, deviceId, commandId, "SYNC_CONFIG", payload);
        return commandId;
    }

    public UUID publishRevokePassCommand(
            UUID condominiumId,
            UUID deviceId,
            String passIdB64,
            long expiresAtEpochSec
    ) {
        UUID commandId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pass_id_b64", passIdB64);
        payload.put("expires_at", expiresAtEpochSec);

        publishCommandEnvelope(condominiumId, deviceId, commandId, "REVOKE_PASS", payload);
        return commandId;
    }

    public UUID publishIntercomActionCommand(
            UUID condominiumId,
            UUID deviceId,
            UUID callId,
            String action,
            String signalingSessionId
    ) {
        UUID commandId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call_id", callId.toString());
        payload.put("action", action);
        payload.put("signaling_session_id", signalingSessionId);

        publishCommandEnvelope(condominiumId, deviceId, commandId, "INTERCOM_ACTION", payload);
        return commandId;
    }

    private void publishCommandEnvelope(
            UUID condominiumId,
            UUID deviceId,
            UUID commandId,
            String type,
            Map<String, Object> payload
    ) {
        String topic = MqttTopicBuilder.commandTopic(condominiumId, deviceId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("command_id", commandId.toString());
        envelope.put("type", type);
        envelope.put("issued_at", Instant.now().getEpochSecond());
        envelope.put("payload", payload);

        if (!properties.getMqtt().isEnabled()) {
            log.warn("MQTT disabled; skipping publish to {} (commandId={}, type={})", topic, commandId, type);
            return;
        }

        try {
            connectIfNecessary();
            byte[] bytes = objectMapper.writeValueAsBytes(envelope);
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(properties.getMqtt().getQos());
            message.setRetained(false);
            mqttClient.publish(topic, message);
            log.info("Published MQTT command type={} commandId={} to {}", type, commandId, topic);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MQTT command payload", ex);
        } catch (MqttException ex) {
            throw new IllegalStateException("Failed to publish MQTT command to " + topic + ": " + ex.getMessage(), ex);
        }
    }

    private synchronized void connectIfNecessary() throws MqttException {
        if (mqttClient == null) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_CLOSED);
        }
        if (!mqttClient.isConnected()) {
            mqttClient.connect(connectOptions);
            subscribeTelemetry();
        }
    }

    private void subscribeTelemetry() {
        try {
            mqttClient.subscribe(MqttTopicBuilder.TELEMETRY_WILDCARD, properties.getMqtt().getQos());
            log.info("Subscribed to MQTT telemetry wildcard topic: {}", MqttTopicBuilder.TELEMETRY_WILDCARD);
        } catch (MqttException ex) {
            log.error("Failed to subscribe to telemetry wildcard topic: {}", ex.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connected (reconnect={}) to {}", reconnect, serverURI);
        subscribeTelemetry();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown cause");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String body = new String(message.getPayload(), StandardCharsets.UTF_8);
        MqttTopicBuilder.parseTelemetryTopic(topic).ifPresentOrElse(
                parsed -> eventPublisher.publishEvent(
                        new MqttTelemetryMessageEvent(parsed.condominiumId(), parsed.deviceId(), topic, body)
                ),
                () -> log.debug("Ignoring MQTT message on unrecognized topic: {}", topic)
        );
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Delivery acknowledged by local Mosquitto broker
    }
}
