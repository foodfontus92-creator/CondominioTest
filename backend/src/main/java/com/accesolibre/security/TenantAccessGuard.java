package com.accesolibre.security;

import com.accesolibre.exception.ForbiddenTenantException;
import com.accesolibre.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantAccessGuard {

    public AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("Authentication required");
        }
        return user;
    }

    public AuthenticatedUser requireCondoAccess(UUID targetCondominiumId) {
        AuthenticatedUser user = currentUser();
        if (user.isSuperAdmin()) {
            return user;
        }
        if (targetCondominiumId == null || !targetCondominiumId.equals(user.condominiumId())) {
            throw new ForbiddenTenantException("Cross-tenant access denied for condominium " + targetCondominiumId);
        }
        return user;
    }

    public AuthenticatedUser requireCondoAdmin(UUID targetCondominiumId) {
        AuthenticatedUser user = requireCondoAccess(targetCondominiumId);
        if (!user.isSuperAdmin() && !user.isCondoAdmin()) {
            throw new ForbiddenTenantException("Condominium administrator role required");
        }
        return user;
    }

    public AuthenticatedUser requireSuperAdmin() {
        AuthenticatedUser user = currentUser();
        if (!user.isSuperAdmin()) {
            throw new ForbiddenTenantException("Super administrator role required");
        }
        return user;
    }

    public AuthenticatedUser requireUnitAccess(UUID targetCondominiumId, UUID targetUnitId) {
        AuthenticatedUser user = requireCondoAccess(targetCondominiumId);
        if (user.isSuperAdmin() || user.isCondoAdmin()) {
            return user;
        }
        if (user.unitId() == null || !user.unitId().equals(targetUnitId)) {
            throw new ForbiddenTenantException("Access denied for unit " + targetUnitId);
        }
        return user;
    }
}
