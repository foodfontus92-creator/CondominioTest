package com.accesolibre.service;

import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;
import com.accesolibre.dto.access.AccessLogResponse;
import com.accesolibre.dto.access.RemoteOpenRequest;
import com.accesolibre.dto.access.RemoteOpenResponse;
import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.mqtt.MqttTopicBuilder;
import com.accesolibre.repository.AccessLogRepository;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccessControlService {

    private final CondominiumRepository condominiumRepository;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final IotDeviceRepository deviceRepository;
    private final AccessLogRepository accessLogRepository;
    private final GeofenceService geofenceService;
    private final MqttGatewayService mqttGatewayService;
    private final TenantAccessGuard tenantAccessGuard;

    public AccessControlService(
            CondominiumRepository condominiumRepository,
            UserRepository userRepository,
            UnitRepository unitRepository,
            IotDeviceRepository deviceRepository,
            AccessLogRepository accessLogRepository,
            GeofenceService geofenceService,
            MqttGatewayService mqttGatewayService,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.condominiumRepository = condominiumRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.deviceRepository = deviceRepository;
        this.accessLogRepository = accessLogRepository;
        this.geofenceService = geofenceService;
        this.mqttGatewayService = mqttGatewayService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    /**
     * Remote gate/door opening with strict multi-tenant isolation, block validation,
     * GPS geofencing against the condominium radius, MQTT command dispatch, and audit logging.
     *
     * noRollbackFor ensures denied access attempts remain persisted in access_logs.
     */
    @Transactional(noRollbackFor = AccessDeniedDomainException.class)
    public RemoteOpenResponse remoteOpen(RemoteOpenRequest request) {
        AuthenticatedUser principal = tenantAccessGuard.currentUser();
        UUID condoId = principal.condominiumId();
        Instant now = Instant.now();

        User user = userRepository.findByIdAndCondominiumId(principal.userId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Condominium condo = condominiumRepository.findById(condoId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found or inactive"));

        IotDevice device = deviceRepository.findByIdAndCondominiumId(request.deviceId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("IoT device not found in condominium: " + request.deviceId()));

        if (!Boolean.TRUE.equals(device.getActive())) {
            throw new BadRequestException("Target IoT device is inactive");
        }

        // 1. Check user active & block status
        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getBlocked())) {
            persistLog(condoId, device.getId(), user.getUnitId(), user.getId(),
                    AccessMethod.REMOTE_APP, AccessResult.DENIED_USER_BLOCKED,
                    Map.of("block_reason", user.getBlockReason() != null ? user.getBlockReason() : "User blocked or inactive"),
                    now);
            throw new AccessDeniedDomainException(
                    "DENIED_USER_BLOCKED",
                    "Remote access denied: user account is blocked or inactive"
            );
        }

        // 2. Check unit active & block status for residents (and any user bound to a unit)
        Unit unit = null;
        if (user.getUnitId() != null) {
            unit = unitRepository.findByIdAndCondominiumId(user.getUnitId(), condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned unit not found"));
            if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
                persistLog(condoId, device.getId(), unit.getId(), user.getId(),
                        AccessMethod.REMOTE_APP, AccessResult.DENIED_UNIT_BLOCKED,
                        Map.of("block_reason", unit.getBlockReason() != null ? unit.getBlockReason() : "Unit blocked or inactive"),
                        now);
                throw new AccessDeniedDomainException(
                        "DENIED_UNIT_BLOCKED",
                        "Remote access denied: unit is blocked or inactive"
                );
            }
        } else if (principal.isResident()) {
            persistLog(condoId, device.getId(), null, user.getId(),
                    AccessMethod.REMOTE_APP, AccessResult.DENIED_UNAUTHORIZED,
                    Map.of("reason", "Resident has no assigned unit"),
                    now);
            throw new AccessDeniedDomainException(
                    "DENIED_UNAUTHORIZED",
                    "Resident has no assigned unit"
            );
        }

        // 3. Validate GPS geofence against condominium coordinates and radius
        if (condo.getLatitude() == null || condo.getLongitude() == null) {
            throw new BadRequestException("Condominium GPS coordinates are not configured for geofencing");
        }

        double distanceMeters = geofenceService.distanceMeters(
                condo.getLatitude(),
                condo.getLongitude(),
                request.latitude(),
                request.longitude()
        );
        double roundedDistance = Math.round(distanceMeters * 100.0) / 100.0;
        int allowedRadius = condo.getGeofenceRadiusMeters();

        if (distanceMeters > allowedRadius) {
            Map<String, Object> geoDetails = new LinkedHashMap<>();
            geoDetails.put("distance_meters", roundedDistance);
            geoDetails.put("allowed_radius_meters", allowedRadius);
            geoDetails.put("latitude", request.latitude());
            geoDetails.put("longitude", request.longitude());

            persistLog(condoId, device.getId(), unit != null ? unit.getId() : null, user.getId(),
                    AccessMethod.REMOTE_APP, AccessResult.DENIED_OUTSIDE_GEOFENCE, geoDetails, now);

            throw new AccessDeniedDomainException(
                    "DENIED_OUTSIDE_GEOFENCE",
                    "Remote access denied: coordinates are outside condominium geofence radius",
                    geoDetails
            );
        }

        // 4. Publish JSON UNLOCK command on condo/{condoId}/device/{deviceId}/command
        int pulseMs = request.pulseMs() != null ? request.pulseMs() : device.getRelayPulseMs();
        AccessMethod method = principal.isResident() ? AccessMethod.REMOTE_APP : AccessMethod.ADMIN_OVERRIDE;
        String topic = MqttTopicBuilder.commandTopic(condoId, device.getId());

        UUID commandId;
        try {
            commandId = mqttGatewayService.publishUnlockCommand(
                    condoId,
                    device.getId(),
                    pulseMs,
                    method.name(),
                    user.getId(),
                    unit != null ? unit.getId() : null,
                    null
            );
        } catch (RuntimeException ex) {
            Map<String, Object> errorDetails = new LinkedHashMap<>();
            errorDetails.put("mqtt_topic", topic);
            errorDetails.put("error", ex.getMessage());
            persistLog(condoId, device.getId(), unit != null ? unit.getId() : null, user.getId(),
                    method, AccessResult.DENIED_DEVICE_OFFLINE, errorDetails, now);
            throw new AccessDeniedDomainException(
                    "DENIED_DEVICE_OFFLINE",
                    "Unable to dispatch MQTT unlock command to local broker",
                    errorDetails
            );
        }

        // 5. Persist GRANTED access log
        Map<String, Object> grantedDetails = new LinkedHashMap<>();
        grantedDetails.put("command_id", commandId.toString());
        grantedDetails.put("mqtt_topic", topic);
        grantedDetails.put("pulse_ms", pulseMs);
        grantedDetails.put("distance_meters", roundedDistance);
        grantedDetails.put("allowed_radius_meters", allowedRadius);
        grantedDetails.put("latitude", request.latitude());
        grantedDetails.put("longitude", request.longitude());

        AccessLog savedLog = persistLog(
                condoId,
                device.getId(),
                unit != null ? unit.getId() : null,
                user.getId(),
                method,
                AccessResult.GRANTED,
                grantedDetails,
                now
        );

        return new RemoteOpenResponse(
                true,
                AccessResult.GRANTED,
                savedLog.getId(),
                commandId,
                topic,
                device.getId(),
                device.getName(),
                roundedDistance,
                allowedRadius,
                pulseMs,
                now
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessLogResponse> listAccessLogs(
            UUID condoId,
            UUID deviceId,
            UUID unitId,
            AccessResult result,
            List<AccessMethod> methods,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        final UUID effectiveUnitId;
        if (caller.isResident()) {
            User liveUser = userRepository.findByIdAndCondominiumId(caller.userId(), condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
            if (!Boolean.TRUE.equals(liveUser.getActive()) || Boolean.TRUE.equals(liveUser.getBlocked())) {
                throw new AccessDeniedDomainException(
                        "DENIED_USER_BLOCKED",
                        "User account is inactive or blocked"
                );
            }
            if (liveUser.getUnitId() == null) {
                return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
            }
            effectiveUnitId = liveUser.getUnitId();   // residents only ever see their own unit
        } else {
            effectiveUnitId = unitId;
        }

        Specification<AccessLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("condominiumId"), condoId));   // tenant isolation
            if (effectiveUnitId != null) {
                predicates.add(cb.equal(root.get("unitId"), effectiveUnitId));
            }
            if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (methods != null && !methods.isEmpty()) {
                predicates.add(root.get("accessMethod").in(methods));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt")
        );
        Page<AccessLog> page = accessLogRepository.findAll(spec, sorted);
        return PageResponse.from(page.map(AccessLogResponse::from));
    }

    private AccessLog persistLog(
            UUID condoId,
            UUID deviceId,
            UUID unitId,
            UUID userId,
            AccessMethod method,
            AccessResult result,
            Map<String, Object> details,
            Instant occurredAt
    ) {
        AccessLog logEntry = new AccessLog();
        logEntry.setCondominiumId(condoId);
        logEntry.setDeviceId(deviceId);
        logEntry.setUnitId(unitId);
        logEntry.setTriggeredByUserId(userId);
        logEntry.setAccessMethod(method);
        logEntry.setResult(result);
        logEntry.setOfflineValidated(false);
        logEntry.setDetails(details);
        logEntry.setOccurredAt(occurredAt);
        return accessLogRepository.save(logEntry);
    }
}
