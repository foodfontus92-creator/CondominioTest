package com.accesolibre.service;

import com.accesolibre.domain.entity.AdminActivationCode;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.SubscriptionStatus;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.condo.CondominiumResponse;
import com.accesolibre.dto.saas.AdminActivationCodeResponse;
import com.accesolibre.dto.saas.SaasCondoOverviewItem;
import com.accesolibre.dto.saas.SaasOverviewResponse;
import com.accesolibre.dto.saas.UpdateCondoLicenseRequest;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.repository.AdminActivationCodeRepository;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SaasAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long ADMIN_CODE_TTL_HOURS = 72L;

    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final AdminActivationCodeRepository adminActivationCodeRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public SaasAdminService(
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            UserRepository userRepository,
            AdminActivationCodeRepository adminActivationCodeRepository,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.adminActivationCodeRepository = adminActivationCodeRepository;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional
    public AdminActivationCodeResponse generateAdminCode(UUID condoId) {
        tenantAccessGuard.requireSuperAdmin();

        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ADMIN_CODE_TTL_HOURS, ChronoUnit.HOURS);

        AdminActivationCode entity = new AdminActivationCode();
        entity.setCondominiumId(condoId);
        entity.setCode(generateUniqueAdminCode());
        entity.setExpiresAt(expiresAt);

        AdminActivationCode saved = adminActivationCodeRepository.save(entity);
        return new AdminActivationCodeResponse(
                saved.getId(),
                saved.getCondominiumId(),
                condo.getCode(),
                condo.getName(),
                condo.getCommunityType() != null ? condo.getCommunityType() : CommunityType.CONDOMINIUM,
                saved.getCode(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public SaasOverviewResponse getOverview() {
        tenantAccessGuard.requireSuperAdmin();

        Instant now = Instant.now();
        Instant soonThreshold = now.plus(7, ChronoUnit.DAYS);

        List<Condominium> condos = condominiumRepository.findAll().stream()
                .sorted(Comparator.comparing(Condominium::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Unit> allUnits = unitRepository.findAll();
        List<User> allUsers = userRepository.findAll();

        Map<UUID, Long> unitCounts = new LinkedHashMap<>();
        long totalUnits = 0;
        for (Unit u : allUnits) {
            if (Boolean.TRUE.equals(u.getActive())) {
                unitCounts.merge(u.getCondominiumId(), 1L, Long::sum);
                totalUnits++;
            }
        }

        Map<UUID, Long> residentCounts = new LinkedHashMap<>();
        Map<UUID, Long> adminCounts = new LinkedHashMap<>();
        long totalResidents = 0;
        long totalUsers = 0;
        for (User u : allUsers) {
            if (u.getRole() != UserRole.SUPER_ADMIN) {
                totalUsers++;
            }
            if (u.getRole() == UserRole.RESIDENT) {
                residentCounts.merge(u.getCondominiumId(), 1L, Long::sum);
                totalResidents++;
            } else if (u.getRole() == UserRole.CONDO_ADMIN) {
                adminCounts.merge(u.getCondominiumId(), 1L, Long::sum);
            }
        }

        long activeCondos = 0;
        long expiringSoonCondos = 0;
        long expiredOrSuspendedCondos = 0;

        List<SaasCondoOverviewItem> items = new java.util.ArrayList<>(condos.size());
        for (Condominium c : condos) {
            boolean licenseActive = c.isLicenseActiveAt(now);
            SubscriptionStatus effectiveStatus = c.effectiveSubscriptionStatus(now);
            Instant expiresAt = c.getLicenseExpiresAt();

            if (licenseActive) {
                activeCondos++;
                if (expiresAt != null && !expiresAt.isAfter(soonThreshold)) {
                    expiringSoonCondos++;
                }
            } else {
                expiredOrSuspendedCondos++;
            }

            Long daysRemaining = computeDaysRemaining(now, expiresAt);

            items.add(new SaasCondoOverviewItem(
                    c.getId(),
                    c.getCode(),
                    c.getName(),
                    c.getCommunityType() != null ? c.getCommunityType() : CommunityType.CONDOMINIUM,
                    c.getAddress(),
                    c.getSubscriptionStatus() != null ? c.getSubscriptionStatus() : SubscriptionStatus.ACTIVE,
                    effectiveStatus,
                    c.getPlanName() != null ? c.getPlanName() : "Mensual",
                    expiresAt,
                    daysRemaining,
                    c.getMaxUnits() != null ? c.getMaxUnits() : 200,
                    unitCounts.getOrDefault(c.getId(), 0L),
                    residentCounts.getOrDefault(c.getId(), 0L),
                    adminCounts.getOrDefault(c.getId(), 0L),
                    Boolean.TRUE.equals(c.getActive()),
                    licenseActive,
                    c.getCreatedAt()
            ));
        }

        return new SaasOverviewResponse(
                condos.size(),
                activeCondos,
                expiringSoonCondos,
                expiredOrSuspendedCondos,
                totalUnits,
                totalResidents,
                totalUsers,
                items
        );
    }

    @Transactional
    public CondominiumResponse updateLicense(UUID condoId, UpdateCondoLicenseRequest request) {
        tenantAccessGuard.requireSuperAdmin();

        Condominium condo = condominiumRepository.findById(condoId)
                .orElseThrow(() -> new ResourceNotFoundException("Condominium not found: " + condoId));

        Instant now = Instant.now();

        if (request.planName() != null && !request.planName().isBlank()) {
            condo.setPlanName(request.planName().trim());
        }
        if (request.maxUnits() != null) {
            condo.setMaxUnits(request.maxUnits());
        }
        if (request.subscriptionStatus() != null) {
            condo.setSubscriptionStatus(request.subscriptionStatus());
        }
        if (Boolean.TRUE.equals(request.unlimitedLicense())) {
            condo.setLicenseExpiresAt(null);
        } else if (request.licenseExpiresAt() != null) {
            condo.setLicenseExpiresAt(request.licenseExpiresAt());
        }

        if (request.quickAction() != null && !request.quickAction().isBlank()) {
            String action = request.quickAction().trim().toUpperCase(Locale.ROOT);
            switch (action) {
                case "EXTEND_30_DAYS" -> {
                    Instant base = (condo.getLicenseExpiresAt() != null && condo.getLicenseExpiresAt().isAfter(now))
                            ? condo.getLicenseExpiresAt()
                            : now;
                    condo.setLicenseExpiresAt(base.plus(30, ChronoUnit.DAYS));
                    condo.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                    condo.setActive(true);
                }
                case "EXTEND_1_YEAR" -> {
                    Instant base = (condo.getLicenseExpiresAt() != null && condo.getLicenseExpiresAt().isAfter(now))
                            ? condo.getLicenseExpiresAt()
                            : now;
                    condo.setLicenseExpiresAt(base.plus(365, ChronoUnit.DAYS));
                    condo.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                    condo.setActive(true);
                    if (request.planName() == null || request.planName().isBlank()) {
                        condo.setPlanName("Anual");
                    }
                }
                case "ACTIVATE_UNLIMITED" -> {
                    condo.setLicenseExpiresAt(null);
                    condo.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                    condo.setActive(true);
                }
                case "SUSPEND" -> condo.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
                default -> throw new BadRequestException("Acción de licencia no soportada: " + action);
            }
        }

        return CondominiumResponse.from(condominiumRepository.save(condo));
    }

    private Long computeDaysRemaining(Instant now, Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long hours = Duration.between(now, expiresAt).toHours();
        if (hours >= 0) {
            return (long) Math.ceil(hours / 24.0);
        }
        return -((long) Math.ceil(Math.abs(hours) / 24.0));
    }

    private String generateUniqueAdminCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = String.format(Locale.ROOT, "ADM-%04d", SECURE_RANDOM.nextInt(10_000));
            if (!adminActivationCodeRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < 10; i++) {
            String candidate = String.format(Locale.ROOT, "ADM-%06d", SECURE_RANDOM.nextInt(1_000_000));
            if (!adminActivationCodeRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código único de administrador");
    }
}
