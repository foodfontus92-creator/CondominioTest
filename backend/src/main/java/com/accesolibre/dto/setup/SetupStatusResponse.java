package com.accesolibre.dto.setup;

import com.accesolibre.domain.enums.CommunityType;

public record SetupStatusResponse(
        boolean initialized,
        CommunityType defaultCommunityType
) {
}
