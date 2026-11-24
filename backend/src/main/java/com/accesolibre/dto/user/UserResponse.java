package com.accesolibre.dto.user;

import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID condominiumId,
        UUID unitId,
        String email,
        String fullName,
        String phone,
        UserRole role,
        Boolean active,
        Boolean blocked,
        String blockReason,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User entity) {
        return new UserResponse(
                entity.getId(),
                entity.getCondominiumId(),
                entity.getUnitId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getRole(),
                entity.getActive(),
                entity.getBlocked(),
                entity.getBlockReason(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
