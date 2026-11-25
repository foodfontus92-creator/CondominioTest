package com.accesolibre.service;

import com.accesolibre.domain.entity.Unit;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.dto.user.CreateUserRequest;
import com.accesolibre.dto.user.ResidentsSummaryResponse;
import com.accesolibre.dto.user.UpdateUserRequest;
import com.accesolibre.dto.user.UserResponse;
import com.accesolibre.exception.BadRequestException;
import com.accesolibre.exception.ConflictException;
import com.accesolibre.exception.ForbiddenTenantException;
import com.accesolibre.exception.ResourceNotFoundException;
import com.accesolibre.repository.CondominiumRepository;
import com.accesolibre.repository.RefreshTokenRepository;
import com.accesolibre.repository.UnitRepository;
import com.accesolibre.repository.UserRepository;
import com.accesolibre.security.AuthenticatedUser;
import com.accesolibre.security.TenantAccessGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAccessGuard tenantAccessGuard;

    public UserService(
            UserRepository userRepository,
            CondominiumRepository condominiumRepository,
            UnitRepository unitRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            TenantAccessGuard tenantAccessGuard
    ) {
        this.userRepository = userRepository;
        this.condominiumRepository = condominiumRepository;
        this.unitRepository = unitRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID condoId) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        return userRepository.findAllByCondominiumIdOrderByFullNameAsc(condoId).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResidentsSummaryResponse getResidentsSummary(UUID condoId) {
        tenantAccessGuard.requireCondoAdmin(condoId);
        List<User> residents = userRepository.findAllByCondominiumIdAndRoleOrderByFullNameAsc(condoId, UserRole.RESIDENT);
        List<Unit> activeUnits = unitRepository.findAllByCondominiumIdAndActiveTrueOrderByUnitNumberAsc(condoId);
        Set<UUID> activeUnitIds = activeUnits.stream()
                .map(Unit::getId)
                .collect(Collectors.toSet());

        long total = residents.size();
        long active = residents.stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()) && !Boolean.TRUE.equals(u.getBlocked()))
                .count();
        long blocked = residents.stream()
                .filter(u -> Boolean.TRUE.equals(u.getBlocked()))
                .count();
        long inactive = residents.stream()
                .filter(u -> !Boolean.TRUE.equals(u.getActive()))
                .count();
        long blockedOrInactive = total - active;
        long occupiedUnits = residents.stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive())
                        && !Boolean.TRUE.equals(u.getBlocked())
                        && u.getUnitId() != null
                        && activeUnitIds.contains(u.getUnitId()))
                .map(User::getUnitId)
                .distinct()
                .count();

        return new ResidentsSummaryResponse(
                total,
                active,
                blocked,
                inactive,
                blockedOrInactive,
                occupiedUnits,
                activeUnits.size()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID condoId, UUID userId) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAccess(condoId);
        if (caller.isResident() && !caller.userId().equals(userId)) {
            throw new ForbiddenTenantException("Residents may only access their own user record");
        }
        User user = userRepository.findByIdAndCondominiumId(userId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(UUID condoId, CreateUserRequest request) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAdmin(condoId);
        if (request.role() == UserRole.SUPER_ADMIN && !caller.isSuperAdmin()) {
            throw new ForbiddenTenantException("Only SUPER_ADMIN can create SUPER_ADMIN accounts");
        }
        if (!condominiumRepository.existsById(condoId)) {
            throw new ResourceNotFoundException("Condominium not found: " + condoId);
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByCondominiumIdAndEmailIgnoreCase(condoId, normalizedEmail)) {
            throw new ConflictException("User email already exists in this condominium: " + normalizedEmail);
        }

        if (request.role() == UserRole.RESIDENT && request.unitId() == null) {
            throw new BadRequestException("unitId is required for RESIDENT users");
        }
        if (request.unitId() != null) {
            unitRepository.findByIdAndCondominiumId(request.unitId(), condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Unit not found in condominium: " + request.unitId()));
        }

        User user = new User();
        user.setCondominiumId(condoId);
        user.setUnitId(request.unitId());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(true);
        user.setBlocked(false);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID condoId, UUID userId, UpdateUserRequest request) {
        AuthenticatedUser caller = tenantAccessGuard.requireCondoAdmin(condoId);
        if (caller.userId().equals(userId)
                && (Boolean.TRUE.equals(request.blocked()) || Boolean.FALSE.equals(request.active()))) {
            throw new BadRequestException("No puedes bloquear ni desactivar tu propia cuenta");
        }
        User user = userRepository.findByIdAndCondominiumId(userId, condoId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.role() != null) {
            if (request.role() == UserRole.SUPER_ADMIN && !caller.isSuperAdmin()) {
                throw new ForbiddenTenantException("Only SUPER_ADMIN can assign SUPER_ADMIN role");
            }
            user.setRole(request.role());
        }
        if (request.unitId() != null) {
            unitRepository.findByIdAndCondominiumId(request.unitId(), condoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Unit not found in condominium: " + request.unitId()));
            user.setUnitId(request.unitId());
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            refreshTokenRepository.revokeAllActiveByUser(condoId, userId, Instant.now());
        }
        if (request.active() != null) {
            user.setActive(request.active());
            if (!request.active()) {
                refreshTokenRepository.revokeAllActiveByUser(condoId, userId, Instant.now());
            }
        }
        if (request.blocked() != null) {
            user.setBlocked(request.blocked());
            if (request.blocked()) {
                refreshTokenRepository.revokeAllActiveByUser(condoId, userId, Instant.now());
            } else {
                user.setBlockReason(null);
            }
        }
        if (request.blockReason() != null) {
            user.setBlockReason(request.blockReason());
        }

        return UserResponse.from(userRepository.save(user));
    }
}
