package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.CommunityType;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.setup.SetupInitializeRequest;
import com.accesolibre.dto.setup.SetupInitializeResponse;
import com.accesolibre.dto.setup.SetupStatusResponse;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {

    private final UserRepository userRepository;
    private final CondominiumRepository condominiumRepository;
    private final CompactQrCryptoService compactQrCryptoService;
    private final PasswordEncoder passwordEncoder;

    public SetupService(
            UserRepository userRepository,
            CondominiumRepository condominiumRepository,
            CompactQrCryptoService compactQrCryptoService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.condominiumRepository = condominiumRepository;
        this.compactQrCryptoService = compactQrCryptoService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return userRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse getStatus() {
        boolean initialized = userRepository.count() > 0;
        CommunityType defaultCommunityType = CommunityType.CONDOMINIUM;
        if (initialized) {
            defaultCommunityType = condominiumRepository.findAll().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActive()))
                    .map(c -> c.getCommunityType() != null ? c.getCommunityType() : CommunityType.CONDOMINIUM)
                    .findFirst()
                    .orElse(CommunityType.CONDOMINIUM);
        }
        return new SetupStatusResponse(initialized, defaultCommunityType);
    }

    @Transactional
    public SetupInitializeResponse initialize(SetupInitializeRequest request) {
        if (userRepository.count() > 0) {
            throw new AccessDeniedDomainException(
                    "SYSTEM_ALREADY_INITIALIZED",
                    "El sistema ya fue inicializado"
            );
        }

        String normalizedCode = request.condoCode().trim().toLowerCase();
        String normalizedEmail = request.adminEmail().trim().toLowerCase();

        if (condominiumRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new ConflictException("Condominium code already exists: " + normalizedCode);
        }

        String generatedSecret = compactQrCryptoService.generateRandomHmacSecretHex();

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BadRequestException("Debes enviar latitud y longitud juntas");
        }

        Condominium condo = new Condominium();
        condo.setCode(normalizedCode);
        condo.setName(request.condoName().trim());
        condo.setCommunityType(request.communityType() != null ? request.communityType() : CommunityType.CONDOMINIUM);
        condo.setAddress(request.address() != null && !request.address().isBlank() ? request.address().trim() : null);
        condo.setTimezone(request.timezone() != null && !request.timezone().isBlank() ? request.timezone().trim() : "UTC");
        condo.setHmacSecret(generatedSecret);
        condo.setHmacKeyVersion(1);
        condo.setLatitude(request.latitude());
        condo.setLongitude(request.longitude());
        condo.setGeofenceRadiusMeters(request.geofenceRadiusMeters() != null ? request.geofenceRadiusMeters() : 150);
        condo.setActive(true);
        Condominium savedCondo = condominiumRepository.save(condo);

        User admin = new User();
        admin.setCondominiumId(savedCondo.getId());
        admin.setUnitId(null);
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        admin.setFullName(request.adminFullName().trim());
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setActive(true);
        admin.setBlocked(false);
        User savedAdmin = userRepository.save(admin);

        return new SetupInitializeResponse(
                true,
                savedCondo.getId(),
                savedCondo.getCode(),
                savedCondo.getName(),
                savedCondo.getCommunityType(),
                savedAdmin.getId(),
                savedAdmin.getEmail()
        );
    }
}
