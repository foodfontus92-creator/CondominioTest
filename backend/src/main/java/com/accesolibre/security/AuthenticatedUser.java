package com.accesolibre.security;

import com.accesolibre.domain.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID condominiumId,
        UUID unitId,
        String email,
        UserRole role
) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isSuperAdmin() {
        return role == UserRole.SUPER_ADMIN;
    }

    public boolean isCondoAdmin() {
        return role == UserRole.CONDO_ADMIN;
    }

    public boolean isResident() {
        return role == UserRole.RESIDENT;
    }
}
