package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.dto.unit.BatchCreateUnitsRequest;
import com.accesolibre.dto.unit.BatchCreateUnitsResponse;
import com.accesolibre.dto.unit.CreateUnitRequest;
import com.accesolibre.dto.unit.UnitResponse;
import com.accesolibre.dto.unit.UpdateUnitRequest;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
                .sorted(Comparator
                        .comparing((Unit u) -> u.effectiveBlockName() == null ? "" : u.effectiveBlockName().toLowerCase(Locale.ROOT))
                        .thenComparing(u -> u.getUnitNumber().toLowerCase(Locale.ROOT)))
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
        String blockName = normalizeNullable(request.blockName() != null ? request.blockName() : request.towerOrBlock());
        enforceMaxUnits(condoId, condo, 1);
        assertUniqueUnitInBlock(condoId, null, blockName, unitNumber);

        if (request.intercomExtension() != null && !request.intercomExtension().isBlank()
                && unitRepository.existsByCondominiumIdAndIntercomExtension(condoId, request.intercomExtension().trim())) {
            throw new ConflictException("Intercom extension already in use: " + request.intercomExtension());
        }

        UUID unitId = UUID.randomUUID();
        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setCondominiumId(condoId);
        unit.setUnitNumber(unitNumber);
        unit.setBlockName(blockName);
        unit.setTowerOrBlock(blockName);
        unit.setFloor(normalizeNullable(request.floor()));
        unit.setIntercomExtension(normalizeNullable(request.intercomExtension()));
        unit.setUnitSecret(qrCryptoService.deriveUnitSecretHex(condo.getHmacSecret(), unitId));
        unit.setActive(true);
        unit.setBlocked(false);

        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public BatchCreateUnitsResponse createUnitsBatch(UUID condoId, BatchCreateUnitsRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        String blockName = normalizeNullable(request.blockName());
        if (blockName == null) {
            throw new BadRequestException("El nombre del Block es obligatorio");
        }

        Set<String> deduplicatedNumbers = new LinkedHashSet<>();
        Set<String> seenLower = new LinkedHashSet<>();
        for (String raw : request.unitNumbers()) {
            String trimmed = normalizeNullable(raw);
            if (trimmed != null && seenLower.add(trimmed.toLowerCase(Locale.ROOT))) {
                deduplicatedNumbers.add(trimmed);
            }
        }
        if (deduplicatedNumbers.isEmpty()) {
            throw new BadRequestException("Debes indicar al menos un número de vivienda válido");
        }

        List<Unit> existingUnits = unitRepository.findAllByCondominiumIdOrderByUnitNumberAsc(condoId);
        enforceMaxUnits(condo, existingUnits, deduplicatedNumbers.size());
        String targetBlockKey = normalizeBlockKey(blockName);
        Set<String> existingKeysInBlock = new LinkedHashSet<>();
        for (Unit existing : existingUnits) {
            if (targetBlockKey.equals(normalizeBlockKey(existing.effectiveBlockName()))) {
                existingKeysInBlock.add(existing.getUnitNumber().trim().toLowerCase(Locale.ROOT));
            }
        }

        List<Unit> toCreate = new ArrayList<>(deduplicatedNumbers.size());
        for (String num : deduplicatedNumbers) {
            if (existingKeysInBlock.contains(num.toLowerCase(Locale.ROOT))) {
                throw new ConflictException("La vivienda " + num + " ya existe en " + blockName);
            }
            UUID unitId = UUID.randomUUID();
            Unit unit = new Unit();
            unit.setId(unitId);
            unit.setCondominiumId(condoId);
            unit.setBlockName(blockName);
            unit.setTowerOrBlock(blockName);
            unit.setUnitNumber(num);
            unit.setUnitSecret(qrCryptoService.deriveUnitSecretHex(condo.getHmacSecret(), unitId));
            unit.setActive(true);
            unit.setBlocked(false);
            toCreate.add(unit);
        }

        List<UnitResponse> saved = unitRepository.saveAll(toCreate).stream()
                .map(UnitResponse::from)
                .toList();

        return new BatchCreateUnitsResponse(blockName, saved.size(), saved);
    }

    @Transactional
    public UnitResponse updateUnit(UUID condoId, UUID unitId, UpdateUnitRequest request) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        Unit unit = unitRepository.findByIdAndCondominiumId(unitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));

        String nextUnitNumber = (request.unitNumber() != null && !request.unitNumber().isBlank())
                ? request.unitNumber().trim()
                : unit.getUnitNumber();
        String nextBlock = unit.effectiveBlockName();
        boolean blockUpdated = false;
        if (request.blockName() != null || request.towerOrBlock() != null) {
            nextBlock = normalizeNullable(request.blockName() != null ? request.blockName() : request.towerOrBlock());
            blockUpdated = true;
        }

        if (!nextUnitNumber.equalsIgnoreCase(unit.getUnitNumber()) || blockUpdated) {
            assertUniqueUnitInBlock(condoId, unit.getId(), nextBlock, nextUnitNumber);
        }

        unit.setUnitNumber(nextUnitNumber);
        if (blockUpdated) {
            unit.setBlockName(nextBlock);
            unit.setTowerOrBlock(nextBlock);
        }
        if (request.floor() != null) {
            unit.setFloor(normalizeNullable(request.floor()));
        }
        if (request.intercomExtension() != null) {
            String ext = normalizeNullable(request.intercomExtension());
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

    private void enforceMaxUnits(UUID condoId, Condominium condo, int addingCount) {
        enforceMaxUnits(condo, unitRepository.findAllByCondominiumIdOrderByUnitNumberAsc(condoId), addingCount);
    }

    private void enforceMaxUnits(Condominium condo, List<Unit> existingUnits, int addingCount) {
        int maxUnits = condo.getMaxUnits() != null ? condo.getMaxUnits() : 200;
        long activeCount = existingUnits.stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .count();
        if (activeCount + addingCount > maxUnits) {
            throw new BadRequestException(
                    "El recinto alcanzó el límite de " + maxUnits + " viviendas permitidas en su plan actual."
            );
        }
    }

    private void assertUniqueUnitInBlock(UUID condoId, UUID excludeUnitId, String blockName, String unitNumber) {
        String targetBlock = normalizeBlockKey(blockName);
        String targetNum = unitNumber.trim().toLowerCase(Locale.ROOT);
        for (Unit existing : unitRepository.findAllByCondominiumIdOrderByUnitNumberAsc(condoId)) {
            if (excludeUnitId != null && excludeUnitId.equals(existing.getId())) {
                continue;
            }
            if (targetBlock.equals(normalizeBlockKey(existing.effectiveBlockName()))
                    && targetNum.equals(existing.getUnitNumber().trim().toLowerCase(Locale.ROOT))) {
                String scope = blockName != null ? "en " + blockName : "en el recinto";
                throw new ConflictException("Ya existe una unidad/vivienda " + unitNumber + " " + scope);
            }
        }
    }

    private String normalizeBlockKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
