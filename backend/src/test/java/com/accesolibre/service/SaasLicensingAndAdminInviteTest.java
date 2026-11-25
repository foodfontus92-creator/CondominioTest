package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.AccessLog;
import com.accesolibre.domain.entity.AccessPass;
import com.accesolibre.domain.entity.AdminActivationCode;
import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.IotDevice;
import com.accesolibre.domain.entity.RefreshToken;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.DeviceType;
import com.accesolibre.domain.enums.PassType;
import com.accesolibre.domain.enums.SubscriptionStatus;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.access.RemoteOpenRequest;
import com.accesolibre.dto.auth.ActivateAdminRequest;
import com.accesolibre.dto.auth.AuthResponse;
import com.accesolibre.dto.condo.CondominiumResponse;
import com.accesolibre.dto.qr.CreateAccessPassRequest;
import com.accesolibre.dto.saas.AdminActivationCodeResponse;
import com.accesolibre.dto.saas.SaasOverviewResponse;
import com.accesolibre.dto.saas.UpdateCondoLicenseRequest;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.LicenseSuspendedException;
import com.accesolibre.mqtt.MqttGatewayService;
import com.accesolibre.repository.AccessLogRepository;
import com.accesolibre.repository.AccessPassRepository;
import com.accesolibre.repository.AdminActivationCodeRepository;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.IotDeviceRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaasLicensingAndAdminInviteTest {

    private UUID condoActiveId;
    private UUID condoExpiringId;
    private UUID condoSuspendedId;
    private UUID unitId;
    private UUID deviceId;
    private UUID residentId;

    private final Map<UUID, Condominium> condos = new LinkedHashMap<>();
    private final Map<UUID, Unit> units = new LinkedHashMap<>();
    private final Map<UUID, User> users = new LinkedHashMap<>();
    private final Map<UUID, IotDevice> devices = new LinkedHashMap<>();
    private final Map<UUID, AdminActivationCode> adminCodes = new LinkedHashMap<>();
    private final List<AccessLog> logs = new ArrayList<>();

    private SaasAdminService saasAdminService;
    private AuthService authService;
    private AccessControlService accessControlService;
    private AccessPassService accessPassService;

    @BeforeEach
    void setUp() {
        condoActiveId = UUID.randomUUID();
        condoExpiringId = UUID.randomUUID();
        condoSuspendedId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        residentId = UUID.randomUUID();

        Condominium c1 = condo(condoActiveId, "villa-sol", "Villa El Sol", SubscriptionStatus.ACTIVE, null);
        Condominium c2 = condo(condoExpiringId, "pob-norte", "Población Norte", SubscriptionStatus.ACTIVE, Instant.now().plus(3, ChronoUnit.DAYS));
        Condominium c3 = condo(condoSuspendedId, "condo-sur", "Condominio Sur", SubscriptionStatus.SUSPENDED, Instant.now().minus(2, ChronoUnit.DAYS));
        condos.put(condoActiveId, c1);
        condos.put(condoExpiringId, c2);
        condos.put(condoSuspendedId, c3);

        Unit u = new Unit();
        u.setId(unitId);
        u.setCondominiumId(condoSuspendedId);
        u.setUnitNumber("101");
        u.setUnitSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        u.setActive(true);
        u.setBlocked(false);
        units.put(unitId, u);

        IotDevice d = new IotDevice();
        d.setId(deviceId);
        d.setCondominiumId(condoSuspendedId);
        d.setName("Portón Principal");
        d.setDeviceType(DeviceType.VEHICLE_BARRIER);
        d.setRelayPulseMs(1000);
        d.setActive(true);
        devices.put(deviceId, d);

        User resident = new User();
        resident.setId(residentId);
        resident.setCondominiumId(condoSuspendedId);
        resident.setUnitId(unitId);
        resident.setEmail("vecino@sur.local");
        resident.setFullName("Vecino Sur");
        resident.setRole(UserRole.RESIDENT);
        resident.setActive(true);
        resident.setBlocked(false);
        users.put(residentId, resident);

        CondominiumRepository condoRepo = (CondominiumRepository) Proxy.newProxyInstance(
                CondominiumRepository.class.getClassLoader(),
                new Class<?>[]{CondominiumRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAll" -> new ArrayList<>(condos.values());
                    case "findById" -> Optional.ofNullable(condos.get((UUID) a[0]));
                    case "save" -> {
                        Condominium saved = (Condominium) a[0];
                        condos.put(saved.getId(), saved);
                        yield saved;
                    }
                    default -> throw new UnsupportedOperationException("CondominiumRepository." + m.getName());
                }
        );

        UnitRepository unitRepo = (UnitRepository) Proxy.newProxyInstance(
                UnitRepository.class.getClassLoader(),
                new Class<?>[]{UnitRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAll" -> new ArrayList<>(units.values());
                    case "findByIdAndCondominiumId" -> {
                        Unit found = units.get((UUID) a[0]);
                        yield (found != null && found.getCondominiumId().equals(a[1])) ? Optional.of(found) : Optional.empty();
                    }
                    default -> throw new UnsupportedOperationException("UnitRepository." + m.getName());
                }
        );

        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAll" -> new ArrayList<>(users.values());
                    case "findByIdAndCondominiumId" -> {
                        User found = users.get((UUID) a[0]);
                        yield (found != null && found.getCondominiumId().equals(a[1])) ? Optional.of(found) : Optional.empty();
                    }
                    case "existsByCondominiumIdAndEmailIgnoreCase" -> users.values().stream()
                            .anyMatch(usr -> usr.getCondominiumId().equals(a[0]) && usr.getEmail().equalsIgnoreCase((String) a[1]));
                    case "save" -> {
                        User usr = (User) a[0];
                        if (usr.getId() == null) usr.setId(UUID.randomUUID());
                        users.put(usr.getId(), usr);
                        yield usr;
                    }
                    default -> throw new UnsupportedOperationException("UserRepository." + m.getName());
                }
        );

        AdminActivationCodeRepository adminCodeRepo = (AdminActivationCodeRepository) Proxy.newProxyInstance(
                AdminActivationCodeRepository.class.getClassLoader(),
                new Class<?>[]{AdminActivationCodeRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "existsByCodeIgnoreCase" -> adminCodes.values().stream()
                            .anyMatch(c -> c.getCode().equalsIgnoreCase((String) a[0]));
                    case "findByCodeForUpdate" -> adminCodes.values().stream()
                            .filter(c -> c.getCode().equalsIgnoreCase((String) a[0]))
                            .findFirst();
                    case "save" -> {
                        AdminActivationCode c = (AdminActivationCode) a[0];
                        if (c.getId() == null) c.setId(UUID.randomUUID());
                        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
                        adminCodes.put(c.getId(), c);
                        yield c;
                    }
                    default -> throw new UnsupportedOperationException("AdminActivationCodeRepository." + m.getName());
                }
        );

        IotDeviceRepository deviceRepo = (IotDeviceRepository) Proxy.newProxyInstance(
                IotDeviceRepository.class.getClassLoader(),
                new Class<?>[]{IotDeviceRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findByIdAndCondominiumId" -> {
                        IotDevice found = devices.get((UUID) a[0]);
                        yield (found != null && found.getCondominiumId().equals(a[1])) ? Optional.of(found) : Optional.empty();
                    }
                    default -> throw new UnsupportedOperationException("IotDeviceRepository." + m.getName());
                }
        );

        AccessLogRepository logRepo = (AccessLogRepository) Proxy.newProxyInstance(
                AccessLogRepository.class.getClassLoader(),
                new Class<?>[]{AccessLogRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "save" -> {
                        AccessLog log = (AccessLog) a[0];
                        if (log.getId() == null) log.setId(UUID.randomUUID());
                        logs.add(log);
                        yield log;
                    }
                    default -> throw new UnsupportedOperationException("AccessLogRepository." + m.getName());
                }
        );

        AccessPassRepository passRepo = (AccessPassRepository) Proxy.newProxyInstance(
                AccessPassRepository.class.getClassLoader(),
                new Class<?>[]{AccessPassRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "save" -> (AccessPass) a[0];
                    default -> throw new UnsupportedOperationException("AccessPassRepository." + m.getName());
                }
        );

        RefreshTokenRepository refreshRepo = (RefreshTokenRepository) Proxy.newProxyInstance(
                RefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{RefreshTokenRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "save" -> (RefreshToken) a[0];
                    default -> throw new UnsupportedOperationException("RefreshTokenRepository." + m.getName());
                }
        );

        UnitActivationCodeRepository unitCodeRepo = (UnitActivationCodeRepository) Proxy.newProxyInstance(
                UnitActivationCodeRepository.class.getClassLoader(),
                new Class<?>[]{UnitActivationCodeRepository.class},
                (p, m, a) -> Optional.empty()
        );

        AppProperties props = new AppProperties();
        props.getSecurity().setJwtSecret("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        TenantAccessGuard guard = new TenantAccessGuard();
        CompactQrCryptoService qrCryptoService = new CompactQrCryptoService(props);

        saasAdminService = new SaasAdminService(condoRepo, unitRepo, userRepo, adminCodeRepo, guard);
        authService = new AuthService(condoRepo, unitRepo, unitCodeRepo, adminCodeRepo, userRepo, refreshRepo, new BCryptPasswordEncoder(4), new JwtService(props), guard);
        accessControlService = new AccessControlService(condoRepo, userRepo, unitRepo, deviceRepo, logRepo, new GeofenceService(), (MqttGatewayService) null, guard);
        accessPassService = new AccessPassService(passRepo, logRepo, condoRepo, unitRepo, userRepo, deviceRepo, qrCryptoService, null, guard, props);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminGeneratesCodeAndAdminSelfRegistersOnce() {
        setCaller(UUID.randomUUID(), condoActiveId, null, UserRole.SUPER_ADMIN);
        AdminActivationCodeResponse generated = saasAdminService.generateAdminCode(condoActiveId);

        assertThat(generated.code()).matches("^ADM-\\d{4}(\\d{2})?$");
        assertThat(generated.expiresAt()).isAfter(Instant.now().plus(70, ChronoUnit.HOURS));

        SecurityContextHolder.clearContext();
        AuthResponse activated = authService.activateAdmin(new ActivateAdminRequest(
                generated.code().toLowerCase(),
                "Camila Rojas",
                "camila@villasol.local",
                "+56988887777",
                "ClaveAdmin123"
        ));

        assertThat(activated.accessToken()).isNotBlank();
        assertThat(activated.user().role()).isEqualTo(UserRole.CONDO_ADMIN);
        assertThat(activated.user().condominiumId()).isEqualTo(condoActiveId);
        assertThat(activated.communityType()).isEqualTo(CommunityType.POBLACION);

        assertThatThrownBy(() -> authService.activateAdmin(new ActivateAdminRequest(
                generated.code(),
                "Intruso",
                "otro@villasol.local",
                null,
                "ClaveAdmin123"
        ))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void saasOverviewAndQuickRenewCalculateAccurateCounts() {
        setCaller(UUID.randomUUID(), condoActiveId, null, UserRole.SUPER_ADMIN);

        SaasOverviewResponse overview = saasAdminService.getOverview();
        assertThat(overview.totalCondos()).isEqualTo(3);
        assertThat(overview.activeCondos()).isEqualTo(2);
        assertThat(overview.expiringSoonCondos()).isEqualTo(1);
        assertThat(overview.expiredOrSuspendedCondos()).isEqualTo(1);
        assertThat(overview.totalUnits()).isEqualTo(1);
        assertThat(overview.totalResidents()).isEqualTo(1);

        CondominiumResponse renewed = saasAdminService.updateLicense(
                condoSuspendedId,
                new UpdateCondoLicenseRequest(null, "Mensual", null, null, null, "EXTEND_30_DAYS")
        );
        assertThat(renewed.licenseActive()).isTrue();
        assertThat(renewed.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(renewed.licenseExpiresAt()).isAfter(Instant.now().plus(28, ChronoUnit.DAYS));
    }

    @Test
    void suspendedOrExpiredLicenseRejectsOpenAndPassCreationWith402() {
        setCaller(residentId, condoSuspendedId, unitId, UserRole.RESIDENT);

        assertThatThrownBy(() -> accessControlService.remoteOpen(
                new RemoteOpenRequest(deviceId, -33.44, -70.66, 1000)
        )).isInstanceOf(LicenseSuspendedException.class)
                .satisfies(ex -> assertThat(((LicenseSuspendedException) ex).getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        assertThatThrownBy(() -> accessPassService.createPass(
                condoSuspendedId,
                new CreateAccessPassRequest(unitId, "Visita", null, PassType.VISITOR, null, Instant.now().plus(2, ChronoUnit.HOURS), 1)
        )).isInstanceOf(LicenseSuspendedException.class);

        assertThat(logs).hasSize(1);
    }

    private Condominium condo(UUID id, String code, String name, SubscriptionStatus status, Instant expiresAt) {
        Condominium c = new Condominium();
        c.setId(id);
        c.setCode(code);
        c.setName(name);
        c.setCommunityType(CommunityType.POBLACION);
        c.setSubscriptionStatus(status);
        c.setPlanName("Mensual");
        c.setLicenseExpiresAt(expiresAt);
        c.setMaxUnits(200);
        c.setLatitude(-33.44);
        c.setLongitude(-70.66);
        c.setGeofenceRadiusMeters(150);
        c.setActive(true);
        return c;
    }

    private void setCaller(UUID userId, UUID condoId, UUID uId, UserRole role) {
        AuthenticatedUser caller = new AuthenticatedUser(userId, condoId, uId, "caller@test.local", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, caller.authorities())
        );
    }
}
