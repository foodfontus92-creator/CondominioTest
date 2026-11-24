package com.accesolibre.controller;

import com.accesolibre.dto.device.CreateDeviceRequest;
import com.accesolibre.dto.device.DeviceResponse;
import com.accesolibre.dto.device.UpdateDeviceRequest;
import com.accesolibre.service.IotDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/condos/{condoId}/devices")
public class IotDeviceController {

    private final IotDeviceService deviceService;

    public IotDeviceController(IotDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> list(@PathVariable UUID condoId) {
        return ResponseEntity.ok(deviceService.listDevices(condoId));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> get(@PathVariable UUID condoId, @PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceService.getDevice(condoId, deviceId));
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(
            @PathVariable UUID condoId,
            @Valid @RequestBody CreateDeviceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createDevice(condoId, request));
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> update(
            @PathVariable UUID condoId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceRequest request
    ) {
        return ResponseEntity.ok(deviceService.updateDevice(condoId, deviceId, request));
    }

    @PostMapping("/{deviceId}/sync-config")
    public ResponseEntity<Map<String, Object>> syncConfig(
            @PathVariable UUID condoId,
            @PathVariable UUID deviceId
    ) {
        UUID commandId = deviceService.syncDeviceConfig(condoId, deviceId);
        return ResponseEntity.accepted().body(Map.of(
                "commandId", commandId,
                "status", "DISPATCHED"
        ));
    }
}
