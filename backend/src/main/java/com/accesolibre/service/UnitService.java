package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.dto.unit.CreateUnitRequest;
import com.accesolibre.dto.unit.UnitResponse;
import com.accesolibre.dto.unit.UpdateUnitRequest;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final CondominiumRepository condominiumRepository;
    private final CompactQrCryptoService qrCryptoService;
    private final TenantAccessGuard tenantAccessGuard;

    public UnitService(
            UnitRepository unitRepository,
            CondominiumRepository condominiumRepository,
            CompactQrCryptoService qrCryptoService,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.unitRepository = unitRepository;
        this.condominiumRepository = condominiumRepository;
        this.qrCryptoService = qrCryptoService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits(UUID condoId) {
        AuthenticatedUser user = tenantAccessGuard.requireCondoAccess(condoId);
        if (user.isResident()) {
            if (user.unitId() == null) {
                return List.of();
            }
            return unitRepository.findByIdAndCondominiumId(user.unitId(), condoId)
                    .map(UnitResponse::from)
                    .map(List::of)
                    .orElse(List.of());
        }
        return unitRepository.findAllByCondominiumIdOrderByUnitNumberAsc(condoId).stream()
                .map(UnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnit(UUID condoId, UUID unitId) {
        tenantAccessGuard.requireUnitAccess(condoId, unitId);
        Unit unit = unitRepository.findByIdAndCondominiumId(unitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));
        return UnitResponse.from(unit);
    }

    @Transactional
    public UnitResponse createUnit(UUID condoId, CreateUnitRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        String unitNumber = request.unitNumber().trim();
        if (unitRepository.existsByCondominiumIdAndUnitNumberIgnoreCase(condoId, unitNumber)) {
            throw new ConflictException("Unit number already exists in condominium: " + unitNumber);
        }
        if (request.intercomExtension() != null && !request.intercomExtension().isBlank()
                && unitRepository.existsByCondominiumIdAndIntercomExtension(condoId, request.intercomExtension().trim())) {
            throw new ConflictException("Intercom extension already in use: " + request.intercomExtension());
        }

        UUID unitId = UUID.randomUUID();
        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setCondominiumId(condoId);
        unit.setUnitNumber(unitNumber);
        unit.setTowerOrBlock(request.towerOrBlock());
        unit.setFloor(request.floor());
        unit.setIntercomExtension(request.intercomExtension() != null && !request.intercomExtension().isBlank()
                ? request.intercomExtension().trim() : null);
        unit.setUnitSecret(qrCryptoService.deriveUnitSecretHex(condo.getHmacSecret(), unitId));
        unit.setActive(true);
        unit.setBlocked(false);

        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse updateUnit(UUID condoId, UUID unitId, UpdateUnitRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Unit unit = unitRepository.findByIdAndCondominiumId(unitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));

        if (request.unitNumber() != null && !request.unitNumber().isBlank()) {
            String nextNum = request.unitNumber().trim();
            if (!nextNum.equalsIgnoreCase(unit.getUnitNumber())
                    && unitRepository.existsByCondominiumIdAndUnitNumberIgnoreCase(condoId, nextNum)) {
                throw new ConflictException("Unit number already exists: " + nextNum);
            }
            unit.setUnitNumber(nextNum);
        }
        if (request.towerOrBlock() != null) {
            unit.setTowerOrBlock(request.towerOrBlock());
        }
        if (request.floor() != null) {
            unit.setFloor(request.floor());
        }
        if (request.intercomExtension() != null) {
            String ext = request.intercomExtension().isBlank() ? null : request.intercomExtension().trim();
            if (ext != null && !ext.equals(unit.getIntercomExtension())
                    && unitRepository.existsByCondominiumIdAndIntercomExtension(condoId, ext)) {
                throw new ConflictException("Intercom extension already in use: " + ext);
            }
            unit.setIntercomExtension(ext);
        }
        if (request.active() != null) {
            unit.setActive(request.active());
        }
        if (request.blocked() != null) {
            unit.setBlocked(request.blocked());
            if (!request.blocked()) {
                unit.setBlockReason(null);
            }
        }
        if (request.blockReason() != null) {
            unit.setBlockReason(request.blockReason());
        }

        return UnitResponse.from(unitRepository.save(unit));
    }
}
