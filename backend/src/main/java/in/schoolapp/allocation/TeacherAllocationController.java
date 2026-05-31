package in.schoolapp.allocation;

import in.schoolapp.allocation.dto.LiveMonitorResponse;
import in.schoolapp.allocation.dto.TeacherAllocationOverviewResponse;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.auth.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Teacher Allocation Command Center — a single centralized overview for principals/admins of all
 * class/section/subject/class-teacher allocations, per-teacher workload, gaps and the live
 * "now teaching" snapshot. Admin-only; teachers use their personal schedule endpoints instead.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/teacher-allocation")
@RequiredArgsConstructor
public class TeacherAllocationController {

    private final TeacherAllocationService service;

    @GetMapping("/overview")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<TeacherAllocationOverviewResponse> overview(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.getOverview(tenantId));
    }

    /** Real-time teaching snapshot for the Live Teaching Monitor / command center. */
    @GetMapping("/live-monitor")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<LiveMonitorResponse> liveMonitor(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.getLiveMonitor(tenantId));
    }
}
