package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.setup.SetupInitializeRequest;
import com.accesolibre.dto.setup.SetupInitializeResponse;
import com.accesolibre.dto.setup.SetupStatusResponse;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetupServiceTest {

    private long userCount;
    private boolean condoExists;
    private final List<Condominium> savedCondos = new ArrayList<>();
    private final List<User> savedUsers = new ArrayList<>();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private SetupService setupService;

    @BeforeEach
    void setUp() {
        userCount = 0L;
        condoExists = false;
        savedCondos.clear();
        savedUsers.clear();

        UserRepository userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "count" -> userCount;
                    case "save" -> {
                        User u = (User) args[0];
                        if (u.getId() == null) {
                            u.setId(UUID.randomUUID());
                        }
                        savedUsers.add(u);
                        userCount++;
                        yield u;
                    }
                    default -> throw new UnsupportedOperationException("Unexpected UserRepository call: " + method.getName());
                }
        );

        CondominiumRepository condominiumRepository = (CondominiumRepository) Proxy.newProxyInstance(
                CondominiumRepository.class.getClassLoader(),
                new Class<?>[]{CondominiumRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCodeIgnoreCase" -> condoExists;
                    case "findAll" -> List.copyOf(savedCondos);
                    case "save" -> {
                        Condominium c = (Condominium) args[0];
                        if (c.getId() == null) {
                            c.setId(UUID.randomUUID());
                        }
                        savedCondos.add(c);
                        yield c;
                    }
                    default -> throw new UnsupportedOperationException("Unexpected CondominiumRepository call: " + method.getName());
                }
        );

        CompactQrCryptoService qrCryptoService = new CompactQrCryptoService(new AppProperties());
        setupService = new SetupService(userRepository, condominiumRepository, qrCryptoService, passwordEncoder);
    }

    @Test
    void getStatusReturnsFalseWhenNoUsersExistAndTrueOtherwise() {
        userCount = 0L;
        SetupStatusResponse uninitialized = setupService.getStatus();
        assertThat(uninitialized.initialized()).isFalse();
        assertThat(uninitialized.defaultCommunityType()).isEqualTo(CommunityType.CONDOMINIUM);

        Condominium poblacion = new Condominium();
        poblacion.setActive(true);
        poblacion.setCommunityType(CommunityType.POBLACION);
        savedCondos.add(poblacion);
        userCount = 1L;
        SetupStatusResponse initialized = setupService.getStatus();
        assertThat(initialized.initialized()).isTrue();
        assertThat(initialized.defaultCommunityType()).isEqualTo(CommunityType.POBLACION);
    }

    @Test
    void initializeThrowsForbiddenWhenAlreadyInitialized() {
        userCount = 1L;
        SetupInitializeRequest request = new SetupInitializeRequest(
                "torre-norte",
                "Torre Norte",
                "Av. Principal 123",
                "America/Santiago",
                null,
                null,
                null,
                "Admin Principal",
                "admin@torrenorte.local",
                "SuperSecret123!",
                null
        );

        assertThatThrownBy(() -> setupService.initialize(request))
                .isInstanceOf(AccessDeniedDomainException.class)
                .satisfies(ex -> {
                    AccessDeniedDomainException apiEx = (AccessDeniedDomainException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getMessage()).isEqualTo("El sistema ya fue inicializado");
                });

        assertThat(savedCondos).isEmpty();
        assertThat(savedUsers).isEmpty();
    }

    @Test
    void initializeCreatesCondoWith64CharHexHmacAndSuperAdmin() {
        SetupInitializeRequest request = new SetupInitializeRequest(
                "Torre-Norte",
                "Población Torre Norte",
                "Av. Principal 123",
                "America/Santiago",
                -33.4489,
                -70.6693,
                200,
                "Admin Principal",
                "Admin@TorreNorte.local",
                "SuperSecret123!",
                CommunityType.POBLACION
        );

        SetupInitializeResponse response = setupService.initialize(request);

        assertThat(savedCondos).hasSize(1);
        Condominium savedCondo = savedCondos.get(0);
        assertThat(savedCondo.getCode()).isEqualTo("torre-norte");
        assertThat(savedCondo.getName()).isEqualTo("Población Torre Norte");
        assertThat(savedCondo.getCommunityType()).isEqualTo(CommunityType.POBLACION);
        assertThat(savedCondo.getAddress()).isEqualTo("Av. Principal 123");
        assertThat(savedCondo.getTimezone()).isEqualTo("America/Santiago");
        assertThat(savedCondo.getHmacSecret()).matches("^[0-9a-f]{64}$");
        assertThat(savedCondo.getHmacKeyVersion()).isEqualTo(1);
        assertThat(savedCondo.getLatitude()).isEqualTo(-33.4489);
        assertThat(savedCondo.getLongitude()).isEqualTo(-70.6693);
        assertThat(savedCondo.getGeofenceRadiusMeters()).isEqualTo(200);
        assertThat(savedCondo.getActive()).isTrue();

        assertThat(savedUsers).hasSize(1);
        User savedUser = savedUsers.get(0);
        assertThat(savedUser.getCondominiumId()).isEqualTo(savedCondo.getId());
        assertThat(savedUser.getUnitId()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("admin@torrenorte.local");
        assertThat(passwordEncoder.matches("SuperSecret123!", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getFullName()).isEqualTo("Admin Principal");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(savedUser.getActive()).isTrue();
        assertThat(savedUser.getBlocked()).isFalse();

        assertThat(response.initialized()).isTrue();
        assertThat(response.condominiumId()).isEqualTo(savedCondo.getId());
        assertThat(response.condoCode()).isEqualTo("torre-norte");
        assertThat(response.condoName()).isEqualTo("Población Torre Norte");
        assertThat(response.communityType()).isEqualTo(CommunityType.POBLACION);
        assertThat(response.adminUserId()).isEqualTo(savedUser.getId());
        assertThat(response.adminEmail()).isEqualTo("admin@torrenorte.local");
    }
}
