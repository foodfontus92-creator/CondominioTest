package com.accesolibre.controller;

import com.accesolibre.dto.condo.CondominiumResponse;
import com.accesolibre.dto.saas.AdminActivationCodeResponse;
import com.accesolibre.dto.saas.SaasOverviewResponse;
import com.accesolibre.dto.saas.UpdateCondoLicenseRequest;
import com.accesolibre.service.SaasAdminService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin")
public class SuperAdminController {

    private final SaasAdminService saasAdminService;

    public SuperAdminController(SaasAdminService saasAdminService) {
        this.saasAdminService = saasAdminService;
    }

    @GetMapping("/overview")
    public ResponseEntity<SaasOverviewResponse> overview() {
        return ResponseEntity.ok(saasAdminService.getOverview());
    }

    @PostMapping("/condos/{condoId}/admin-code")
    public ResponseEntity<AdminActivationCodeResponse> generateAdminCode(@PathVariable UUID condoId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saasAdminService.generateAdminCode(condoId));
    }

    @PatchMapping("/condos/{condoId}/license")
    public ResponseEntity<CondominiumResponse> updateLicense(
            @PathVariable UUID condoId,
            @Valid @RequestBody UpdateCondoLicenseRequest request
    ) {
        return ResponseEntity.ok(saasAdminService.updateLicense(condoId, request));
    }
}
