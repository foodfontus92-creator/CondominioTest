package com.accesolibre.service;

import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.user.ResidentsSummaryResponse;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.RefreshTokenRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceSummaryTest {

    private UUID condoId;
    private UUID unitA;
    private UUID unitB;
    private UUID unitC;
    private UserService userService;
    private List<User> allUsers;
    private List<Unit> allUnits;

    @BeforeEach
    void setUp() {
        condoId = UUID.randomUUID();
        unitA = UUID.randomUUID();
        unitB = UUID.randomUUID();
        unitC = UUID.randomUUID();

        Unit u1 = new Unit();
        u1.setId(unitA);
        u1.setCondominiumId(condoId);
        u1.setUnitNumber("101");
        u1.setActive(true);

        Unit u2 = new Unit();
        u2.setId(unitB);
        u2.setCondominiumId(condoId);
        u2.setUnitNumber("102");
        u2.setActive(true);

        Unit u3 = new Unit();
        u3.setId(unitC);
        u3.setCondominiumId(condoId);
        u3.setUnitNumber("103");
        u3.setActive(true);
        allUnits = List.of(u1, u2, u3);

        User active1 = resident("Ana", unitA, true, false);
        User active2 = resident("Luis", unitA, true, false); // shares unitA -> unitA counted once
        User blocked1 = resident("Mario", unitB, true, true);
        User inactive1 = resident("Sofia", unitC, false, false);
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setCondominiumId(condoId);
        admin.setRole(UserRole.CONDO_ADMIN);
        admin.setActive(true);
        admin.setBlocked(false);

        allUsers = List.of(active1, active2, blocked1, inactive1, admin);

        AuthenticatedUser caller = new AuthenticatedUser(admin.getId(), condoId, null, "admin@test.local", UserRole.CONDO_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, caller.authorities())
        );

        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAllByCondominiumIdAndRoleOrderByFullNameAsc" -> allUsers.stream()
                            .filter(u -> u.getCondominiumId().equals(a[0]) && u.getRole() == a[1])
                            .toList();
                    default -> throw new UnsupportedOperationException("UserRepository." + m.getName());
                }
        );

        UnitRepository unitRepo = (UnitRepository) Proxy.newProxyInstance(
                UnitRepository.class.getClassLoader(),
                new Class<?>[]{UnitRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAllByCondominiumIdAndActiveTrueOrderByUnitNumberAsc" -> allUnits.stream()
                            .filter(u -> u.getCondominiumId().equals(a[0]) && Boolean.TRUE.equals(u.getActive()))
                            .toList();
                    default -> throw new UnsupportedOperationException("UnitRepository." + m.getName());
                }
        );

        CondominiumRepository condoRepo = (CondominiumRepository) Proxy.newProxyInstance(
                CondominiumRepository.class.getClassLoader(),
                new Class<?>[]{CondominiumRepository.class},
                (p, m, a) -> { throw new UnsupportedOperationException("CondominiumRepository." + m.getName()); }
        );

        RefreshTokenRepository refreshRepo = (RefreshTokenRepository) Proxy.newProxyInstance(
                RefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{RefreshTokenRepository.class},
                (p, m, a) -> { throw new UnsupportedOperationException("RefreshTokenRepository." + m.getName()); }
        );

        userService = new UserService(userRepo, condoRepo, unitRepo, refreshRepo, new BCryptPasswordEncoder(4), new TenantAccessGuard());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void computesSummaryMetricsAndOccupiedUnitsExcludingAdmins() {
        ResidentsSummaryResponse summary = userService.getResidentsSummary(condoId);

        assertThat(summary.totalResidents()).isEqualTo(4);
        assertThat(summary.activeResidents()).isEqualTo(2);
        assertThat(summary.blockedResidents()).isEqualTo(1);
        assertThat(summary.inactiveResidents()).isEqualTo(1);
        assertThat(summary.blockedOrInactiveResidents()).isEqualTo(2);
        assertThat(summary.occupiedUnits()).isEqualTo(1); // Only unitA has active non-blocked residents
        assertThat(summary.totalUnits()).isEqualTo(3);
    }

    private User resident(String name, UUID unitId, boolean active, boolean blocked) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setCondominiumId(condoId);
        u.setUnitId(unitId);
        u.setFullName(name);
        u.setEmail(name.toLowerCase() + "@test.local");
        u.setRole(UserRole.RESIDENT);
        u.setActive(active);
        u.setBlocked(blocked);
        return u;
    }
}
