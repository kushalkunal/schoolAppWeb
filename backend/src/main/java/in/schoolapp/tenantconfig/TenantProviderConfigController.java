package in.schoolapp.tenantconfig;

import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-self view of provider configurations. Sensitive fields are MASKED — a school owner
 * can see <em>which</em> provider is wired and that credentials are configured, but never the
 * credential value. Writes go through the platform-admin endpoints (slice 1 deliberately keeps
 * credential ownership with the platform team).
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/providers")
@RequiredArgsConstructor
public class TenantProviderConfigController {

    private final TenantProviderConfigService service;

    @GetMapping
    public ApiResponse<List<TenantProviderConfigService.ResolvedConfig>> myProviders(
        @PathVariable UUID tenantId
    ) {
        TenantContext.validateTenant(tenantId);
        return ApiResponse.success(service.listForTenantMasked(tenantId));
    }
}
