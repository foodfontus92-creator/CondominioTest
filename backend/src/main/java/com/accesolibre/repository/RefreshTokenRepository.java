package com.accesolibre.repository;

import com.accesolibre.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :revokedAt " +
           "WHERE rt.condominiumId = :condoId AND rt.userId = :userId AND rt.revoked = false")
    int revokeAllActiveByUser(
            @Param("condoId") UUID condoId,
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt
    );
}
