package com.accesolibre.dto.user;

import com.accesolibre.domain.enums.UserRole;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRequest(
        UUID unitId,

        @Size(max = 150)
        String fullName,

        @Size(max = 32)
        String phone,

        UserRole role,

        Boolean active,

        Boolean blocked,

        @Size(max = 255)
        String blockReason,

        @Size(min = 8, max = 128)
        String newPassword
) {
}
