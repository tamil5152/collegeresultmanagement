package com.college.result.dto;

import com.college.result.common.Patterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "Username is required") @Size(max = 50) String username,
            @NotBlank(message = "Password is required") @Size(max = 100) String password) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank @Pattern(regexp = Patterns.PASSWORD, message = Patterns.PASSWORD_MESSAGE) String newPassword) {}

    public record SessionUser(long userId, String username, String fullName, String role) {}

    /** Returned by login and /auth/me. csrfToken must be echoed in the X-CSRF-Token header for unsafe requests. */
    public record SessionInfo(SessionUser user, String csrfToken) {}
}
