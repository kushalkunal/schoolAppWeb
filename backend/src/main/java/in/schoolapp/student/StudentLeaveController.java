package in.schoolapp.student;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.student.dto.StudentLeaveDto;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Student leave applications — parent/teacher applies; class teacher or admin decides.
 *
 * <table>
 *   <tr><th>Verb</th><th>Path</th><th>Who</th></tr>
 *   <tr><td>POST</td><td>/student-leaves</td><td>CLASS_TEACHER (own section) or ADMIN</td></tr>
 *   <tr><td>POST</td><td>/student-leaves/{id}/decide</td><td>CLASS_TEACHER (own section) or ADMIN</td></tr>
 *   <tr><td>POST</td><td>/student-leaves/{id}/cancel</td><td>ANY_TEACHER or ADMIN</td></tr>
 *   <tr><td>GET</td><td>/student-leaves/students/{studentId}</td><td>any authenticated</td></tr>
 *   <tr><td>GET</td><td>/student-leaves/sections/{sectionId}/pending</td><td>CLASS_TEACHER (own section) or ADMIN</td></tr>
 *   <tr><td>GET</td><td>/student-leaves/pending</td><td>OWNER_OR_ADMIN only</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/student-leaves")
@RequiredArgsConstructor
public class StudentLeaveController {

    private final StudentLeaveService leaveService;

    /** Apply for student leave. CLASS_TEACHER of that section or ADMIN/PRINCIPAL/OWNER. */
    @PostMapping
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ResponseEntity<ApiResponse<StudentLeaveDto>> submit(
        @PathVariable UUID tenantId,
        @Valid @RequestBody StudentLeaveDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(leaveService.submit(tenantId, request)));
    }

    /** Approve or reject a submitted leave. CLASS_TEACHER of that section or ADMIN. */
    @PostMapping("/{id}/decide")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<StudentLeaveDto> decide(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestParam boolean approve,
        @RequestParam(required = false) String note
    ) {
        return ApiResponse.success(leaveService.decide(tenantId, id, approve, note));
    }

    /** Cancel a leave application. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<StudentLeaveDto> cancel(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        return ApiResponse.success(leaveService.cancel(tenantId, id));
    }

    /** All leave applications for a specific student (any authenticated). */
    @GetMapping("/students/{studentId}")
    public ApiResponse<List<StudentLeaveDto>> forStudent(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId
    ) {
        return ApiResponse.success(leaveService.listForStudent(tenantId, studentId));
    }

    /** Pending leaves for a specific section (class teacher or admin). */
    @GetMapping("/sections/{sectionId}/pending")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<StudentLeaveDto>> pendingForSection(
        @PathVariable UUID tenantId,
        @PathVariable UUID sectionId
    ) {
        return ApiResponse.success(leaveService.listPendingForSection(tenantId, sectionId));
    }

    /** All pending leaves across the school (admin / principal view). */
    @GetMapping("/pending")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<StudentLeaveDto>> allPending(@PathVariable UUID tenantId) {
        return ApiResponse.success(leaveService.listAllPending(tenantId));
    }
}
