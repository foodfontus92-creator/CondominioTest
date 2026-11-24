package com.accesolibre.controller;

import com.accesolibre.dto.unit.CreateUnitRequest;
import com.accesolibre.dto.unit.UnitResponse;
import com.accesolibre.dto.unit.UpdateUnitRequest;
import com.accesolibre.service.UnitService;
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
@RequestMapping("/api/v1/condos/{condoId}/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<UnitResponse>> list(@PathVariable UUID condoId) {
        return ResponseEntity.ok(unitService.listUnits(condoId));
    }

    @GetMapping("/{unitId}")
    public ResponseEntity<UnitResponse> get(@PathVariable UUID condoId, @PathVariable UUID unitId) {
        return ResponseEntity.ok(unitService.getUnit(condoId, unitId));
    }

    @PostMapping
    public ResponseEntity<UnitResponse> create(
            @PathVariable UUID condoId,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.createUnit(condoId, request));
    }

    @PatchMapping("/{unitId}")
    public ResponseEntity<UnitResponse> update(
            @PathVariable UUID condoId,
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request
    ) {
        return ResponseEntity.ok(unitService.updateUnit(condoId, unitId, request));
    }
}
