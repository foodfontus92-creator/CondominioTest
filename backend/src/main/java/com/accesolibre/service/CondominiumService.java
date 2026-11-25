package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.SubscriptionStatus;
import com.accesolibre.dto.condo.CondominiumResponse;
import com.accesolibre.dto.condo.CreateCondominiumRequest;
import com.accesolibre.dto.condo.UpdateCondominiumRequest;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CondominiumService {

    private static final Logger log = LoggerFactory.getLogger(CondominiumService.class);

    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final IotDeviceRepository iotDeviceRepository;
    private final CompactQrCryptoService qrCryptoService;
    private final MqttGatewayService mqttGatewayService;
    private final TenantAccessGuard tenantAccessGuard;

    public CondominiumService(
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            IotDeviceRepository iotDeviceRepository,
            CompactQrCryptoService qrCryptoService,
            MqttGatewayService mqttGatewayService,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.iotDeviceRepository = iotDeviceRepository;
        this.qrCryptoService = qrCryptoService;
        this.mqttGatewayService = mqttGatewayService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional(readOnly = true)
    public List<CondominiumResponse> listCondominiums() {
        AuthenticatedUser user = tenantAccessGuard.currentUser();
        if (user.isSuperAdmin()) {
            return condominiumRepository.findAll().stream()
                    .map(CondominiumResponse::from)
                    .toList();
        }
        Condominium own = condominiumRepository.findById(user.condominiumId())
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found"));
        return List.of(CondominiumResponse.from(own));
    }

    @Transactional(readOnly = true)
    public CondominiumResponse getCondominium(UUID condoId) {
        tenantAccessGuard.requireCondoAccess(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));
        return CondominiumResponse.from(condo);
    }

    @Transactional
    public CondominiumResponse createCondominium(CreateCondominiumRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        String code = request.code().trim().toLowerCase();
        if (condominiumRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Condominium code already exists: " + code);
        }

        Condominium condo = new Condominium();
        condo.setCode(code);
        condo.setName(request.name().trim());
        condo.setCommunityType(request.communityType() != null ? request.communityType() : CommunityType.CONDOMINIUM);
        condo.setAddress(request.address());
        condo.setTimezone(request.timezone() != null && !request.timezone().isBlank() ? request.timezone().trim() : "UTC");
        condo.setHmacSecret(qrCryptoService.generateRandomHmacSecretHex());
        condo.setHmacKeyVersion(1);
        condo.setLatitude(request.latitude());
        condo.setLongitude(request.longitude());
        condo.setGeofenceRadiusMeters(request.geofenceRadiusMeters() != null ? request.geofenceRadiusMeters() : 150);
        condo.setSubscriptionStatus(request.subscriptionStatus() != null ? request.subscriptionStatus() : SubscriptionStatus.ACTIVE);
        condo.setPlanName(request.planName() != null && !request.planName().isBlank() ? request.planName().trim() : "Mensual");
        condo.setLicenseExpiresAt(request.licenseExpiresAt());
        condo.setMaxUnits(request.maxUnits() != null ? request.maxUnits() : 200);
        condo.setActive(true);

        return CondominiumResponse.from(condominiumRepository.save(condo));
    }

    @Transactional
    public CondominiumResponse updateCondominium(UUID condoId, UpdateCondominiumRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        if (request.name() != null && !request.name().isBlank()) {
            condo.setName(request.name().trim());
        }
        if (request.communityType() != null) {
            condo.setCommunityType(request.communityType());
        }
        if (request.address() != null) {
            condo.setAddress(request.address());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            condo.setTimezone(request.timezone().trim());
        }
        if (request.latitude() != null) {
            condo.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            condo.setLongitude(request.longitude());
        }
        if (request.geofenceRadiusMeters() != null) {
            condo.setGeofenceRadiusMeters(request.geofenceRadiusMeters());
        }
        if (request.active() != null) {
            condo.setActive(request.active());
        }

        return CondominiumResponse.from(condominiumRepository.save(condo));
    }

    @Transactional
    public CondominiumResponse rotateHmacSecret(UUID condoId) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        String newSecret = qrCryptoService.generateRandomHmacSecretHex();
        condo.setHmacSecret(newSecret);
        condo.setHmacKeyVersion(condo.getHmacKeyVersion() + 1);
        Condominium saved = condominiumRepository.save(condo);

        // Re-derive deterministic unit_secret for every unit in the condominium
        List<Unit> units = unitRepository.findAllByCondominiumIdOrderByUnitNumberAsc(condoId);
        for (Unit unit : units) {
            unit.setUnitSecret(qrCryptoService.deriveUnitSecretHex(newSecret, unit.getId()));
        }
        unitRepository.saveAll(units);

        // Push SYNC_CONFIG via MQTT to all active devices in this condominium
        String condoIdB64 = qrCryptoService.encodeUuidBase64Url(condoId);
        List<IotDevice> devices = iotDeviceRepository.findAllByCondominiumIdAndActiveTrueOrderByNameAsc(condoId);
        for (IotDevice device : devices) {
            try {
                mqttGatewayService.publishSyncConfigCommand(
                        condoId,
                        device.getId(),
                        condoIdB64,
                        newSecret,
                        saved.getHmacKeyVersion(),
                        device.getRelayPulseMs()
                );
            } catch (RuntimeException ex) {
                log.warn("Could not push SYNC_CONFIG to device {} during HMAC rotation: {}", device.getId(), ex.getMessage());
            }
        }

        return CondominiumResponse.from(saved);
    }
}
