package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.entity.AccessPass;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;
import com.accesolibre.domain.enums.PassStatus;
import com.accesolibre.domain.enums.PassType;
import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.dto.qr.AccessPassResponse;
import com.accesolibre.dto.qr.CreateAccessPassRequest;
import com.accesolibre.dto.qr.RegisterOfflinePassRequest;
import com.accesolibre.dto.qr.UnitSecretResponse;
import com.accesolibre.dto.qr.ValidateQrRequest;
import com.accesolibre.dto.qr.ValidateQrResponse;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ForbiddenTenantException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.repository.AccessLogRepository;
import com.accesolibre.repository.AccessPassRepository;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccessPassService {

    private static final Logger log = LoggerFactory.getLogger(AccessPassService.class);
    private static final String CANONICAL_QR_FORMAT =
            "PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED";

    private final AccessPassRepository accessPassRepository;
    private final AccessLogRepository accessLogRepository;
    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final IotDeviceRepository deviceRepository;
    private final CompactQrCryptoService qrCryptoService;
    private final MqttGatewayService mqttGatewayService;
    private final TenantAccessGuard tenantAccessGuard;
    private final AppProperties properties;

    public AccessPassService(
            AccessPassRepository accessPassRepository,
            AccessLogRepository accessLogRepository,
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            UserRepository userRepository,
            IotDeviceRepository deviceRepository,
            CompactQrCryptoService qrCryptoService,
            MqttGatewayService mqttGatewayService,
            TenantAccessGuard tenantAccessGuard,
            AppProperties properties
    ) {
        this.accessPassRepository = accessPassRepository;
        this.accessLogRepository = accessLogRepository;
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.qrCryptoService = qrCryptoService;
        this.mqttGatewayService = mqttGatewayService;
        this.tenantAccessGuard = tenantAccessGuard;
        this.properties = properties;
    }

    /**
     * Delivers the authenticated resident's derived unit_secret so their mobile app
     * can generate offline compact QR passes without ever holding the condo master secret.
     */
    @Transactional(readOnly = true)
    public UnitSecretResponse getUnitSecretForCurrentUser() {
        AuthenticatedUser principal = tenantAccessGuard.currentUser();
        User user = userRepository.findByIdAndCondominiumId(principal.userId(), principal.condominiumId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_USER_BLOCKED", "User is inactive or blocked");
        }
        if (user.getUnitId() == null) {
            throw new BadRequestException("Authenticated user is not assigned to a unit");
        }

        Condominium condo = condominiumRepository.findById(user.getCondominiumId())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found or inactive"));

        Unit unit = unitRepository.findByIdAndCondominiumId(user.getUnitId(), user.getCondominiumId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_UNIT_BLOCKED", "Unit is inactive or blocked");
        }

        return new UnitSecretResponse(
                condo.getId(),
                qrCryptoService.encodeUuidBase64Url(condo.getId()),
                unit.getId(),
                qrCryptoService.encodeUuidBase64Url(unit.getId()),
                unit.getUnitNumber(),
                unit.effectiveBlockName(),
                unit.getUnitSecret(),
                condo.getHmacKeyVersion(),
                CANONICAL_QR_FORMAT,
                qrCryptoService.getExpectedHmacHexLength()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessPassResponse> listPasses(UUID condoId, Pageable pageable) {
        AuthenticatedUser user = tenantAccessGuard.requireCondoAccess(condoId);
        Page<AccessPass> page;
        if (user.isResident()) {
            if (user.unitId() == null) {
                return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
            }
            page = accessPassRepository.findAllByCondominiumIdAndUnitIdOrderByCreatedAtDesc(condoId, user.unitId(), pageable);
        } else {
            page = accessPassRepository.findAllByCondominiumIdOrderByCreatedAtDesc(condoId, pageable);
        }
        return PageResponse.from(page.map(AccessPassResponse::from));
    }

    @Transactional
    public AccessPassResponse createPass(UUID condoId, CreateAccessPassRequest request) {
        AuthenticatedUser principal = tenantAccessGuard.requireCondoAccess(condoId);
        UUID targetUnitId = resolveTargetUnitId(principal, request.unitId());

        User creator = userRepository.findByIdAndCondominiumId(principal.userId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(creator.getActive()) || Boolean.TRUE.equals(creator.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_USER_BLOCKED", "User is inactive or blocked");
        }

        Condominium condo = condominiumRepository.findById(condoId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found or inactive"));

        Unit unit = unitRepository.findByIdAndCondominiumId(targetUnitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + targetUnitId));
        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_UNIT_BLOCKED", "Unit is inactive or blocked");
        }

        Instant now = Instant.now();
        Instant validFrom = request.validFrom() != null ? request.validFrom() : now;
        Instant expiresAt = request.expiresAt() != null
                ? request.expiresAt()
                : validFrom.plus(properties.getQr().getDefaultPassTtlHours(), ChronoUnit.HOURS);

        if (!expiresAt.isAfter(validFrom)) {
            throw new BadRequestException("expiresAt must be strictly after validFrom");
        }

        UUID passId = UUID.randomUUID();
        String qrPayload = qrCryptoService.generateQrPayload(passId, condoId, unit.getId(), expiresAt, unit.getUnitSecret());

        AccessPass pass = new AccessPass();
        pass.setId(passId);
        pass.setCondominiumId(condoId);
        pass.setUnitId(unit.getId());
        pass.setCreatedByUserId(creator.getId());
        pass.setVisitorName(request.visitorName().trim());
        pass.setVisitorDocument(request.visitorDocument());
        pass.setPassType(request.passType() != null ? request.passType() : PassType.VISITOR);
        pass.setQrPayload(qrPayload);
        pass.setHmacKeyVersion(condo.getHmacKeyVersion());
        pass.setValidFrom(validFrom);
        pass.setExpiresAt(expiresAt);
        pass.setMaxUses(request.maxUses() != null ? request.maxUses() : 1);
        pass.setCurrentUses(0);
        pass.setStatus(PassStatus.ACTIVE);

        return AccessPassResponse.from(accessPassRepository.save(pass));
    }

    /**
     * Registers a compact QR pass generated offline by a resident's mobile app using their unit_secret.
     */
    @Transactional
    public AccessPassResponse registerOfflinePass(UUID condoId, RegisterOfflinePassRequest request) {
        AuthenticatedUser principal = tenantAccessGuard.requireCondoAccess(condoId);
        CompactQrCryptoService.ParsedQrPayload parsed = qrCryptoService.parseQrPayload(request.qrPayload().trim())
                .orElseThrow(() -> new BadRequestException("Invalid compact QR payload format"));

        if (!condoId.equals(parsed.condoId())) {
            throw new ForbiddenTenantException("QR payload condominium does not match tenant path");
        }
        tenantAccessGuard.requireUnitAccess(condoId, parsed.unitId());

        Optional<AccessPass> existing = accessPassRepository.findByIdAndCondominiumId(parsed.passId(), condoId);
        if (existing.isPresent()) {
            return AccessPassResponse.from(existing.get());
        }

        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found"));
        Unit unit = unitRepository.findByIdAndCondominiumId(parsed.unitId(), condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));

        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException("DENIED_UNIT_BLOCKED", "Unit is inactive or blocked");
        }

        if (!qrCryptoService.verifyTruncatedHmac(unit.getUnitSecret(), parsed.signingInput(), parsed.truncatedMacHex())) {
            throw new BadRequestException("QR HMAC-SHA256 signature verification failed");
        }

        Instant now = Instant.now();
        if (!parsed.expiresAt().isAfter(now)) {
            throw new BadRequestException("Cannot register an already expired QR pass");
        }

        AccessPass pass = new AccessPass();
        pass.setId(parsed.passId());
        pass.setCondominiumId(condoId);
        pass.setUnitId(unit.getId());
        pass.setCreatedByUserId(principal.userId());
        pass.setVisitorName(request.visitorName().trim());
        pass.setVisitorDocument(request.visitorDocument());
        pass.setPassType(request.passType() != null ? request.passType() : PassType.VISITOR);
        pass.setQrPayload(request.qrPayload().trim());
        pass.setHmacKeyVersion(condo.getHmacKeyVersion());
        pass.setValidFrom(now);
        pass.setExpiresAt(parsed.expiresAt());
        pass.setMaxUses(request.maxUses() != null ? request.maxUses() : 1);
        pass.setCurrentUses(0);
        pass.setStatus(PassStatus.ACTIVE);

        return AccessPassResponse.from(accessPassRepository.save(pass));
    }

    /**
     * Online validation endpoint for compact QR payloads (also optionally triggers MQTT unlock).
     */
    @Transactional
    public ValidateQrResponse validateQrPass(UUID condoId, ValidateQrRequest request) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        String rawPayload = request.qrPayload().trim();
        Instant now = Instant.now();

        IotDevice device = null;
        if (request.deviceId() != null) {
            device = deviceRepository.findByIdAndCondominiumId(request.deviceId(), condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("IoT device not found: " + request.deviceId()));
        }

        Optional<CompactQrCryptoService.ParsedQrPayload> parsedOpt = qrCryptoService.parseQrPayload(rawPayload);
        if (parsedOpt.isEmpty()) {
            recordQrLog(condoId, device, null, null, caller.userId(), AccessResult.DENIED_INVALID_HMAC, rawPayload,
                    Map.of("reason", "Malformed compact QR format or length >= 120"));
            return new ValidateQrResponse(false, AccessResult.DENIED_INVALID_HMAC, "Malformed compact QR payload",
                    null, condoId, null, null, null, null, null, null);
        }

        CompactQrCryptoService.ParsedQrPayload parsed = parsedOpt.get();
        if (!condoId.equals(parsed.condoId())) {
            recordQrLog(condoId, device, null, null, caller.userId(), AccessResult.DENIED_WRONG_CONDO, parsed.passIdB64(),
                    Map.of("qr_condo_id", parsed.condoId().toString()));
            return new ValidateQrResponse(false, AccessResult.DENIED_WRONG_CONDO, "QR belongs to another condominium",
                    parsed.passId(), parsed.condoId(), parsed.unitId(), null, parsed.expiresAt(), null, null, null);
        }

        Optional<Unit> unitOpt = unitRepository.findByIdAndCondominiumId(parsed.unitId(), condoId);
        if (unitOpt.isEmpty()) {
            recordQrLog(condoId, device, null, null, caller.userId(), AccessResult.DENIED_INVALID_HMAC, parsed.passIdB64(),
                    Map.of("reason", "Unknown unit"));
            return new ValidateQrResponse(false, AccessResult.DENIED_INVALID_HMAC, "Unit not found in condominium",
                    parsed.passId(), condoId, parsed.unitId(), null, parsed.expiresAt(), null, null, null);
        }
        Unit unit = unitOpt.get();

        if (!qrCryptoService.verifyTruncatedHmac(unit.getUnitSecret(), parsed.signingInput(), parsed.truncatedMacHex())) {
            recordQrLog(condoId, device, unit.getId(), null, caller.userId(), AccessResult.DENIED_INVALID_HMAC, parsed.passIdB64(),
                    Map.of("reason", "HMAC mismatch"));
            return new ValidateQrResponse(false, AccessResult.DENIED_INVALID_HMAC, "Invalid HMAC-SHA256 signature",
                    parsed.passId(), condoId, unit.getId(), null, parsed.expiresAt(), null, null, null);
        }

        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            recordQrLog(condoId, device, unit.getId(), null, caller.userId(), AccessResult.DENIED_UNIT_BLOCKED, parsed.passIdB64(),
                    Map.of("reason", "Unit inactive or blocked"));
            return new ValidateQrResponse(false, AccessResult.DENIED_UNIT_BLOCKED, "Unit is blocked or inactive",
                    parsed.passId(), condoId, unit.getId(), null, parsed.expiresAt(), null, null, null);
        }

        if (now.isAfter(parsed.expiresAt().plusSeconds(qrCryptoService.getClockSkewSeconds()))) {
            recordQrLog(condoId, device, unit.getId(), null, caller.userId(), AccessResult.DENIED_EXPIRED, parsed.passIdB64(),
                    Map.of("expires_at", parsed.expiresAt().toString()));
            return new ValidateQrResponse(false, AccessResult.DENIED_EXPIRED, "QR pass has expired",
                    parsed.passId(), condoId, unit.getId(), null, parsed.expiresAt(), null, null, null);
        }

        Optional<AccessPass> passOpt = accessPassRepository.findByIdAndCondominiumId(parsed.passId(), condoId);
        AccessPass pass = passOpt.orElse(null);
        if (pass != null) {
            if (pass.getStatus() == PassStatus.REVOKED) {
                recordQrLog(condoId, device, unit.getId(), pass.getId(), caller.userId(), AccessResult.DENIED_REVOKED, parsed.passIdB64(),
                        Map.of("revoked_at", String.valueOf(pass.getRevokedAt())));
                return new ValidateQrResponse(false, AccessResult.DENIED_REVOKED, "QR pass was revoked",
                        pass.getId(), condoId, unit.getId(), pass.getVisitorName(), pass.getExpiresAt(),
                        pass.getCurrentUses(), pass.getMaxUses(), null);
            }
            if (pass.getCurrentUses() >= pass.getMaxUses() || pass.getStatus() == PassStatus.USED) {
                recordQrLog(condoId, device, unit.getId(), pass.getId(), caller.userId(), AccessResult.DENIED_MAX_USES, parsed.passIdB64(),
                        Map.of("max_uses", pass.getMaxUses()));
                return new ValidateQrResponse(false, AccessResult.DENIED_MAX_USES, "QR pass reached maximum uses",
                        pass.getId(), condoId, unit.getId(), pass.getVisitorName(), pass.getExpiresAt(),
                        pass.getCurrentUses(), pass.getMaxUses(), null);
            }
            pass.setCurrentUses(pass.getCurrentUses() + 1);
            if (pass.getCurrentUses() >= pass.getMaxUses()) {
                pass.setStatus(PassStatus.USED);
            }
            accessPassRepository.save(pass);
        }

        UUID mqttCommandId = null;
        if (device != null && Boolean.TRUE.equals(request.triggerUnlock())) {
            mqttCommandId = mqttGatewayService.publishUnlockCommand(
                    condoId,
                    device.getId(),
                    device.getRelayPulseMs(),
                    "QR_ONLINE",
                    caller.userId(),
                    unit.getId(),
                    null
            );
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("registered_in_db", pass != null);
        if (mqttCommandId != null) {
            details.put("command_id", mqttCommandId.toString());
        }
        recordQrLog(condoId, device, unit.getId(), pass != null ? pass.getId() : null, caller.userId(),
                AccessResult.GRANTED, parsed.passIdB64(), details);

        return new ValidateQrResponse(
                true,
                AccessResult.GRANTED,
                "QR pass validated successfully",
                parsed.passId(),
                condoId,
                unit.getId(),
                pass != null ? pass.getVisitorName() : "Offline Pass Visitor",
                parsed.expiresAt(),
                pass != null ? pass.getCurrentUses() : 1,
                pass != null ? pass.getMaxUses() : 1,
                mqttCommandId
        );
    }

    @Transactional
    public AccessPassResponse revokePass(UUID condoId, UUID passId) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        AccessPass pass = accessPassRepository.findByIdAndCondominiumId(passId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Access pass not found: " + passId));

        tenantAccessGuard.requireUnitAccess(condoId, pass.getUnitId());

        if (pass.getStatus() != PassStatus.REVOKED) {
            pass.setStatus(PassStatus.REVOKED);
            pass.setRevokedAt(Instant.now());
            pass.setRevokedByUserId(caller.userId());
            pass = accessPassRepository.save(pass);

            // Push REVOKE_PASS to all active ESP32 devices in this condominium
            String passIdB64 = qrCryptoService.encodeUuidBase64Url(pass.getId());
            long expEpoch = pass.getExpiresAt().getEpochSecond();
            List<IotDevice> devices = deviceRepository.findAllByCondominiumIdAndActiveTrueOrderByNameAsc(condoId);
            for (IotDevice device : devices) {
                try {
                    mqttGatewayService.publishRevokePassCommand(condoId, device.getId(), passIdB64, expEpoch);
                } catch (RuntimeException ex) {
                    log.warn("Could not publish REVOKE_PASS to device {}: {}", device.getId(), ex.getMessage());
                }
            }
        }

        return AccessPassResponse.from(pass);
    }

    private UUID resolveTargetUnitId(AuthenticatedUser principal, UUID requestedUnitId) {
        if (principal.isResident()) {
            if (principal.unitId() == null) {
                throw new BadRequestException("Resident user has no assigned unit");
            }
            if (requestedUnitId != null && !requestedUnitId.equals(principal.unitId())) {
                throw new ForbiddenTenantException("Residents may only create passes for their own unit");
            }
            return principal.unitId();
        }
        if (requestedUnitId == null) {
            throw new BadRequestException("unitId is required for administrators creating an access pass");
        }
        return requestedUnitId;
    }

    private void recordQrLog(
            UUID condoId,
            IotDevice device,
            UUID unitId,
            UUID passId,
            UUID triggeredByUserId,
            AccessResult result,
            String qrPassIdRaw,
            Map<String, Object> details
    ) {
        AccessLog logEntry = new AccessLog();
        logEntry.setCondominiumId(condoId);
        logEntry.setDeviceId(device != null ? device.getId() : null);
        logEntry.setUnitId(unitId);
        logEntry.setAccessPassId(passId);
        logEntry.setTriggeredByUserId(triggeredByUserId);
        logEntry.setAccessMethod(AccessMethod.QR_ONLINE);
        logEntry.setResult(result);
        logEntry.setOfflineValidated(false);
        logEntry.setQrPassIdRaw(qrPassIdRaw != null && qrPassIdRaw.length() > 64 ? qrPassIdRaw.substring(0, 64) : qrPassIdRaw);
        logEntry.setDetails(details);
        logEntry.setOccurredAt(Instant.now());
        accessLogRepository.save(logEntry);
    }
}
