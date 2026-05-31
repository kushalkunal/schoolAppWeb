package in.schoolapp.substitution;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.substitution.dto.SubstitutionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Smart substitute management — admin-only. Detects absent teachers, builds a period-wise
 * replacement plan with ranked suggestions, and auto-assigns the best picks in one click.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/substitution")
@RequiredArgsConstructor
@PreAuthorize(AppRoles.OWNER_OR_ADMIN)
public class SubstitutionController {

    private final SubstitutionPlannerService planner;

    private static LocalDate orToday(LocalDate d) { return d == null ? LocalDate.now() : d; }

    /** Only teachers who are absent today (self-attendance ABSENT/LEAVE or approved leave). */
    @GetMapping("/absent-today")
    public ApiResponse<List<AbsentTeacher>> absentToday(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(planner.absentTeachersToday(tenantId, orToday(date)));
    }

    /** Period-wise replacement plan for one absent teacher, with ranked + recommended substitutes. */
    @GetMapping("/plan")
    public ApiResponse<ReplacementPlan> plan(
        @PathVariable UUID tenantId,
        @RequestParam UUID teacherId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(planner.buildPlan(tenantId, teacherId, orToday(date)));
    }

    /** One-click: assign the recommended substitute for every affected (uncovered) period. */
    @PostMapping("/auto-assign")
    public ApiResponse<AutoAssignResult> autoAssign(
        @PathVariable UUID tenantId,
        @RequestParam UUID teacherId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(planner.autoAssign(tenantId, teacherId, orToday(date)));
    }

    /** Principal dashboard counters: absent teachers, assigned, pending, classes without a teacher. */
    @GetMapping("/dashboard")
    public ApiResponse<SubstitutionDashboard> dashboard(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(planner.dashboard(tenantId, orToday(date)));
    }
}
