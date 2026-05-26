package in.schoolapp.tenantconfig.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Body of {@code PUT /platform/tenants/{id}/providers/{concern}}.
 * Sensitive values inside {@code config} are encrypted by the service layer before save.
 */
public record SetProviderConfigRequest(
    @NotBlank String provider,
    Map<String, Object> config,
    String note
) {}
