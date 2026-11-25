package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.RefreshToken;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.UnitActivationCode;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.auth.ActivateUnitRequest;
import com.accesolibre.dto.auth.AuthResponse;
import com.accesolibre.dto.unit.CreateUnitActivationCodeRequest;
import com.accesolibre.dto.unit.RevokeUnitResidentsResponse;
import com.accesolibre.dto.unit.UnitActivationCodeResponse;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.RefreshTokenRepository;
import com.accesolibre.repository.UnitActivationCodeRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.JwtService;
import com.accesolibre.security.TenantAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitActivationFlowTest {

    private UUID condoId;
    private UUID unitId;
    private UUID adminId;

    private final Map<UUID, Condominium> condos = new LinkedHashMap<>();
    private final Map<UUID, Unit> units = new LinkedHashMap<>();
    private final Map<UUID, User> users = new LinkedHashMap<>();
    private final Map<UUID, UnitActivationCode> codes = new LinkedHashMap<>();
    private final List<RefreshToken> refreshTokens = new ArrayList<>();

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UnitActivationService unitActivationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        condoId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        Condominium condo = new Condominium();
        condo.setId(condoId);
        condo.setCode("00b");
        condo.setName("Condominio 00B");
        condo.setHmacSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        condo.setActive(true);
        condos.put(condoId, condo);

        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setCondominiumId(condoId);
        unit.setUnitNumber("203");
        unit.setUnitSecret("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        unit.setActive(true);
        unit.setBlocked(false);
        units.put(unitId, unit);

        AuthenticatedUser admin = new AuthenticatedUser(adminId, condoId, null, "admin@00b.local", UserRole.CONDO_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.authorities())
        );

        CondominiumRepository condoRepo = (CondominiumRepository) Proxy.newProxyInstance(
                CondominiumRepository.class.getClassLoader(),
                new Class<?>[]{CondominiumRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findById" -> Optional.ofNullable(condos.get((UUID) a[0]));
                    default -> throw new UnsupportedOperationException("CondominiumRepository." + m.getName());
                }
        );

        UnitRepository unitRepo = (UnitRepository) Proxy.newProxyInstance(
                UnitRepository.class.getClassLoader(),
                new Class<?>[]{UnitRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findByIdAndCondominiumId" -> {
                        Unit found = units.get((UUID) a[0]);
                        yield (found != null && found.getCondominiumId().equals(a[1]))
                                ? Optional.of(found)
                                : Optional.empty();
                    }
                    default -> throw new UnsupportedOperationException("UnitRepository." + m.getName());
                }
        );

        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "existsByCondominiumIdAndEmailIgnoreCase" -> {
                        UUID cId = (UUID) a[0];
                        String email = ((String) a[1]).toLowerCase();
                        yield users.values().stream()
                                .anyMatch(u -> u.getCondominiumId().equals(cId) && u.getEmail().equalsIgnoreCase(email));
                    }
                    case "findAllByCondominiumIdAndUnitIdOrderByFullNameAsc" -> {
                        UUID cId = (UUID) a[0];
                        UUID uId = (UUID) a[1];
                        yield users.values().stream()
                                .filter(u -> u.getCondominiumId().equals(cId) && uId.equals(u.getUnitId()))
                                .toList();
                    }
                    case "save" -> {
                        User u = (User) a[0];
                        if (u.getId() == null) u.setId(UUID.randomUUID());
                        users.put(u.getId(), u);
                        yield u;
                    }
                    case "saveAll" -> {
                        @SuppressWarnings("unchecked")
                        Iterable<User> batch = (Iterable<User>) a[0];
                        List<User> out = new ArrayList<>();
                        for (User u : batch) {
                            if (u.getId() == null) u.setId(UUID.randomUUID());
                            users.put(u.getId(), u);
                            out.add(u);
                        }
                        yield out;
                    }
                    default -> throw new UnsupportedOperationException("UserRepository." + m.getName());
                }
        );

        UnitActivationCodeRepository codeRepo = (UnitActivationCodeRepository) Proxy.newProxyInstance(
                UnitActivationCodeRepository.class.getClassLoader(),
                new Class<?>[]{UnitActivationCodeRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "existsByCodeIgnoreCase" -> {
                        String code = (String) a[0];
                        yield codes.values().stream().anyMatch(c -> c.getCode().equalsIgnoreCase(code));
                    }
                    case "findByCodeForUpdate" -> {
                        String code = (String) a[0];
                        yield codes.values().stream()
                                .filter(c -> c.getCode().equalsIgnoreCase(code))
                                .findFirst();
                    }
                    case "deactivateActiveByUnit" -> {
                        UUID cId = (UUID) a[0];
                        UUID uId = (UUID) a[1];
                        int count = 0;
                        for (UnitActivationCode c : codes.values()) {
                            if (c.getCondominiumId().equals(cId) && c.getUnitId().equals(uId) && Boolean.TRUE.equals(c.getActive())) {
                                c.setActive(false);
                                count++;
                            }
                        }
                        yield count;
                    }
                    case "save" -> {
                        UnitActivationCode c = (UnitActivationCode) a[0];
                        if (c.getId() == null) c.setId(UUID.randomUUID());
                        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
                        codes.put(c.getId(), c);
                        yield c;
                    }
                    default -> throw new UnsupportedOperationException("UnitActivationCodeRepository." + m.getName());
                }
        );

        RefreshTokenRepository refreshRepo = (RefreshTokenRepository) Proxy.newProxyInstance(
                RefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{RefreshTokenRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "save" -> {
                        RefreshToken rt = (RefreshToken) a[0];
                        if (rt.getId() == null) rt.setId(UUID.randomUUID());
                        refreshTokens.add(rt);
                        yield rt;
                    }
                    case "revokeAllActiveByUser" -> {
                        UUID cId = (UUID) a[0];
                        UUID uId = (UUID) a[1];
                        Instant at = (Instant) a[2];
                        int count = 0;
                        for (RefreshToken rt : refreshTokens) {
                            if (rt.getCondominiumId().equals(cId) && rt.getUserId().equals(uId) && !Boolean.TRUE.equals(rt.getRevoked())) {
                                rt.setRevoked(true);
                                rt.setRevokedAt(at);
                                count++;
                            }
                        }
                        yield count;
                    }
                    default -> throw new UnsupportedOperationException("RefreshTokenRepository." + m.getName());
                }
        );

        AppProperties props = new AppProperties();
        props.getSecurity().setJwtSecret("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        TenantAccessGuard guard = new TenantAccessGuard();

        unitActivationService = new UnitActivationService(codeRepo, condoRepo, unitRepo, userRepo, refreshRepo, guard);
        authService = new AuthService(condoRepo, unitRepo, codeRepo, userRepo, refreshRepo, passwordEncoder, new JwtService(props), guard);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatesFormattedCodeAndRevokesPreviousCodes() {
        UnitActivationCodeResponse first = unitActivationService.createActivationCode(
                condoId, unitId, new CreateUnitActivationCodeRequest(4, 48, true));
        assertThat(first.code()).matches("^00B-203-\\d{4}$");
        assertThat(first.maxUses()).isEqualTo(4);
        assertThat(first.usedCount()).isZero();
        assertThat(first.active()).isTrue();

        UnitActivationCodeResponse second = unitActivationService.createActivationCode(
                condoId, unitId, new CreateUnitActivationCodeRequest(2, 24, true));
        assertThat(second.code()).matches("^00B-203-\\d{4}$");
        assertThat(codes.get(first.id()).getActive()).isFalse();
        assertThat(codes.get(second.id()).getActive()).isTrue();
    }

    @Test
    void activateUnitCreatesResidentEnforcesMaxUsesAndIssuesJwt() {
        UnitActivationCodeResponse created = unitActivationService.createActivationCode(
                condoId, unitId, new CreateUnitActivationCodeRequest(2, 48, true));

        AuthResponse res1 = authService.activateUnit(new ActivateUnitRequest(
                created.code().toLowerCase(),
                "Ana Pérez",
                "Ana@Familia.local",
                "+56912345678",
                "ClaveSegura123"
        ));

        assertThat(res1.accessToken()).isNotBlank();
        assertThat(res1.refreshToken()).isNotBlank();
        assertThat(res1.user().role()).isEqualTo(UserRole.RESIDENT);
        assertThat(res1.user().unitId()).isEqualTo(unitId);
        assertThat(res1.user().condominiumId()).isEqualTo(condoId);
        assertThat(codes.get(created.id()).getUsedCount()).isEqualTo(1);
        assertThat(codes.get(created.id()).getActive()).isTrue();

        AuthResponse res2 = authService.activateUnit(new ActivateUnitRequest(
                created.code(),
                "Luis Pérez",
                "luis@familia.local",
                null,
                "ClaveSegura123"
        ));
        assertThat(res2.user().role()).isEqualTo(UserRole.RESIDENT);
        assertThat(codes.get(created.id()).getUsedCount()).isEqualTo(2);
        assertThat(codes.get(created.id()).getActive()).isFalse();

        assertThatThrownBy(() -> authService.activateUnit(new ActivateUnitRequest(
                created.code(),
                "Tercero Pérez",
                "tercero@familia.local",
                null,
                "ClaveSegura123"
        ))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void revokeResidentsDeactivatesUnitResidentsAndInvalidatesCodes() {
        UnitActivationCodeResponse code = unitActivationService.createActivationCode(
                condoId, unitId, new CreateUnitActivationCodeRequest(4, 48, true));
        authService.activateUnit(new ActivateUnitRequest(
                code.code(), "Residente Saliente", "saliente@familia.local", null, "ClaveSegura123"
        ));

        RevokeUnitResidentsResponse result = unitActivationService.revokeResidents(condoId, unitId);
        assertThat(result.deactivatedResidents()).isEqualTo(1);
        assertThat(result.invalidatedCodes()).isEqualTo(1);
        assertThat(users.values()).allSatisfy(u -> assertThat(u.getActive()).isFalse());
        assertThat(refreshTokens).allSatisfy(rt -> assertThat(rt.getRevoked()).isTrue());
        assertThat(codes.get(code.id()).getActive()).isFalse();
    }
}
