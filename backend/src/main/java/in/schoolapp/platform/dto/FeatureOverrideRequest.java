package in.schoolapp.platform.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /platform/tenants/{id}/features/{featureKey}}. */
public record FeatureOverrideRequest(
    @NotNull Boolean enabled,
    String note
) {}
