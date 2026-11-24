package com.accesolibre.mqtt;

import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.entity.AccessPass;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;
import com.accesolibre.domain.enums.DeviceStatus;
import com.accesolibre.domain.enums.PassStatus;
import com.accesolibre.repository.AccessLogRepository;
import com.accesolibre.repository.AccessPassRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.service.CompactQrCryptoService;
import com.accesolibre.service.IntercomService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class MqttTelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryListener.class);
    private static final java.util.Set<String> LIVE_EVENTS = java.util.Set.of(
            "HEARTBEAT", "DEVICE_OFFLINE", "ACCESS_EVENT", "GATE_OPEN_TIMEOUT", "GATE_CLOSED");

    private final ObjectMapper objectMapper;
    private final IotDeviceRepository deviceRepository;
    private final AccessPassRepository accessPassRepository;
    private final AccessLogRepository accessLogRepository;
    private final CompactQrCryptoService qrCryptoService;
    private final IntercomService intercomService;
    private final SimpMessagingTemplate messagingTemplate;

    public MqttTelemetryListener(
            ObjectMapper objectMapper,
            IotDeviceRepository deviceRepository,
            AccessPassRepository accessPassRepository,
            AccessLogRepository accessLogRepository,
            CompactQrCryptoService qrCryptoService,
            IntercomService intercomService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.objectMapper = objectMapper;
        this.deviceRepository = deviceRepository;
        this.accessPassRepository = accessPassRepository;
        this.accessLogRepository = accessLogRepository;
        this.qrCryptoService = qrCryptoService;
        this.intercomService = intercomService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    @Transactional
    public void onTelemetryMessage(MqttTelemetryMessageEvent event) {
        try {
            JsonNode root = objectMapper.readTree(event.payloadJson());
            String eventType = root.path("event").asText("").toUpperCase();
            long occurredEpoch = root.path("occurred_at").asLong(Instant.now().getEpochSecond());
            Instant occurredAt = Instant.ofEpochSecond(occurredEpoch);
            JsonNode payload = root.path("payload");

            switch (eventType) {
                case "HEARTBEAT" -> handleHeartbeat(event.condominiumId(), event.deviceId(), payload, occurredAt);
                case "DEVICE_OFFLINE" -> handleDeviceOffline(event.condominiumId(), event.deviceId());
                case "ACCESS_EVENT" -> handleAccessEvent(event.condominiumId(), event.deviceId(), payload, occurredAt);
                case "INTERCOM_RING" -> handleIntercomRing(event.condominiumId(), event.deviceId(), payload);
                case "COMMAND_ACK" -> handleCommandAck(event.deviceId(), payload);
                case "GATE_OPEN_TIMEOUT", "GATE_CLOSED" ->
                        handleGateAlert(event.condominiumId(), event.deviceId(), eventType, payload, occurredAt);
                default -> log.debug("Unhandled MQTT telemetry event '{}' on topic {}", eventType, event.topic());
            }
            broadcastDeviceEvent(event.condominiumId(), event.deviceId(), eventType, occurredAt, payload);
        } catch (Exception ex) {
            log.warn("Failed to process MQTT telemetry on topic {}: {}", event.topic(), ex.getMessage());
        }
    }

    /**
     * Pushes live device events to the web admin over STOMP:
     * /topic/condo/{condoId}/devices (administrators only, enforced in StompJwtChannelInterceptor).
     */
    private void broadcastDeviceEvent(UUID condoId, UUID deviceId, String eventType, Instant occurredAt, JsonNode payload) {
        if (!LIVE_EVENTS.contains(eventType)) {
            return;
        }
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("eventType", eventType);
        message.put("condominiumId", condoId.toString());
        message.put("deviceId", deviceId.toString());
        message.put("occurredAt", occurredAt.toString());
        message.put("payload", payload.isObject()
                ? objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {})
                : Map.of());
        messagingTemplate.convertAndSend("/topic/condo/" + condoId + "/devices", message);
    }

    private void handleHeartbeat(UUID condoId, UUID deviceId, JsonNode payload, Instant occurredAt) {
        deviceRepository.findByIdAndCondominiumId(deviceId, condoId).ifPresent(device -> {
            String statusText = payload.path("status").asText("ONLINE").toUpperCase();
            try {
                device.setStatus(DeviceStatus.valueOf(statusText));
            } catch (IllegalArgumentException ex) {
                device.setStatus(DeviceStatus.ONLINE);
            }
            if (payload.hasNonNull("firmware_version")) {
                device.setFirmwareVersion(payload.get("firmware_version").asText());
            }
            if (payload.hasNonNull("ip_address")) {
                try {
                    device.setIpAddress(InetAddress.getByName(payload.get("ip_address").asText()));
                } catch (UnknownHostException ex) {
                    log.debug("Invalid IP address in heartbeat for device {}", deviceId);
                }
            }
            if (payload.hasNonNull("hmac_key_version_synced")) {
                device.setHmacKeyVersionSynced(payload.get("hmac_key_version_synced").asInt());
            }
            device.setLastSeenAt(occurredAt);
            // Merge instead of replace so gate alert markers (set by GATE_* events) survive heartbeats.
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            if (device.getLastTelemetry() != null) {
                Object lastGateEvent = device.getLastTelemetry().get("last_gate_event");
                Object lastGateEventAt = device.getLastTelemetry().get("last_gate_event_at");
                if (lastGateEvent != null) snapshot.put("last_gate_event", lastGateEvent);
                if (lastGateEventAt != null) snapshot.put("last_gate_event_at", lastGateEventAt);
            }
            snapshot.putAll(objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}));
            device.setLastTelemetry(snapshot);
            deviceRepository.save(device);
        });
    }

    private void handleDeviceOffline(UUID condoId, UUID deviceId) {
        deviceRepository.findByIdAndCondominiumId(deviceId, condoId).ifPresent(device -> {
            device.setStatus(DeviceStatus.OFFLINE);
            deviceRepository.save(device);
            log.info("Marked IoT device {} in condo {} as OFFLINE (LWT)", deviceId, condoId);
        });
    }

    private void handleAccessEvent(UUID condoId, UUID deviceId, JsonNode payload, Instant occurredAt) {
        Optional<IotDevice> deviceOpt = deviceRepository.findByIdAndCondominiumId(deviceId, condoId);
        if (deviceOpt.isEmpty()) {
            log.warn("Ignoring ACCESS_EVENT for unknown device {} in condo {}", deviceId, condoId);
            return;
        }

        AccessMethod method = parseEnum(AccessMethod.class, payload.path("access_method").asText("QR_OFFLINE"), AccessMethod.QR_OFFLINE);
        AccessResult result = parseEnum(AccessResult.class, payload.path("result").asText("ERROR"), AccessResult.ERROR);
        boolean offlineValidated = payload.path("offline_validated").asBoolean(true);

        String passIdB64 = payload.path("pass_id_b64").asText(null);
        String unitIdB64 = payload.path("unit_id_b64").asText(null);

        UUID passId = qrCryptoService.decodeUuidBase64Url(passIdB64).orElse(null);
        UUID unitId = qrCryptoService.decodeUuidBase64Url(unitIdB64).orElse(null);

        UUID persistedPassId = null;
        if (passId != null) {
            Optional<AccessPass> passOpt = accessPassRepository.findByIdAndCondominiumId(passId, condoId);
            if (passOpt.isPresent()) {
                AccessPass pass = passOpt.get();
                persistedPassId = pass.getId();
                if (unitId == null) {
                    unitId = pass.getUnitId();
                }
                if (result == AccessResult.GRANTED && pass.getCurrentUses() < pass.getMaxUses()) {
                    pass.setCurrentUses(pass.getCurrentUses() + 1);
                    if (pass.getCurrentUses() >= pass.getMaxUses()) {
                        pass.setStatus(PassStatus.USED);
                    }
                    accessPassRepository.save(pass);
                }
            }
        }

        AccessLog logEntry = new AccessLog();
        logEntry.setCondominiumId(condoId);
        logEntry.setDeviceId(deviceId);
        logEntry.setUnitId(unitId);
        logEntry.setAccessPassId(persistedPassId);
        logEntry.setAccessMethod(method);
        logEntry.setResult(result);
        logEntry.setOfflineValidated(offlineValidated);
        logEntry.setQrPassIdRaw(passIdB64);
        logEntry.setDetails(objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}));
        logEntry.setOccurredAt(occurredAt);
        logEntry.setSyncedAt(Instant.now());
        accessLogRepository.save(logEntry);
    }

    private void handleIntercomRing(UUID condoId, UUID deviceId, JsonNode payload) {
        String extension = payload.path("intercom_extension").asText("");
        String sessionId = payload.path("signaling_session_id").asText(null);
        if (extension.isBlank()) {
            log.warn("Received INTERCOM_RING without intercom_extension from device {}", deviceId);
            return;
        }
        intercomService.initiateCallFromHardware(condoId, deviceId, extension, sessionId);
    }

    private void handleGateAlert(UUID condoId, UUID deviceId, String eventType, JsonNode payload, Instant occurredAt) {
        if ("GATE_OPEN_TIMEOUT".equals(eventType)) {
            log.warn("GATE ALERT: device {} in condo {} left open for {} s (threshold {} s)",
                    deviceId, condoId, payload.path("open_seconds").asInt(), payload.path("threshold_seconds").asInt());
        } else {
            log.info("Gate closed on device {} in condo {} after {} s", deviceId, condoId, payload.path("open_seconds").asInt());
        }
        deviceRepository.findByIdAndCondominiumId(deviceId, condoId).ifPresent(device -> {
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            if (device.getLastTelemetry() != null) {
                snapshot.putAll(device.getLastTelemetry());
            }
            snapshot.put("last_gate_event", eventType);
            snapshot.put("last_gate_event_at", occurredAt.toString());
            snapshot.put("door_sensor_open", "GATE_OPEN_TIMEOUT".equals(eventType));
            device.setLastTelemetry(snapshot);
            device.setLastSeenAt(occurredAt);
            deviceRepository.save(device);
        });
    }

    private void handleCommandAck(UUID deviceId, JsonNode payload) {
        log.info("MQTT COMMAND_ACK from device {}: commandId={} status={} error={}",
                deviceId,
                payload.path("command_id").asText(),
                payload.path("status").asText(),
                payload.path("error_message").asText(null));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
