package com.accesolibre.dto.setup;

import java.util.UUID;

public record SetupInitializeResponse(
        boolean initialized,
        UUID condominiumId,
        String condoCode,
        String condoName,
        UUID adminUserId,
        String adminEmail
) {
}
