package com.accesolibre.dto.setup;

import com.accesolibre.domain.enums.CommunityType;

import java.util.UUID;

public record SetupInitializeResponse(
        boolean initialized,
        UUID condominiumId,
        String condoCode,
        String condoName,
        CommunityType communityType,
        UUID adminUserId,
        String adminEmail
) {
}
