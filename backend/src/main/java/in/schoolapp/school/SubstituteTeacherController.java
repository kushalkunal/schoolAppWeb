package in.schoolapp.school;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.school.dto.CreateSubstituteRequest;
import in.schoolapp.school.dto.SubstituteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/substitutes")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.SUBSTITUTE_TEACHERS)
public class SubstituteTeacherController {

    private final SubstituteTeacherService service;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<SubstituteResponse>> assign(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateSubstituteRequest request
    ) {
        SubstituteResponse response = service.assign(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<SubstituteResponse>> listForDate(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return ApiResponse.success(service.listForDate(tenantId, target));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> cancel(
        @PathVariable UUID tenantId, @PathVariable UUID assignmentId
    ) {
        service.cancel(tenantId, assignmentId);
        return ApiResponse.ok();
    }
}
