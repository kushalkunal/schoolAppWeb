package in.schoolapp.platform.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PUT /platform/tenants/{id}/subscription}. */
public record ChangePlanRequest(
    @NotBlank String planCode,
    String note
) {}
