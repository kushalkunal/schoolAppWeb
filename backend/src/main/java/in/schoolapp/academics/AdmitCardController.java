package in.schoolapp.academics;

import in.schoolapp.academics.dto.AdmitCardDashboardResponse;
import in.schoolapp.academics.dto.AdmitCardResponse;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/exams/{examId}/admit-cards")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.HALL_TICKETS)
public class AdmitCardController {

    private final AdmitCardService admitCardService;

    /** Dashboard stats: total / generated / downloaded / blocked / pending. */
    @GetMapping("/stats")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<AdmitCardDashboardResponse> stats(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId
    ) {
        return ApiResponse.success(admitCardService.getDashboard(tenantId, examId));
    }

    /**
     * List all admit cards for an exam. Optional {@code status} filter:
     * BLOCKED | PENDING | GENERATED | DOWNLOADED
     */
    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<AdmitCardResponse>> list(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(admitCardService.listForExam(tenantId, examId, status));
    }

    /**
     * Bulk-generate admit cards for all eligible students in this exam's section/class.
     * Idempotent — skips already-generated cards; re-evaluates BLOCKED ones in case fees cleared.
     */
    @PostMapping("/generate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Map<String, Integer>> bulkGenerate(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId
    ) {
        int count = admitCardService.generateForExam(tenantId, examId);
        return ApiResponse.success(Map.of("generated", count));
    }

    /**
     * Generate / regenerate admit card for a single student.
     * Also used as "manual regenerate" after the cashier clears fees.
     */
    @PostMapping("/{studentId}/regenerate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<AdmitCardResponse> regenerate(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(admitCardService.generateForStudent(tenantId, examId, studentId));
    }

    /**
     * Mark a card as downloaded. Returns the same card response (with updated status).
     * Called by the frontend when the user clicks the download button.
     */
    @PostMapping("/{admitCardId}/download")
    public ResponseEntity<ApiResponse<AdmitCardResponse>> markDownloaded(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID admitCardId
    ) {
        AdmitCardResponse card = admitCardService.markDownloaded(tenantId, admitCardId);
        return ResponseEntity.ok(ApiResponse.success(card));
    }
}
