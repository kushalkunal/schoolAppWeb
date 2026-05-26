package in.schoolapp.timetable;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.timetable.dto.PeriodDto;
import in.schoolapp.timetable.dto.TimetableEntryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService service;

    @GetMapping("/periods")
    public ApiResponse<List<PeriodDto>> listPeriods(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listPeriods(tenantId));
    }

    @PostMapping("/periods")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<PeriodDto>> createPeriod(@PathVariable UUID tenantId,
                                                               @Valid @RequestBody PeriodDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createPeriod(tenantId, req)));
    }

    @DeleteMapping("/periods/{periodId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deletePeriod(@PathVariable UUID tenantId, @PathVariable UUID periodId) {
        service.deletePeriod(tenantId, periodId);
        return ApiResponse.ok();
    }

    @GetMapping("/sections/{sectionId}")
    public ApiResponse<List<TimetableEntryDto>> sectionTimetable(@PathVariable UUID tenantId,
                                                                 @PathVariable UUID sectionId) {
        return ApiResponse.success(service.getSectionTimetable(tenantId, sectionId));
    }

    @GetMapping("/teachers/{teacherId}")
    public ApiResponse<List<TimetableEntryDto>> teacherTimetable(@PathVariable UUID tenantId,
                                                                 @PathVariable UUID teacherId) {
        return ApiResponse.success(service.getTeacherTimetable(tenantId, teacherId));
    }

    @PostMapping("/entries")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<TimetableEntryDto>> upsertEntry(@PathVariable UUID tenantId,
                                                                      @Valid @RequestBody TimetableEntryDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.upsertEntry(tenantId, req)));
    }

    @PutMapping("/entries")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<TimetableEntryDto> updateEntry(@PathVariable UUID tenantId,
                                                      @Valid @RequestBody TimetableEntryDto req) {
        return ApiResponse.success(service.upsertEntry(tenantId, req));
    }

    @DeleteMapping("/entries/{entryId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> deleteEntry(@PathVariable UUID tenantId, @PathVariable UUID entryId) {
        service.deleteEntry(tenantId, entryId);
        return ApiResponse.ok();
    }
}
