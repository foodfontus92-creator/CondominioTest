package com.accesolibre.dto.auth;

import com.accesolibre.domain.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        long refreshTokenExpiresInSeconds,
        UserProfile user
) {
    public record UserProfile(
            UUID id,
            UUID condominiumId,
            UUID unitId,
            String email,
            String fullName,
            UserRole role
    ) {
    }
}
