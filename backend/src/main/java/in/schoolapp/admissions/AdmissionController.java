package in.schoolapp.admissions;

import in.schoolapp.admissions.dto.AdmissionResponse;
import in.schoolapp.admissions.dto.EnquiryRequest;
import in.schoolapp.admissions.dto.OfferRequest;
import in.schoolapp.admissions.dto.ScheduleTestRequest;
import in.schoolapp.admissions.dto.SubmitApplicationRequest;
import in.schoolapp.admissions.dto.TestResultRequest;
import in.schoolapp.admissions.entity.AdmissionStatus;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.school.dto.ClassResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two surfaces in one file:
 *
 * <ul>
 *   <li>{@code /api/v1/public/schools/{schoolId}/admissions/enquiry} — unauthenticated. The
 *       SecurityConfig allowlist lets this through; rate limiting and CAPTCHA are deferred
 *       to a future slice.</li>
 *   <li>{@code /api/v1/tenants/{tenantId}/admissions/**} — authenticated; role-gated;
 *       feature-flagged behind {@link FeatureKey#ADMISSIONS_FUNNEL}.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;

    // ---------------- PUBLIC ----------------

    @PostMapping("/api/v1/public/schools/{schoolId}/admissions/enquiry")
    public ResponseEntity<ApiResponse<AdmissionResponse>> publicEnquiry(
        @PathVariable UUID schoolId,
        @Valid @RequestBody EnquiryRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(admissionService.createEnquiry(schoolId, req)));
    }

    // ---------------- ADMIN ----------------

    @PostMapping("/api/v1/tenants/{tenantId}/admissions")
    // Front desk logs walk-in enquiries; the rest of the funnel (offer/test/reject) stays admin-only.
    @PreAuthorize(AppRoles.FRONT_DESK)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ResponseEntity<ApiResponse<AdmissionResponse>> createEnquiryAdmin(
        @PathVariable UUID tenantId,
        @Valid @RequestBody EnquiryRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(admissionService.createEnquiry(tenantId, req)));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/admissions")
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<Map<String, Object>> list(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) AdmissionStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 100));
        var p = admissionService.list(tenantId, status, pageable);
        return ApiResponse.success(Map.of(
            "items", p.getContent(),
            "page", p.getNumber(),
            "size", p.getSize(),
            "totalElements", p.getTotalElements(),
            "totalPages", p.getTotalPages()
        ));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}")
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> get(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId
    ) {
        return ApiResponse.success(admissionService.get(tenantId, admissionId));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/application")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> submitApplication(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @Valid @RequestBody SubmitApplicationRequest req
    ) {
        return ApiResponse.success(admissionService.submitApplication(tenantId, admissionId, req));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/test/schedule")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> scheduleTest(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @Valid @RequestBody ScheduleTestRequest req
    ) {
        return ApiResponse.success(admissionService.scheduleTest(tenantId, admissionId, req));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/test/result")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> recordTestResult(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @Valid @RequestBody TestResultRequest req
    ) {
        return ApiResponse.success(admissionService.recordTestResult(tenantId, admissionId, req));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/offer")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> makeOffer(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @Valid @RequestBody OfferRequest req
    ) {
        return ApiResponse.success(admissionService.makeOffer(tenantId, admissionId, req));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/offer/accept")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> acceptOffer(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId
    ) {
        return ApiResponse.success(admissionService.acceptOffer(tenantId, admissionId));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/offer/decline")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> declineOffer(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @RequestParam(required = false) String reason
    ) {
        return ApiResponse.success(admissionService.declineOffer(tenantId, admissionId, reason));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/reject")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> reject(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @RequestParam(required = false) String reason
    ) {
        return ApiResponse.success(admissionService.reject(tenantId, admissionId, reason));
    }

    @PostMapping("/api/v1/tenants/{tenantId}/admissions/{admissionId}/enroll")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<AdmissionResponse> enroll(
        @PathVariable UUID tenantId,
        @PathVariable UUID admissionId,
        @RequestParam UUID sectionId
    ) {
        return ApiResponse.success(admissionService.enrollStudent(tenantId, admissionId, sectionId));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/admissions/intake-options")
    @RequiresFeature(FeatureKey.ADMISSIONS_FUNNEL)
    public ApiResponse<List<ClassResponse>> intakeOptions(
        @PathVariable UUID tenantId
    ) {
        return ApiResponse.success(admissionService.intakeOptions(tenantId));
    }
}
