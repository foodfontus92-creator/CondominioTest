package com.accesolibre.dto.user;

import com.accesolibre.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        UUID unitId,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @Size(max = 32)
        String phone,

        @NotNull
        UserRole role
) {
}
