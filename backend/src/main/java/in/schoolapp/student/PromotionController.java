package in.schoolapp.student;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.dto.AcademicYearResponse;
import in.schoolapp.student.dto.BulkPromotionRequest;
import in.schoolapp.student.dto.BulkPromotionResponse;
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
 * Endpoints for student promotion and academic-year rollover.
 * <p>
 * All operations here are destructive (they close old enrollments and create new ones)
 * and are therefore restricted to {@code OWNER_OR_ADMIN} roles.
 * <p>
 * Typical end-of-year flow:
 * <ol>
 *   <li>{@code POST /academic-years/rollover} — creates next year, flips current flag.</li>
 *   <li>Admin creates sections for the new year (via existing {@code /classes/bulk}).</li>
 *   <li>{@code POST /promotions} — promotes each source section to its target section.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    // ---- Academic year rollover ----

    /**
     * Creates the next academic year and marks the current one as inactive.
     * Safe to call once per year — throws 400 if the next year already exists.
     */
    @PostMapping("/academic-years/rollover")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<AcademicYearResponse>> rollover(@PathVariable UUID tenantId) {
        AcademicYear next = promotionService.rolloverAcademicYear(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(AcademicYearResponse.from(next)));
    }

    /**
     * Lists all academic years for the tenant, newest first.
     * Useful in the promotion UI to confirm which year is current.
     */
    @GetMapping("/academic-years")
    public ApiResponse<List<AcademicYearResponse>> listAcademicYears(@PathVariable UUID tenantId) {
        return ApiResponse.success(
            promotionService.listAcademicYears(tenantId).stream()
                .map(AcademicYearResponse::from).toList());
    }

    // ---- Student promotion ----

    /**
     * Promotes all ACTIVE students in a source section to a target section in the new year.
     * Students listed in {@code failedStudentIds} are retained (moved to {@code retainedSectionId}
     * or back to the source section's class in the new year).
     * <p>
     * Idempotent: students already enrolled in the new year are skipped.
     */
    @PostMapping("/promotions")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<BulkPromotionResponse>> bulkPromote(
        @PathVariable UUID tenantId,
        @Valid @RequestBody BulkPromotionRequest request
    ) {
        BulkPromotionResponse result = promotionService.promoteSection(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }
}
