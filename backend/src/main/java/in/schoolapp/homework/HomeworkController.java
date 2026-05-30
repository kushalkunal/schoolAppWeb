package in.schoolapp.homework;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.homework.dto.AssignmentDto;
import in.schoolapp.homework.dto.SubmissionDto;
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
@RequestMapping("/api/v1/tenants/{tenantId}/homework")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkService service;

    @PostMapping("/assignments")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ResponseEntity<ApiResponse<AssignmentDto>> create(@PathVariable UUID tenantId,
                                                              @Valid @RequestBody AssignmentDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createAssignment(tenantId, req)));
    }

    @GetMapping("/assignments")
    public ApiResponse<List<AssignmentDto>> list(@PathVariable UUID tenantId,
                                                 @RequestParam(required = false) UUID sectionId) {
        return ApiResponse.success(sectionId == null
            ? service.listForTenant(tenantId)
            : service.listForSection(tenantId, sectionId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID assignmentId) {
        service.deleteAssignment(tenantId, assignmentId);
        return ApiResponse.ok();
    }

    @PostMapping("/submissions")
    @PreAuthorize(AppRoles.ANY_TEACHER)   // audit #4: was unguarded (any authenticated user could submit)
    public ResponseEntity<ApiResponse<SubmissionDto>> submit(@PathVariable UUID tenantId,
                                                              @Valid @RequestBody SubmissionDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.submitOrUpdate(tenantId, req)));
    }

    @PostMapping("/submissions/{submissionId}/grade")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<SubmissionDto> grade(@PathVariable UUID tenantId,
                                            @PathVariable UUID submissionId,
                                            @RequestParam String grade,
                                            @RequestParam(required = false) String remark) {
        return ApiResponse.success(service.gradeSubmission(tenantId, submissionId, grade, remark));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<SubmissionDto>> listSubmissions(@PathVariable UUID tenantId,
                                                            @PathVariable UUID assignmentId) {
        return ApiResponse.success(service.listForAssignment(tenantId, assignmentId));
    }
}
