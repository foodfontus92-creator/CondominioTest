package com.accesolibre.service;

import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IntercomCall;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;
import com.accesolibre.domain.enums.IntercomCallStatus;
import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.dto.intercom.InitiateIntercomCallRequest;
import com.accesolibre.dto.intercom.IntercomCallResponse;
import com.accesolibre.dto.intercom.IntercomSignalEvent;
import com.accesolibre.dto.intercom.PublicIntercomInfoResponse;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ForbiddenTenantException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.repository.AccessLogRepository;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IntercomCallRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IntercomService {

    private static final Logger log = LoggerFactory.getLogger(IntercomService.class);

    private final IntercomCallRepository callRepository;
    private final CondominiumRepository condominiumRepository;
    private final IotDeviceRepository deviceRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final MqttGatewayService mqttGatewayService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantAccessGuard tenantAccessGuard;

    public IntercomService(
            IntercomCallRepository callRepository,
            CondominiumRepository condominiumRepository,
            IotDeviceRepository deviceRepository,
            UnitRepository unitRepository,
            UserRepository userRepository,
            AccessLogRepository accessLogRepository,
            MqttGatewayService mqttGatewayService,
            SimpMessagingTemplate messagingTemplate,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.callRepository = callRepository;
        this.condominiumRepository = condominiumRepository;
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.accessLogRepository = accessLogRepository;
        this.mqttGatewayService = mqttGatewayService;
        this.messagingTemplate = messagingTemplate;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    /**
     * Public endpoint invoked when a visitor scans the static door QR code.
     * Exposes only non-PII callable unit identifiers for that condominium.
     */
    @Transactional(readOnly = true)
    public PublicIntercomInfoResponse getPublicDoorInfo(String publicQrToken) {
        IotDevice device = deviceRepository.findByPublicQrToken(publicQrToken.trim())
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Door intercom QR token not recognized"));

        Condominium condo = condominiumRepository.findById(device.getCondominiumId())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Condominium is inactive"));

        List<PublicIntercomInfoResponse.CallableUnit> callableUnits = unitRepository
                .findAllByCondominiumIdAndActiveTrueOrderByUnitNumberAsc(condo.getId())
                .stream()
                .filter(u -> !Boolean.TRUE.equals(u.getBlocked()))
                .map(u -> new PublicIntercomInfoResponse.CallableUnit(
                        u.getId(),
                        u.getUnitNumber(),
                        u.getTowerOrBlock(),
                        u.getFloor(),
                        u.getIntercomExtension()
                ))
                .toList();

        return new PublicIntercomInfoResponse(
                condo.getId(),
                condo.getName(),
                device.getId(),
                device.getName(),
                device.getLocationDescription(),
                callableUnits
        );
    }

    /**
     * Visitor rings a unit after scanning the public door QR code.
     * Persists the call and broadcasts a real-time STOMP alert to the resident's unit channel.
     */
    @Transactional
    public IntercomCallResponse initiatePublicCall(InitiateIntercomCallRequest request) {
        IotDevice device = deviceRepository.findByPublicQrToken(request.publicQrToken().trim())
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Door intercom QR token not recognized"));

        UUID condoId = device.getCondominiumId();
        Unit unit = resolveUnitForCall(condoId, request.unitId(), request.intercomExtension());

        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException("UNIT_UNAVAILABLE", "Target unit cannot receive intercom calls");
        }

        String sessionId = "lan-rtc-" + UUID.randomUUID().toString().substring(0, 12);
        return createAndBroadcastRing(
                condoId,
                device,
                unit,
                sessionId,
                request.visitorName(),
                request.visitorNote()
        );
    }

    /**
     * Triggered from physical ESP32 intercom panel via MQTT INTERCOM_RING telemetry.
     */
    @Transactional
    public IntercomCallResponse initiateCallFromHardware(
            UUID condoId,
            UUID deviceId,
            String intercomExtension,
            String signalingSessionId
    ) {
        IotDevice device = deviceRepository.findByIdAndCondominiumId(deviceId, condoId)
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("IoT device not found: " + deviceId));

        Unit unit = unitRepository.findByCondominiumIdAndIntercomExtension(condoId, intercomExtension)
                .filter(u -> Boolean.TRUE.equals(u.getActive()) && !Boolean.TRUE.equals(u.getBlocked()))
                .orElseThrow(() -> new ResourceNotFoundException("Callable unit not found for extension " + intercomExtension));

        String sessionId = (signalingSessionId != null && !signalingSessionId.isBlank())
                ? signalingSessionId
                : "lan-rtc-" + UUID.randomUUID().toString().substring(0, 12);

        return createAndBroadcastRing(condoId, device, unit, sessionId, "Entrance Panel Visitor", null);
    }

    @Transactional(readOnly = true)
    public IntercomCallResponse getPublicCallStatus(UUID callId) {
        IntercomCall call = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom call not found: " + callId));
        return IntercomCallResponse.from(call);
    }

    @Transactional(readOnly = true)
    public PageResponse<IntercomCallResponse> listCalls(UUID condoId, Pageable pageable) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        Page<IntercomCall> page;
        if (caller.isResident()) {
            if (caller.unitId() == null) {
                return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
            }
            page = callRepository.findAllByCondominiumIdAndUnitIdOrderByStartedAtDesc(condoId, caller.unitId(), pageable);
        } else {
            page = callRepository.findAllByCondominiumIdOrderByStartedAtDesc(condoId, pageable);
        }
        return PageResponse.from(page.map(IntercomCallResponse::from));
    }

    @Transactional
    public IntercomCallResponse handleDecision(UUID condoId, UUID callId, String rawAction) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        return handleDecisionAsUser(condoId, callId, rawAction, caller);
    }

    @Transactional
    public IntercomCallResponse handleDecisionFromStomp(UUID callId, String rawAction, AuthenticatedUser caller) {
        IntercomCall call = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom call not found: " + callId));
        return handleDecisionAsUser(call.getCondominiumId(), callId, rawAction, caller);
    }

    @Transactional
    public IntercomCallResponse handleDecisionAsUser(
            UUID condoId,
            UUID callId,
            String rawAction,
            AuthenticatedUser caller
    ) {
        if (!caller.isSuperAdmin() && !condoId.equals(caller.condominiumId())) {
            throw new ForbiddenTenantException("Cross-tenant intercom action denied");
        }

        IntercomCall call = callRepository.findByIdAndCondominiumId(callId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom call not found: " + callId));

        if (caller.isResident() && (caller.unitId() == null || !caller.unitId().equals(call.getUnitId()))) {
            throw new ForbiddenTenantException("Residents may only respond to calls for their own unit");
        }

        IotDevice device = deviceRepository.findByIdAndCondominiumId(call.getDeviceId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom device not found"));
        Unit unit = unitRepository.findByIdAndCondominiumId(call.getUnitId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom unit not found"));

        String action = rawAction.trim().toUpperCase();
        Instant now = Instant.now();
        UUID mqttCommandId = null;

        switch (action) {
            case "ANSWER" -> {
                call.setStatus(IntercomCallStatus.ANSWERED);
                call.setAnsweredByUserId(caller.userId());
                if (call.getAnsweredAt() == null) {
                    call.setAnsweredAt(now);
                }
                publishIntercomMqttSafe(condoId, device.getId(), call.getId(), "ANSWERED", call.getSignalingSessionId());
            }
            case "UNLOCK" -> {
                verifyUserAndUnitCanUnlock(condoId, caller.userId(), unit);
                call.setStatus(IntercomCallStatus.UNLOCKED);
                call.setDoorUnlocked(true);
                call.setAnsweredByUserId(caller.userId());
                if (call.getAnsweredAt() == null) {
                    call.setAnsweredAt(now);
                }
                call.setEndedAt(now);
                call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), now).getSeconds()));

                mqttCommandId = mqttGatewayService.publishUnlockCommand(
                        condoId,
                        device.getId(),
                        device.getRelayPulseMs(),
                        "INTERCOM_UNLOCK",
                        caller.userId(),
                        unit.getId(),
                        call.getId()
                );

                // Audit intercom unlock in access_logs
                AccessLog logEntry = new AccessLog();
                logEntry.setCondominiumId(condoId);
                logEntry.setDeviceId(device.getId());
                logEntry.setUnitId(unit.getId());
                logEntry.setTriggeredByUserId(caller.userId());
                logEntry.setAccessMethod(AccessMethod.INTERCOM_UNLOCK);
                logEntry.setResult(AccessResult.GRANTED);
                logEntry.setOfflineValidated(false);
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("intercom_call_id", call.getId().toString());
                details.put("command_id", mqttCommandId.toString());
                details.put("visitor_name", call.getVisitorName());
                logEntry.setDetails(details);
                logEntry.setOccurredAt(now);
                accessLogRepository.save(logEntry);
            }
            case "REJECT" -> {
                call.setStatus(IntercomCallStatus.REJECTED);
                call.setAnsweredByUserId(caller.userId());
                call.setEndedAt(now);
                call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), now).getSeconds()));
                publishIntercomMqttSafe(condoId, device.getId(), call.getId(), "REJECTED", call.getSignalingSessionId());
            }
            case "END" -> {
                call.setStatus(IntercomCallStatus.ENDED);
                call.setEndedAt(now);
                call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), now).getSeconds()));
                publishIntercomMqttSafe(condoId, device.getId(), call.getId(), "ENDED", call.getSignalingSessionId());
            }
            default -> throw new BadRequestException("Unsupported intercom action: " + action);
        }

        IntercomCall saved = callRepository.save(call);
        broadcastCallEvent("CALL_" + saved.getStatus().name(), saved, device, unit, mqttCommandId, null);
        return IntercomCallResponse.from(saved);
    }

    /**
     * Relays local LAN WebRTC SDP/ICE signaling packets between visitor browser and resident app via STOMP.
     */
    @Transactional(readOnly = true)
    public void relayLocalSignaling(UUID callId, Map<String, Object> signalPayload) {
        IntercomCall call = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Intercom call not found: " + callId));
        IotDevice device = deviceRepository.findByIdAndCondominiumId(call.getDeviceId(), call.getCondominiumId())
                .orElse(null);
        Unit unit = unitRepository.findByIdAndCondominiumId(call.getUnitId(), call.getCondominiumId())
                .orElse(null);
        broadcastCallEvent("WEBRTC_SIGNAL", call, device, unit, null, signalPayload);
    }

    private IntercomCallResponse createAndBroadcastRing(
            UUID condoId,
            IotDevice device,
            Unit unit,
            String signalingSessionId,
            String visitorName,
            String visitorNote
    ) {
        IntercomCall call = new IntercomCall();
        call.setCondominiumId(condoId);
        call.setDeviceId(device.getId());
        call.setUnitId(unit.getId());
        call.setStatus(IntercomCallStatus.RINGING);
        call.setSignalingSessionId(signalingSessionId);
        call.setVisitorName(visitorName != null && !visitorName.isBlank() ? visitorName.trim() : "Visitante");
        call.setVisitorNote(visitorNote);
        call.setDoorUnlocked(false);
        call.setStartedAt(Instant.now());

        IntercomCall saved = callRepository.save(call);
        broadcastCallEvent("INCOMING_CALL", saved, device, unit, null, null);
        return IntercomCallResponse.from(saved);
    }

    private void broadcastCallEvent(
            String eventType,
            IntercomCall call,
            IotDevice device,
            Unit unit,
            UUID mqttCommandId,
            Map<String, Object> signalPayload
    ) {
        IntercomSignalEvent event = new IntercomSignalEvent(
                eventType,
                call.getId(),
                call.getCondominiumId(),
                call.getDeviceId(),
                device != null ? device.getName() : null,
                call.getUnitId(),
                unit != null ? unit.getUnitNumber() : null,
                call.getStatus(),
                call.getSignalingSessionId(),
                call.getVisitorName(),
                call.getVisitorNote(),
                call.getDoorUnlocked(),
                mqttCommandId,
                signalPayload,
                Instant.now()
        );

        String unitTopic = "/topic/condo/" + call.getCondominiumId() + "/unit/" + call.getUnitId() + "/intercom";
        String condoTopic = "/topic/condo/" + call.getCondominiumId() + "/intercom";
        String visitorTopic = "/topic/public/intercom/call/" + call.getId();

        messagingTemplate.convertAndSend(unitTopic, event);
        messagingTemplate.convertAndSend(condoTopic, event);
        messagingTemplate.convertAndSend(visitorTopic, event);
    }

    private Unit resolveUnitForCall(UUID condoId, UUID unitId, String intercomExtension) {
        if (unitId != null) {
            return unitRepository.findByIdAndCondominiumId(unitId, condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target unit not found"));
        }
        if (intercomExtension != null && !intercomExtension.isBlank()) {
            return unitRepository.findByCondominiumIdAndIntercomExtension(condoId, intercomExtension.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Target intercom extension not found: " + intercomExtension));
        }
        throw new BadRequestException("Either unitId or intercomExtension must be provided");
    }

    private void verifyUserAndUnitCanUnlock(UUID condoId, UUID userId, Unit unit) {
        User user = userRepository.findByIdAndCondominiumId(userId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_USER_BLOCKED", "Blocked or inactive user cannot unlock doors");
        }
        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_UNIT_BLOCKED", "Blocked or inactive unit cannot unlock doors");
        }
    }

    private void publishIntercomMqttSafe(UUID condoId, UUID deviceId, UUID callId, String action, String sessionId) {
        try {
            mqttGatewayService.publishIntercomActionCommand(condoId, deviceId, callId, action, sessionId);
        } catch (RuntimeException ex) {
            log.warn("Could not publish INTERCOM_ACTION ({}) to device {}: {}", action, deviceId, ex.getMessage());
        }
    }
}
