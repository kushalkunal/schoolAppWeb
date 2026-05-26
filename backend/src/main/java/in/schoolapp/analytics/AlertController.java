package in.schoolapp.analytics;

import in.schoolapp.analytics.dto.AlertResponse;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Principal dashboard surface for alerts. Supports severity-filtered listing and a one-shot
 * dismiss. There is no create endpoint — alerts are produced by scheduled detectors, never
 * ad-hoc by staff (gap analysis §5.4).
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/alerts")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ANALYTICS)
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ApiResponse<List<AlertResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) List<AlertSeverity> severity
    ) {
        List<AlertResponse> out = alertService.listActiveBySeverity(tenantId, severity).stream()
            .map(AlertResponse::from)
            .toList();
        return ApiResponse.success(out);
    }

    @PostMapping("/{alertId}/dismiss")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<AlertResponse> dismiss(
        @PathVariable UUID tenantId,
        @PathVariable UUID alertId
    ) {
        return ApiResponse.success(AlertResponse.from(alertService.dismiss(tenantId, alertId)));
    }
}
