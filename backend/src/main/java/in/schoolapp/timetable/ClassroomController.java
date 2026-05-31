package in.schoolapp.timetable;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.timetable.dto.ClassroomDtos.ClassroomResponse;
import in.schoolapp.timetable.dto.ClassroomDtos.SaveClassroomRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Room/classroom management. Reads open to staff (timetable consumes them); writes admin-only. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService service;

    @GetMapping
    public ApiResponse<List<ClassroomResponse>> list(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.list(tenantId));
    }

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<ClassroomResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody SaveClassroomRequest req) {
        return ApiResponse.success(service.create(tenantId, req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<ClassroomResponse> update(@PathVariable UUID tenantId, @PathVariable UUID id,
                                                 @Valid @RequestBody SaveClassroomRequest req) {
        return ApiResponse.success(service.update(tenantId, id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ApiResponse.ok();
    }
}
