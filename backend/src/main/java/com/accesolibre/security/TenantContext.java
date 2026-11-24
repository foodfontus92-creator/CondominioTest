package com.accesolibre.security;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_CONDO = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCondominiumId(UUID condominiumId) {
        CURRENT_CONDO.set(condominiumId);
    }

    public static UUID getCondominiumId() {
        return CURRENT_CONDO.get();
    }

    public static void clear() {
        CURRENT_CONDO.remove();
    }
}
