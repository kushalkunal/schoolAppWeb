package in.schoolapp.hr;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.hr.dto.LeaveApplicationRequest;
import in.schoolapp.hr.dto.LeaveApplicationResponse;
import in.schoolapp.hr.dto.LeaveDecisionRequest;
import in.schoolapp.hr.dto.PayslipResponse;
import in.schoolapp.hr.dto.StaffAttendanceRequest;
import in.schoolapp.hr.dto.StaffAttendanceResponse;
import in.schoolapp.hr.dto.StaffMonthlySummaryResponse;
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
@RequestMapping("/api/v1/tenants/{tenantId}/hr")
@RequiredArgsConstructor
public class HrController {

    private final StaffAttendanceService staffAttendanceService;
    private final LeaveService leaveService;
    private final PayrollService payrollService;

    // ---------------- Staff attendance ----------------

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
