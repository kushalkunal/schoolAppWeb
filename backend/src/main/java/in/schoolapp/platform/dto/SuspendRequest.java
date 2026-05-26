package in.schoolapp.platform.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /platform/tenants/{id}/suspend}. */
public record SuspendRequest(
    @NotBlank String reason
) {}
