package in.schoolapp.academics;

import in.schoolapp.academics.dto.CreateTeacherAssignmentRequest;
import in.schoolapp.academics.dto.TeacherAssignmentResponse;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/teacher-assignments")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.TEACHER_ASSIGNMENTS)
public class TeacherAssignmentController {

    private final TeacherAssignmentService service;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<TeacherAssignmentResponse>> assign(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateTeacherAssignmentRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.assign(tenantId, req)));
    }

    @GetMapping
    public ApiResponse<List<TeacherAssignmentResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) UUID staffId
    ) {
        return ApiResponse.success(staffId == null
            ? service.listCurrent(tenantId)
            : service.listForStaff(tenantId, staffId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> unassign(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.unassign(tenantId, id);
        return ApiResponse.ok();
    }
}
