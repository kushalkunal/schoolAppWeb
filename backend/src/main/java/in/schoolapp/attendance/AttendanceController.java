package in.schoolapp.attendance;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.attendance.dto.AttendanceRecordResponse;
import in.schoolapp.attendance.dto.AttendanceSectionResponse;
import in.schoolapp.attendance.dto.AttendanceSubmitResponse;
import in.schoolapp.attendance.dto.AttendanceSummaryResponse;
import in.schoolapp.attendance.dto.ChronicAbsenteeResponse;
import in.schoolapp.attendance.dto.SubmitAttendanceRequest;
import in.schoolapp.attendance.dto.UnmarkedSectionResponse;
import in.schoolapp.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceAnalyticsService analyticsService;

    @PostMapping("/sections/{sectionId}/attendance")
    // Any teacher may reach this; the service authorizes the specific section (class teacher of it,
    // or a substitute with an active substitution for it today). Admins may mark any section.
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ResponseEntity<ApiResponse<AttendanceSubmitResponse>> submitAttendance(
        @PathVariable UUID tenantId,
        @PathVariable UUID sectionId,
        @Valid @RequestBody SubmitAttendanceRequest request
    ) {
        AttendanceSubmitResponse response = attendanceService.submitAttendance(tenantId, sectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/sections/{sectionId}/attendance")
    public ApiResponse<AttendanceSectionResponse> getSectionAttendance(
        @PathVariable UUID tenantId,
        @PathVariable UUID sectionId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(attendanceService.getSectionAttendance(tenantId, sectionId, date));
    }

    @GetMapping("/students/{studentId}/attendance")
    public ApiResponse<List<AttendanceRecordResponse>> getStudentAttendance(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(attendanceService.getStudentHistory(tenantId, studentId, from, to));
    }

    @GetMapping("/attendance/summary")
    public ApiResponse<AttendanceSummaryResponse> schoolSummary(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(analyticsService.schoolSummary(
            tenantId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/attendance/unmarked")
    public ApiResponse<List<UnmarkedSectionResponse>> unmarkedSections(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(analyticsService.unmarkedSections(
            tenantId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/attendance/chronic")
    public ApiResponse<List<ChronicAbsenteeResponse>> chronicAbsentees(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "30") int windowDays,
        @RequestParam(defaultValue = "5") int minAbsences
    ) {
        return ApiResponse.success(analyticsService.chronicAbsentees(tenantId, windowDays, minAbsences));
    }
}
