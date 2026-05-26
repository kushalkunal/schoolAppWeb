package in.schoolapp.infirmary;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.infirmary.dto.CreateVisitRequest;
import in.schoolapp.infirmary.dto.MedicalRecordDto;
import in.schoolapp.infirmary.dto.VisitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/infirmary")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.INFIRMARY_LOG)
public class InfirmaryController {

    private final InfirmaryService service;

    @PostMapping("/visits")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<VisitResponse>> record(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateVisitRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.recordVisit(tenantId, req)));
    }

    @GetMapping("/visits")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<VisitResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success(service.list(tenantId, page, size));
    }

    @GetMapping("/visits/by-student/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<VisitResponse>> byStudent(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(service.forStudent(studentId));
    }

    @GetMapping("/medical/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<MedicalRecordDto> getMedical(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(service.getMedical(studentId));
    }

    @PutMapping("/medical/{studentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<MedicalRecordDto> upsertMedical(
        @PathVariable UUID tenantId, @PathVariable UUID studentId,
        @RequestBody MedicalRecordDto req
    ) {
        return ApiResponse.success(service.upsertMedical(tenantId, studentId, req));
    }
}
