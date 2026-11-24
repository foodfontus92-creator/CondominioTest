package com.accesolibre.controller;

import com.accesolibre.dto.condo.CondominiumResponse;
import com.accesolibre.dto.condo.CreateCondominiumRequest;
import com.accesolibre.dto.condo.UpdateCondominiumRequest;
import com.accesolibre.service.CondominiumService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/condominiums")
public class CondominiumController {

    private final CondominiumService condominiumService;

    public CondominiumController(CondominiumService condominiumService) {
        this.condominiumService = condominiumService;
    }

    @GetMapping
    public ResponseEntity<List<CondominiumResponse>> list() {
        return ResponseEntity.ok(condominiumService.listCondominiums());
    }

    @GetMapping("/{condoId}")
    public ResponseEntity<CondominiumResponse> get(@PathVariable UUID condoId) {
        return ResponseEntity.ok(condominiumService.getCondominium(condoId));
    }

    @PostMapping
    public ResponseEntity<CondominiumResponse> create(@Valid @RequestBody CreateCondominiumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(condominiumService.createCondominium(request));
    }

    @PatchMapping("/{condoId}")
    public ResponseEntity<CondominiumResponse> update(
            @PathVariable UUID condoId,
            @Valid @RequestBody UpdateCondominiumRequest request
    ) {
        return ResponseEntity.ok(condominiumService.updateCondominium(condoId, request));
    }

    @PostMapping("/{condoId}/rotate-hmac")
    public ResponseEntity<CondominiumResponse> rotateHmac(@PathVariable UUID condoId) {
        return ResponseEntity.ok(condominiumService.rotateHmacSecret(condoId));
    }
}
