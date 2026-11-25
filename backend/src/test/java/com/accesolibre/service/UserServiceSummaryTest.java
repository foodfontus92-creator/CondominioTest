package com.accesolibre.service;

import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.user.CreateUserRequest;
import com.accesolibre.dto.user.ResidentsSummaryResponse;
import com.accesolibre.dto.user.UpdateUserRequest;
import com.accesolibre.dto.user.UserResponse;
import com.accesolibre.exception.ForbiddenTenantException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceSummaryTest {

    private UUID condoId;
    private UUID unitA;
    private UUID unitB;
    private UUID unitC;
    private UserService userService;
    private List<User> allUsers;
    private List<Unit> allUnits;
    private User superAdmin;
    private User condoAdmin;

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

        condoAdmin = new User();
        condoAdmin.setId(UUID.randomUUID());
        condoAdmin.setCondominiumId(condoId);
        condoAdmin.setFullName("Admin Condominio");
        condoAdmin.setEmail("admin@test.local");
        condoAdmin.setRole(UserRole.CONDO_ADMIN);
        condoAdmin.setActive(true);
        condoAdmin.setBlocked(false);

        superAdmin = new User();
        superAdmin.setId(UUID.randomUUID());
        superAdmin.setCondominiumId(condoId);
        superAdmin.setFullName("Super Admin Principal");
        superAdmin.setEmail("superadmin@test.local");
        superAdmin.setRole(UserRole.SUPER_ADMIN);
        superAdmin.setActive(true);
        superAdmin.setBlocked(false);

        allUsers = new ArrayList<>(List.of(active1, active2, blocked1, inactive1, condoAdmin, superAdmin));

        setCaller(condoAdmin);

        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAllByCondominiumIdOrderByFullNameAsc" -> allUsers.stream()
                            .filter(u -> u.getCondominiumId().equals(a[0]))
                            .toList();
                    case "findAllByCondominiumIdAndRoleOrderByFullNameAsc" -> allUsers.stream()
                            .filter(u -> u.getCondominiumId().equals(a[0]) && u.getRole() == a[1])
                            .toList();
                    case "findByIdAndCondominiumId" -> allUsers.stream()
                            .filter(u -> u.getId().equals(a[0]) && u.getCondominiumId().equals(a[1]))
                            .findFirst();
                    case "existsByCondominiumIdAndEmailIgnoreCase" -> allUsers.stream()
                            .anyMatch(u -> u.getCondominiumId().equals(a[0])
                                    && u.getEmail().equalsIgnoreCase((String) a[1]));
                    case "save" -> {
                        User u = (User) a[0];
                        if (u.getId() == null) {
                            u.setId(UUID.randomUUID());
                        }
                        yield u;
                    }
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
                (p, m, a) -> switch (m.getName()) {
                    case "existsById" -> condoId.equals(a[0]);
                    default -> throw new UnsupportedOperationException("CondominiumRepository." + m.getName());
                }
        );

        RefreshTokenRepository refreshRepo = (RefreshTokenRepository) Proxy.newProxyInstance(
                RefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{RefreshTokenRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "revokeAllActiveByUser" -> 0;
                    default -> throw new UnsupportedOperationException("RefreshTokenRepository." + m.getName());
                }
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

    @Test
    void condoAdminCannotEditBlockDeactivateOrCreateSuperAdmin() {
        setCaller(condoAdmin);

        assertThatThrownBy(() -> userService.updateUser(
                condoId,
                superAdmin.getId(),
                new UpdateUserRequest(null, "Nombre Alterado", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenTenantException.class);

        assertThatThrownBy(() -> userService.updateUser(
                condoId,
                superAdmin.getId(),
                new UpdateUserRequest(null, null, null, null, null, true, "Bloqueo indebido", null)
        )).isInstanceOf(ForbiddenTenantException.class);

        assertThatThrownBy(() -> userService.updateUser(
                condoId,
                superAdmin.getId(),
                new UpdateUserRequest(null, null, null, null, false, null, null, null)
        )).isInstanceOf(ForbiddenTenantException.class);

        assertThatThrownBy(() -> userService.createUser(
                condoId,
                new CreateUserRequest(null, "otro@test.local", "SuperSecret123", "Otro Super", null, UserRole.SUPER_ADMIN)
        )).isInstanceOf(ForbiddenTenantException.class);

        assertThat(superAdmin.getFullName()).isEqualTo("Super Admin Principal");
        assertThat(superAdmin.getActive()).isTrue();
        assertThat(superAdmin.getBlocked()).isFalse();
    }

    @Test
    void superAdminCanUpdateSuperAdmin() {
        setCaller(superAdmin);

        UserResponse updated = userService.updateUser(
                condoId,
                superAdmin.getId(),
                new UpdateUserRequest(null, "Super Admin Actualizado", null, null, null, null, null, null)
        );
        assertThat(updated.fullName()).isEqualTo("Super Admin Actualizado");
    }

    @Test
    void listUsersHidesSuperAdminFromCondoAdmin() {
        setCaller(condoAdmin);
        List<UserResponse> asCondoAdmin = userService.listUsers(condoId);
        assertThat(asCondoAdmin).noneMatch(u -> u.role() == UserRole.SUPER_ADMIN);
        assertThat(asCondoAdmin).anyMatch(u -> u.role() == UserRole.CONDO_ADMIN);

        setCaller(superAdmin);
        List<UserResponse> asSuperAdmin = userService.listUsers(condoId);
        assertThat(asSuperAdmin).anyMatch(u -> u.role() == UserRole.SUPER_ADMIN);
    }

    private void setCaller(User user) {
        AuthenticatedUser caller = new AuthenticatedUser(
                user.getId(), condoId, user.getUnitId(), user.getEmail(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, caller.authorities())
        );
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
