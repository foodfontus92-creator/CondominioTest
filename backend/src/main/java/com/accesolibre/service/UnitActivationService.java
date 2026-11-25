package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.UnitActivationCode;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.unit.CreateUnitActivationCodeRequest;
import com.accesolibre.dto.unit.RevokeUnitResidentsResponse;
import com.accesolibre.dto.unit.UnitActivationCodeResponse;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.RefreshTokenRepository;
import com.accesolibre.repository.UnitActivationCodeRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UnitActivationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UnitActivationCodeRepository activationCodeRepository;
    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public UnitActivationService(
            UnitActivationCodeRepository activationCodeRepository,
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.activationCodeRepository = activationCodeRepository;
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional
    public UnitActivationCodeResponse createActivationCode(
            UUID condoId,
            UUID unitId,
            CreateUnitActivationCodeRequest request
    ) {
        tenantAccessGuard.requireCondoAdmin(condoId);

        Condominium condo = condominiumRepository.findById(condoId)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found or inactive: " + condoId));

        Unit unit = unitRepository.findByIdAndCondominiumId(unitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));

        if (!Boolean.TRUE.equals(unit.getActive())) {
            throw new BadRequestException("No se puede generar un código para una unidad dada de baja");
        }

        int maxUses = (request != null && request.maxUses() != null) ? request.maxUses() : 4;
        int expiresInHours = (request != null && request.expiresInHours() != null) ? request.expiresInHours() : 48;
        boolean revokePrevious = (request == null || request.revokePrevious() == null || request.revokePrevious());

        if (revokePrevious) {
            activationCodeRepository.deactivateActiveByUnit(condoId, unitId);
        }

        String code = generateUniqueCode(condo.getCode(), unit.effectiveBlockName(), unit.getUnitNumber());
        Instant expiresAt = Instant.now().plus(expiresInHours, ChronoUnit.HOURS);

        UnitActivationCode entity = new UnitActivationCode();
        entity.setCondominiumId(condoId);
        entity.setUnitId(unitId);
        entity.setCode(code);
        entity.setMaxUses(maxUses);
        entity.setUsedCount(0);
        entity.setExpiresAt(expiresAt);
        entity.setActive(true);

        UnitActivationCode saved = activationCodeRepository.save(entity);

        return new UnitActivationCodeResponse(
                saved.getId(),
                saved.getCondominiumId(),
                condo.getName(),
                saved.getUnitId(),
                unit.getUnitNumber(),
                saved.getCode(),
                saved.getMaxUses(),
                saved.getUsedCount(),
                saved.getExpiresAt(),
                Boolean.TRUE.equals(saved.getActive()),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public RevokeUnitResidentsResponse revokeResidents(UUID condoId, UUID unitId) {
        tenantAccessGuard.requireCondoAdmin(condoId);

        Unit unit = unitRepository.findByIdAndCondominiumId(unitId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));

        Instant now = Instant.now();
        List<User> unitUsers = userRepository.findAllByCondominiumIdAndUnitIdOrderByFullNameAsc(condoId, unitId);
        List<User> modifiedResidents = new ArrayList<>();

        for (User user : unitUsers) {
            if (user.getRole() == UserRole.RESIDENT && Boolean.TRUE.equals(user.getActive())) {
                user.setActive(false);
                modifiedResidents.add(user);
                refreshTokenRepository.revokeAllActiveByUser(condoId, user.getId(), now);
            }
        }
        if (!modifiedResidents.isEmpty()) {
            userRepository.saveAll(modifiedResidents);
        }

        int invalidatedCodes = activationCodeRepository.deactivateActiveByUnit(condoId, unitId);

        return new RevokeUnitResidentsResponse(
                unit.getId(),
                unit.getUnitNumber(),
                modifiedResidents.size(),
                invalidatedCodes
        );
    }

    private String generateUniqueCode(String condoCode, String blockName, String unitNumber) {
        String condoPart = sanitizeSegment(condoCode);
        String unitPart = sanitizeSegment(unitNumber);
        String prefix = (blockName != null && !blockName.isBlank())
                ? condoPart + "-" + sanitizeSegment(blockName) + "-" + unitPart
                : condoPart + "-" + unitPart;

        for (int attempt = 0; attempt < 10; attempt++) {
            String digits = String.format(Locale.ROOT, "%04d", SECURE_RANDOM.nextInt(10_000));
            String candidate = prefix + "-" + digits;
            if (candidate.length() > 80) {
                candidate = candidate.substring(candidate.length() - 80);
            }
            if (!activationCodeRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de activación único");
    }

    private String sanitizeSegment(String raw) {
        if (raw == null) {
            return "X";
        }
        String sanitized = raw.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return sanitized.isEmpty() ? "X" : sanitized;
    }
}
