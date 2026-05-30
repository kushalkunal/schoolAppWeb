package in.schoolapp.academics;

import in.schoolapp.academics.dto.BulkComponentMarksRequest;
import in.schoolapp.academics.dto.ComponentMarksSheetResponse;
import in.schoolapp.academics.dto.ConfigureExamStructureRequest;
import in.schoolapp.academics.dto.ExamResultResponse;
import in.schoolapp.academics.dto.ExamStructureResponse;
import in.schoolapp.academics.dto.ResultDashboardResponse;
import in.schoolapp.academics.dto.UnlockResultRequest;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exam Result module endpoints — marking scheme, component marks entry,
 * result computation, publish, and dashboard.
 *
 * <p>All paths under {@code /api/v1/tenants/{tenantId}}. Tenant boundary enforced by
 * {@link in.schoolapp.common.TenantInterceptor}.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ACADEMICS)
public class ExamResultController {

    private final ExamStructureService structureService;
    private final ComponentMarksService componentMarksService;
    private final ResultService resultService;
    private final ResultDashboardService dashboardService;

    // ----------------------------------------------------------------
    // Exam structure (marking scheme)
    // ----------------------------------------------------------------

    /**
     * Configure marking components for one subject in one exam.
     * Replaces existing components for that subject atomically.
     */
    @PostMapping("/exams/{examId}/structure")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<ExamStructureResponse>> configureStructure(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @Valid @RequestBody ConfigureExamStructureRequest request
    ) {
        ExamStructureResponse response = structureService.configure(tenantId, examId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** Returns the full marking scheme for an exam (all subjects + components). */
    @GetMapping("/exams/{examId}/structure")
    public ApiResponse<List<ExamStructureResponse>> getStructure(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId
    ) {
        return ApiResponse.success(structureService.listStructure(tenantId, examId));
    }

    // ----------------------------------------------------------------
    // Component marks entry
    // ----------------------------------------------------------------

    /**
     * Returns the marks-entry grid: all active students × all subject-components.
     * Pre-fills any previously saved values.
     */
    @GetMapping("/exams/{examId}/component-marks/{sectionId}")
    public ApiResponse<ComponentMarksSheetResponse> getComponentMarksSheet(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(
            componentMarksService.getEntrySheet(tenantId, examId, sectionId)
        );
    }

    /**
     * Bulk-save component marks for a section.
     * Setting {@code submitFinal=true} locks the entries and triggers result computation.
     */
    @PostMapping("/exams/{examId}/component-marks")
    @PreAuthorize(AppRoles.MARKS_WRITER)
    public ResponseEntity<ApiResponse<Void>> submitComponentMarks(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @Valid @RequestBody BulkComponentMarksRequest request
    ) {
        componentMarksService.submitBulk(tenantId, examId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ----------------------------------------------------------------
    // Results
    // ----------------------------------------------------------------

    /**
     * Trigger result computation for a section. Idempotent — safe to re-run after corrections.
     * Sets result status to {@code READY}.
     */
    @PostMapping("/exams/{examId}/results/compute/{sectionId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<ExamResultResponse>> computeResults(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(resultService.computeForSection(tenantId, examId, sectionId));
    }

    /** Returns computed results for a section, ordered by rank. */
    @GetMapping("/exams/{examId}/results/{sectionId}")
    public ApiResponse<List<ExamResultResponse>> getResults(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(resultService.getResultsForSection(tenantId, examId, sectionId));
    }

    /**
     * Class-teacher verification gate (audit #7): the section's class teacher (or an admin)
     * reviews the computed results and marks them VERIFIED. Required before publish.
     */
    @PostMapping("/exams/{examId}/results/verify/{sectionId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<ExamResultResponse>> verifyResults(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(resultService.verifySection(tenantId, examId, sectionId));
    }

    /**
     * Publish results for a section: flips status to {@code PUBLISHED}, generates PDFs,
     * and sends WhatsApp + email notifications to parents. Requires class-teacher verification.
     */
    @PostMapping("/exams/{examId}/results/publish/{sectionId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<ExamResultResponse>> publishResults(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(resultService.publishSection(tenantId, examId, sectionId));
    }

    /**
     * Unlock (reopen) published results for a section so marks can be corrected.
     * Requires an explicit reason which is written to the audit log.
     * Restricted to OWNER / ADMIN roles.
     */
    @PostMapping("/exams/{examId}/results/unlock/{sectionId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<ExamResultResponse>> unlockResults(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID sectionId,
        @Valid @RequestBody UnlockResultRequest request
    ) {
        return ApiResponse.success(
            resultService.unlockSection(tenantId, examId, sectionId, request.reason())
        );
    }

    /** Analytics dashboard — toppers, pass rates, grade distribution, subject averages. */
    @GetMapping("/exams/{examId}/dashboard")
    public ApiResponse<ResultDashboardResponse> getDashboard(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId
    ) {
        return ApiResponse.success(dashboardService.getDashboard(tenantId, examId));
    }
}
