package in.schoolapp.feature;

import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Tenant-facing endpoints — a school owner / admin reads which features are effectively
 * enabled. Write paths (override, plan change) live on PlatformAdminController under
 * {@code /api/v1/platform/...} and are SUPER_ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    public ApiResponse<Map<String, Boolean>> myFlags(@PathVariable UUID tenantId) {
        TenantContext.validateTenant(tenantId);
        return ApiResponse.success(featureFlagService.effectiveFlagsForSchool(tenantId));
    }
}
