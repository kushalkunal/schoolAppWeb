package in.schoolapp.analytics;

import in.schoolapp.analytics.detector.AtRiskDetectionService;
import in.schoolapp.analytics.entity.StudentRiskScore;
import in.schoolapp.analytics.repository.StudentRiskScoreRepository;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Read + manual-recompute surface for at-risk student data. The scheduled nightly
 * recompute is in {@link AtRiskDetectionService}; this controller lets a principal
 * inspect the dashboard and (optionally) trigger an immediate rerun for one tenant.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/risk")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.AI_RISK_SCORING)
public class RiskController {

    private final StudentRiskScoreRepository repository;
    private final AtRiskDetectionService detectionService;

    @GetMapping("/students")
    public ApiResponse<List<StudentRiskScore>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "70") int minScore,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        List<StudentRiskScore> rows = repository
            .findBySchoolIdAndScoreGreaterThanEqualOrderByScoreDesc(
                tenantId, minScore,
                PageRequest.of(Math.max(0, page), Math.min(size, 200)));
        return ApiResponse.success(rows);
    }

    @GetMapping("/students/{studentId}")
    public ApiResponse<StudentRiskScore> getOne(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(repository.findBySchoolIdAndStudentId(tenantId, studentId)
            .orElseThrow(() -> new in.schoolapp.common.AppException(
                in.schoolapp.common.ErrorCode.RESOURCE_NOT_FOUND,
                "No risk score yet for student " + studentId + ". The next nightly run will create one.")));
    }

    @PostMapping("/recompute")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<AtRiskDetectionService.Result>> recompute(
        @PathVariable UUID tenantId
    ) {
        AtRiskDetectionService.Result r = detectionService.scanTenant(tenantId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(r));
    }
}
