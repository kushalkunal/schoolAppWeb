package in.schoolapp.hr;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.hr.dto.LeaveApplicationRequest;
import in.schoolapp.hr.dto.LeaveApplicationResponse;
import in.schoolapp.hr.dto.LeaveBalanceResponse;
import in.schoolapp.hr.dto.LeaveDecisionRequest;
import in.schoolapp.hr.dto.UpdateLeaveBalanceRequest;
import in.schoolapp.hr.dto.PayslipResponse;
import in.schoolapp.hr.dto.StaffAttendanceRequest;
import in.schoolapp.hr.dto.StaffAttendanceResponse;
import in.schoolapp.hr.dto.StaffMonthlySummaryResponse;
import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/hr")
@RequiredArgsConstructor
public class HrController {

    private final StaffAttendanceService staffAttendanceService;
    private final LeaveService leaveService;
    private final PayrollService payrollService;

    // ---------------- Staff attendance ----------------

    /**
     * Teacher marks their OWN attendance for today.
     * Accessible by any staff member (teacher, principal, etc.).
     * Sets approved=false — requires principal/admin to approve.
     */
    @PostMapping("/attendance/self")
    @PreAuthorize(AppRoles.ANY_STAFF)
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ResponseEntity<ApiResponse<StaffAttendanceResponse>> markSelf(
        @PathVariable UUID tenantId,
        @RequestParam StaffAttendanceStatus status,
        @RequestParam(required = false) String notes
    ) {
        UUID staffId = TenantContext.getStaffId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(staffAttendanceService.markSelf(tenantId, staffId, status, notes)));
    }

    /** Any staff member views their own attendance history. */
    @GetMapping("/attendance/me")
    @PreAuthorize(AppRoles.ANY_STAFF)
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ApiResponse<List<StaffAttendanceResponse>> myAttendance(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        UUID staffId = TenantContext.getStaffId();
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now();
        return ApiResponse.success(staffAttendanceService.getMyAttendance(tenantId, staffId, f, t));
    }

    /** Admin: list all staff whose attendance for the date is pending approval. */
    @GetMapping("/attendance/pending-approvals")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ApiResponse<List<StaffAttendanceResponse>> pendingApprovals(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(staffAttendanceService.pendingApprovals(
            tenantId, date != null ? date : LocalDate.now()));
    }

    /** Admin: approve a teacher's self-attendance. */
    @PostMapping("/attendance/{staffId}/approve")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ApiResponse<StaffAttendanceResponse> approve(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(staffAttendanceService.approve(
            tenantId, staffId, date != null ? date : LocalDate.now()));
    }

    @PostMapping("/attendance/mark")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ResponseEntity<ApiResponse<List<StaffAttendanceResponse>>> bulkMark(
        @PathVariable UUID tenantId,
        @Valid @RequestBody List<StaffAttendanceRequest> requests
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(staffAttendanceService.bulkMark(tenantId, requests)));
    }

    @GetMapping("/attendance")
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ApiResponse<List<StaffAttendanceResponse>> listForDate(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(staffAttendanceService.listForDate(tenantId, date));
    }

    @GetMapping("/attendance/{staffId}/summary")
    @RequiresFeature(FeatureKey.STAFF_ATTENDANCE)
    public ApiResponse<StaffMonthlySummaryResponse> monthlySummary(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId,
        @RequestParam int year,
        @RequestParam int month
    ) {
        return ApiResponse.success(staffAttendanceService.monthlySummary(tenantId, staffId, year, month));
    }

    // ---------------- Leave ----------------

    @PostMapping("/leave")
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ResponseEntity<ApiResponse<LeaveApplicationResponse>> submit(
        @PathVariable UUID tenantId,
        @Valid @RequestBody LeaveApplicationRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(leaveService.submit(tenantId, req)));
    }

    @PostMapping("/leave/{applicationId}/decide")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<LeaveApplicationResponse> decide(
        @PathVariable UUID tenantId,
        @PathVariable UUID applicationId,
        @Valid @RequestBody LeaveDecisionRequest req
    ) {
        return ApiResponse.success(leaveService.decide(tenantId, applicationId, req));
    }

    @PostMapping("/leave/{applicationId}/cancel")
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<LeaveApplicationResponse> cancel(
        @PathVariable UUID tenantId,
        @PathVariable UUID applicationId
    ) {
        return ApiResponse.success(leaveService.cancel(tenantId, applicationId));
    }

    @GetMapping("/leave/staff/{staffId}")
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<List<LeaveApplicationResponse>> listForStaff(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId
    ) {
        return ApiResponse.success(leaveService.listForStaff(tenantId, staffId));
    }

    @GetMapping("/leave/pending")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<List<LeaveApplicationResponse>> listPending(
        @PathVariable UUID tenantId
    ) {
        return ApiResponse.success(leaveService.listPending(tenantId));
    }

    // ---------------- Leave Balances ----------------

    /** Any staff member can view their own balances. Admin can view any. */
    @GetMapping("/leave-balances/staff/{staffId}")
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<List<LeaveBalanceResponse>> listBalances(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId,
        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year
    ) {
        return ApiResponse.success(leaveService.listBalances(tenantId, staffId, year));
    }

    /** Admin updates / overrides a staff member's entitlement for a specific type+year. */
    @PutMapping("/leave-balances/staff/{staffId}/{leaveType}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.LEAVE_MANAGEMENT)
    public ApiResponse<LeaveBalanceResponse> updateBalance(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId,
        @PathVariable LeaveType leaveType,
        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
        @RequestBody UpdateLeaveBalanceRequest req
    ) {
        return ApiResponse.success(leaveService.updateBalance(tenantId, staffId, leaveType, year, req));
    }

    // ---------------- Payroll ----------------

    @PostMapping("/payslips/{staffId}/generate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.PAYROLL)
    public ResponseEntity<ApiResponse<PayslipResponse>> generate(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId,
        @RequestParam int year,
        @RequestParam int month
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(payrollService.generate(tenantId, staffId, year, month)));
    }

    @GetMapping("/payslips/staff/{staffId}")
    @RequiresFeature(FeatureKey.PAYROLL)
    public ApiResponse<List<PayslipResponse>> listPayslips(
        @PathVariable UUID tenantId,
        @PathVariable UUID staffId
    ) {
        return ApiResponse.success(payrollService.listForStaff(tenantId, staffId));
    }
}
