package com.accesolibre.dto.saas;

import com.accesolibre.domain.enums.CommunityType;

import java.time.Instant;
import java.util.UUID;

public record AdminActivationCodeResponse(
        UUID id,
        UUID condominiumId,
        String condoCode,
        String condoName,
        CommunityType communityType,
        String code,
        Instant expiresAt,
        Instant createdAt
) {
}
