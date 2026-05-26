package in.schoolapp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Slice 35 — password-based login. Exactly one of {@code phone} or {@code email} is required
 * (validated in {@link in.schoolapp.auth.AuthService#resolveIdentifier}).
 */
public record PasswordLoginRequest(
    String phone,
    String email,
    @NotBlank String password
) {}
