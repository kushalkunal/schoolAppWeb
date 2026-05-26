package in.schoolapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Slice 35 — set or change password. The caller must hold a valid JWT (typically just-issued
 * from OTP verification) and have a verified identifier — both enforced in the service.
 */
public record SetPasswordRequest(
    @NotBlank @Size(min = 8, max = 100) String password
) {}
