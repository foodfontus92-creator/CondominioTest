package com.accesolibre.service;

import com.accesolibre.domain.entity.Condominium;
import com.accesolibre.domain.entity.RefreshToken;
import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.UnitActivationCode;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.auth.ActivateUnitRequest;
import com.accesolibre.dto.auth.AuthResponse;
import com.accesolibre.dto.auth.LoginRequest;
import com.accesolibre.dto.auth.LogoutRequest;
import com.accesolibre.dto.auth.RefreshTokenRequest;
import com.accesolibre.exception.AccessDeniedDomainException;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.UnauthorizedException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.RefreshTokenRepository;
import com.accesolibre.repository.UnitActivationCodeRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.JwtService;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final UnitActivationCodeRepository activationCodeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantAccessGuard tenantAccessGuard;

    public AuthService(
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            UnitActivationCodeRepository activationCodeRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.activationCodeRepository = activationCodeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Condominium condo = condominiumRepository.findByCodeIgnoreCase(request.condominiumCode().trim())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        User user = userRepository.findByCondominiumIdAndEmailIgnoreCase(condo.getId(), request.email().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new UnauthorizedException("User account is inactive");
        }
        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new UnauthorizedException("User account is blocked");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse activateUnit(ActivateUnitRequest request) {
        String normalizedCode = request.activationCode().trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();

        UnitActivationCode codeEntity = activationCodeRepository.findByCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new BadRequestException("Código de activación inválido"));

        if (!Boolean.TRUE.equals(codeEntity.getActive())) {
            throw new BadRequestException("Este código de activación ya no está vigente");
        }
        if (!codeEntity.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("Este código de activación ha expirado");
        }
        if (codeEntity.getUsedCount() >= codeEntity.getMaxUses()) {
            throw new BadRequestException("Este código de activación ya alcanzó el límite de integrantes");
        }

        Condominium condo = condominiumRepository.findById(codeEntity.getCondominiumId())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new BadRequestException("El condominio asociado al código no está activo"));

        Unit unit = unitRepository.findByIdAndCondominiumId(codeEntity.getUnitId(), condo.getId())
                .orElseThrow(() -> new BadRequestException("El departamento asociado al código no existe"));

        if (!Boolean.TRUE.equals(unit.getActive()) || Boolean.TRUE.equals(unit.getBlocked())) {
            throw new AccessDeniedDomainException(
                    "DENIED_UNIT_BLOCKED",
                    "El departamento se encuentra inactivo o bloqueado"
            );
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByCondominiumIdAndEmailIgnoreCase(condo.getId(), normalizedEmail)) {
            throw new ConflictException("Ya existe una cuenta registrada con ese correo en este condominio");
        }

        User resident = new User();
        resident.setCondominiumId(condo.getId());
        resident.setUnitId(unit.getId());
        resident.setEmail(normalizedEmail);
        resident.setPasswordHash(passwordEncoder.encode(request.password()));
        resident.setFullName(request.fullName().trim());
        resident.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        resident.setRole(UserRole.RESIDENT);
        resident.setActive(true);
        resident.setBlocked(false);
        resident.setLastLoginAt(now);
        User savedResident = userRepository.save(resident);

        int nextUsed = codeEntity.getUsedCount() + 1;
        codeEntity.setUsedCount(nextUsed);
        if (nextUsed >= codeEntity.getMaxUses()) {
            codeEntity.setActive(false);
        }
        activationCodeRepository.save(codeEntity);

        return issueTokenPair(savedResident);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawRefreshToken = request.refreshToken().trim();
        AuthenticatedUser claims = jwtService.parseRefreshToken(rawRefreshToken);
        String tokenHash = jwtService.sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not recognized"));

        Instant now = Instant.now();
        if (Boolean.TRUE.equals(stored.getRevoked()) || stored.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        if (!stored.getCondominiumId().equals(claims.condominiumId())
                || !stored.getUserId().equals(claims.userId())) {
            throw new UnauthorizedException("Refresh token tenant mismatch");
        }

        User user = userRepository.findByIdAndCondominiumId(claims.userId(), claims.condominiumId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getBlocked())) {
            stored.setRevoked(true);
            stored.setRevokedAt(now);
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("User account is inactive or blocked");
        }

        // Rotate refresh token: revoke previous and issue new pair
        stored.setRevoked(true);
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        return issueTokenPair(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = jwtService.sha256Hex(request.refreshToken().trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(stored -> {
            if (!Boolean.TRUE.equals(stored.getRevoked())) {
                stored.setRevoked(true);
                stored.setRevokedAt(Instant.now());
                refreshTokenRepository.save(stored);
            }
        });
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserProfile currentProfile() {
        AuthenticatedUser principal = tenantAccessGuard.currentUser();
        User user = userRepository.findByIdAndCondominiumId(principal.userId(), principal.condominiumId())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
        return toUserProfile(user);
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenRaw = jwtService.generateRefreshToken(user);
        Instant refreshExpiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds());

        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setCondominiumId(user.getCondominiumId());
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(jwtService.sha256Hex(refreshTokenRaw));
        tokenEntity.setExpiresAt(refreshExpiresAt);
        tokenEntity.setRevoked(false);
        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse(
                "Bearer",
                accessToken,
                jwtService.getAccessTokenTtlSeconds(),
                refreshTokenRaw,
                jwtService.getRefreshTokenTtlSeconds(),
                toUserProfile(user)
        );
    }

    private AuthResponse.UserProfile toUserProfile(User user) {
        return new AuthResponse.UserProfile(
                user.getId(),
                user.getCondominiumId(),
                user.getUnitId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}
