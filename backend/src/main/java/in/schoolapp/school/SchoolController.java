package in.schoolapp.school;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.CreateClassesRequest;
import in.schoolapp.school.dto.CreateSchoolRequest;
import in.schoolapp.school.dto.CreateStaffRequest;
import in.schoolapp.school.dto.AcademicYearResponse;
import in.schoolapp.school.dto.OnboardingStatusResponse;
import in.schoolapp.school.dto.SchoolResponse;
import in.schoolapp.school.dto.SchoolSignupResponse;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.dto.UpdateSchoolRequest;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped endpoints. The URL vocabulary is {@code /tenants/{tenantId}} to make the
 * multi-tenant boundary explicit; internally a Tenant maps 1:1 to a School, so the service
 * layer still speaks {@code schoolId}. The {@code TenantInterceptor} verifies the path
 * {@code tenantId} against the caller's JWT claim on every request — controllers never need
 * to call {@code TenantContext.validateTenant(...)} themselves.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;
    private final ClassSectionService classSectionService;
    private final StaffService staffService;
    private final OnboardingService onboardingService;
    private final AcademicYearService academicYearService;

    /**
     * Public signup. Creates the tenant's School + Principal Staff + current AcademicYear.
     * Response's {@code nextStep} tells the client which OTP flow to invoke.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SchoolSignupResponse>> createTenant(
        @Valid @RequestBody CreateSchoolRequest request
    ) {
        SchoolSignupResponse response = schoolService.createSchool(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<SchoolResponse> getTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success(schoolService.getSchool(tenantId));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<SchoolResponse> updateTenant(
        @PathVariable UUID tenantId,
        @Valid @RequestBody UpdateSchoolRequest request
    ) {
        return ApiResponse.success(schoolService.updateSchool(tenantId, request));
    }

    @PostMapping(value = "/{tenantId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<SchoolResponse> uploadLogo(
        @PathVariable UUID tenantId,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Logo file is empty");
        }
        try {
            return ApiResponse.success(schoolService.uploadLogo(
                tenantId, file.getBytes(), file.getContentType()));
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Could not read uploaded file");
        }
    }

    @GetMapping("/{tenantId}/onboarding-status")
    public ApiResponse<OnboardingStatusResponse> getOnboardingStatus(@PathVariable UUID tenantId) {
        return ApiResponse.success(onboardingService.getStatus(tenantId));
    }

    // ----- Classes + Sections -----

    @PostMapping("/{tenantId}/classes/bulk")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<List<ClassResponse>>> bulkCreateClasses(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateClassesRequest request
    ) {
        List<ClassResponse> created = classSectionService.bulkCreate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{tenantId}/classes")
    public ApiResponse<List<ClassResponse>> listClasses(@PathVariable UUID tenantId) {
        return ApiResponse.success(classSectionService.listClasses(tenantId));
    }

    // ----- Academic years (read-only) -----

    /**
     * Returns the current academic year for the tenant. The fee-structure UI and other
     * year-scoped flows call this to resolve {@code academicYearId} without persisting it
     * client-side.
     */
    @GetMapping("/{tenantId}/academic-years/current")
    public ApiResponse<AcademicYearResponse> currentAcademicYear(@PathVariable UUID tenantId) {
        return ApiResponse.success(
            AcademicYearResponse.from(academicYearService.getCurrentOrThrow(tenantId))
        );
    }

    // ----- Staff -----

    @PostMapping("/{tenantId}/staff")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateStaffRequest request
    ) {
        StaffResponse staff = staffService.createStaff(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @GetMapping("/{tenantId}/staff")
    public ApiResponse<List<StaffResponse>> listStaff(@PathVariable UUID tenantId) {
        return ApiResponse.success(staffService.listStaff(tenantId));
    }

    @DeleteMapping("/{tenantId}/staff/{staffId}")
    @PreAuthorize("hasAnyRole('SCHOOL_OWNER','PRINCIPAL')")
    public ApiResponse<Void> deactivateStaff(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId
    ) {
        staffService.deactivateStaff(tenantId, staffId);
        return ApiResponse.ok();
    }
}
