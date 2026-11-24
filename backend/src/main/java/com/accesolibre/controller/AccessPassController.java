package com.accesolibre.controller;

import com.accesolibre.dto.common.PageResponse;
import com.accesolibre.dto.qr.AccessPassResponse;
import com.accesolibre.dto.qr.CreateAccessPassRequest;
import com.accesolibre.dto.qr.RegisterOfflinePassRequest;
import com.accesolibre.dto.qr.UnitSecretResponse;
import com.accesolibre.dto.qr.ValidateQrRequest;
import com.accesolibre.dto.qr.ValidateQrResponse;
import com.accesolibre.service.AccessPassService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccessPassController {

    private final AccessPassService accessPassService;

    public AccessPassController(AccessPassService accessPassService) {
        this.accessPassService = accessPassService;
    }

    /**
     * Delivers the authenticated resident's derived unit_secret for offline compact QR generation.
     */
    @GetMapping("/access-passes/unit-secret")
    public ResponseEntity<UnitSecretResponse> getUnitSecret() {
        return ResponseEntity.ok(accessPassService.getUnitSecretForCurrentUser());
    }

    @GetMapping("/condos/{condoId}/access-passes")
    public ResponseEntity<PageResponse<AccessPassResponse>> list(
            @PathVariable UUID condoId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(accessPassService.listPasses(condoId, pageable));
    }

    @PostMapping("/condos/{condoId}/access-passes")
    public ResponseEntity<AccessPassResponse> create(
            @PathVariable UUID condoId,
            @Valid @RequestBody CreateAccessPassRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accessPassService.createPass(condoId, request));
    }

    @PostMapping("/condos/{condoId}/access-passes/register")
    public ResponseEntity<AccessPassResponse> registerOffline(
            @PathVariable UUID condoId,
            @Valid @RequestBody RegisterOfflinePassRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accessPassService.registerOfflinePass(condoId, request));
    }

    @PostMapping("/condos/{condoId}/access-passes/validate")
    public ResponseEntity<ValidateQrResponse> validate(
            @PathVariable UUID condoId,
            @Valid @RequestBody ValidateQrRequest request
    ) {
        return ResponseEntity.ok(accessPassService.validateQrPass(condoId, request));
    }

    @PostMapping("/condos/{condoId}/access-passes/{passId}/revoke")
    public ResponseEntity<AccessPassResponse> revoke(
            @PathVariable UUID condoId,
            @PathVariable UUID passId
    ) {
        return ResponseEntity.ok(accessPassService.revokePass(condoId, passId));
    }
}
