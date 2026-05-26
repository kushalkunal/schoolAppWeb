package in.schoolapp.auth.dto;

import in.schoolapp.school.dto.StaffResponse;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    StaffResponse user
) {}
