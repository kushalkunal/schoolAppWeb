package in.schoolapp.analytics;

import in.schoolapp.analytics.dto.DashboardResponse;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Principal dashboard landing endpoint — one call returns attendance snapshot, alert counts,
 * fee MTD, unmarked-sections count, and top-N at-risk students. Cacheable client-side for a
 * few seconds; the underlying services are read-only and lightweight.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/dashboard")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ANALYTICS)
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> get(@PathVariable UUID tenantId) {
        return ApiResponse.success(dashboardService.build(tenantId));
    }
}
