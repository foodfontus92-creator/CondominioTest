package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.enums.DeviceStatus;
import com.accesolibre.dto.device.CreateDeviceRequest;
import com.accesolibre.dto.device.DeviceResponse;
import com.accesolibre.dto.device.UpdateDeviceRequest;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IotDeviceRepository;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IotDeviceService {

    private final IotDeviceRepository deviceRepository;
    private final CondominiumRepository condominiumRepository;
    private final CompactQrCryptoService qrCryptoService;
    private final MqttGatewayService mqttGatewayService;
    private final TenantAccessGuard tenantAccessGuard;

    public IotDeviceService(
            IotDeviceRepository deviceRepository,
            CondominiumRepository condominiumRepository,
            CompactQrCryptoService qrCryptoService,
            MqttGatewayService mqttGatewayService,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.deviceRepository = deviceRepository;
        this.condominiumRepository = condominiumRepository;
        this.qrCryptoService = qrCryptoService;
        this.mqttGatewayService = mqttGatewayService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(UUID condoId) {
        tenantAccessGuard.requireCondoAccess(condoId);
        return deviceRepository.findAllByCondominiumIdOrderByNameAsc(condoId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDevice(UUID condoId, UUID deviceId) {
        tenantAccessGuard.requireCondoAccess(condoId);
        IotDevice device = deviceRepository.findByIdAndCondominiumId(deviceId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("IoT device not found: " + deviceId));
        return DeviceResponse.from(device);
    }

    @Transactional
    public DeviceResponse createDevice(UUID condoId, CreateDeviceRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        if (!condominiumRepository.existsById(condoId)) {
            throw new ResourceNotFoundException("Condominium not found: " + condoId);
        }
        String hardwareUid = request.hardwareUid().trim().toUpperCase();
        if (deviceRepository.existsByHardwareUidIgnoreCase(hardwareUid)) {
            throw new ConflictException("Device hardwareUid already registered: " + hardwareUid);
        }
        String mqttUser = request.mqttUsername().trim();
        if (deviceRepository.existsByMqttUsernameIgnoreCase(mqttUser)) {
            throw new ConflictException("Device mqttUsername already registered: " + mqttUser);
        }

        IotDevice device = new IotDevice();
        device.setCondominiumId(condoId);
        device.setHardwareUid(hardwareUid);
        device.setName(request.name().trim());
        device.setDeviceType(request.deviceType());
        device.setLocationDescription(request.locationDescription());
        device.setMqttUsername(mqttUser);
        device.setRelayPulseMs(request.relayPulseMs() != null ? request.relayPulseMs() : 3000);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setHmacKeyVersionSynced(0);
        device.setActive(true);

        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public DeviceResponse updateDevice(UUID condoId, UUID deviceId, UpdateDeviceRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        IotDevice device = deviceRepository.findByIdAndCondominiumId(deviceId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("IoT device not found: " + deviceId));

        if (request.name() != null && !request.name().isBlank()) {
            device.setName(request.name().trim());
        }
        if (request.deviceType() != null) {
            device.setDeviceType(request.deviceType());
        }
        if (request.locationDescription() != null) {
            device.setLocationDescription(request.locationDescription());
        }
        if (request.relayPulseMs() != null) {
            device.setRelayPulseMs(request.relayPulseMs());
        }
        if (request.status() != null) {
            device.setStatus(request.status());
        }
        if (request.active() != null) {
            device.setActive(request.active());
        }

        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional(readOnly = true)
    public UUID syncDeviceConfig(UUID condoId, UUID deviceId) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));
        IotDevice device = deviceRepository.findByIdAndCondominiumId(deviceId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("IoT device not found: " + deviceId));

        String condoIdB64 = qrCryptoService.encodeUuidBase64Url(condoId);
        return mqttGatewayService.publishSyncConfigCommand(
                condoId,
                device.getId(),
                condoIdB64,
                condo.getHmacSecret(),
                condo.getHmacKeyVersion(),
                device.getRelayPulseMs()
        );
    }
}
