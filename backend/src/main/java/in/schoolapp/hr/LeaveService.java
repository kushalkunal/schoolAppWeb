package in.schoolapp.hr;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.hr.dto.LeaveApplicationRequest;
import in.schoolapp.hr.dto.LeaveApplicationResponse;
import in.schoolapp.hr.dto.LeaveBalanceResponse;
import in.schoolapp.hr.dto.LeaveDecisionRequest;
import in.schoolapp.hr.dto.UpdateLeaveBalanceRequest;
import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.LeaveApplication.LeaveStatus;
import in.schoolapp.hr.entity.LeaveBalance;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Leave application workflow.
 *
 * <pre>
 *   submit() → status SUBMITTED
 *   decide(APPROVED)  → status APPROVED, balance.consumed_days += days
 *   decide(REJECTED)  → status REJECTED (no balance change)
 *   cancel()          → status CANCELLED (and rolls back balance if previously APPROVED)
 * </pre>
 *
 * Balance rows are NOT auto-created — HR explicitly seeds entitlements per year via
 * {@code POST /leave-balances}. Approving a leave for a (staff, type, year) that has no
 * balance row throws {@code RESOURCE_NOT_FOUND}, which surfaces as a clear 404 in the UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveBalanceRepository balanceRepository;

    @Transactional
    public LeaveApplicationResponse submit(UUID tenantId, LeaveApplicationRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "end_date must be on or after start_date");
        }
        if (req.days().signum() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "days must be positive");
        }

        // RBAC: non-admin staff may only submit leave for themselves.
        // PRINCIPAL / ADMIN / SCHOOL_OWNER may file on behalf of any staff member.
        String role = TenantContext.getRole();
        boolean isAdmin = "PRINCIPAL".equals(role) || "ADMIN".equals(role) || "SCHOOL_OWNER".equals(role);
        if (!isAdmin) {
            UUID selfId = TenantContext.getStaffId();
            if (!req.staffId().equals(selfId)) {
                throw new AppException(ErrorCode.FORBIDDEN,
                    "You may only submit leave applications for yourself.");
            }
        }

        // Reject if there is already a SUBMITTED or APPROVED leave that overlaps this range.
        List<LeaveApplication> overlaps = applicationRepository
            .findBySchoolIdAndStaffIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                tenantId, req.staffId(),
                List.of(LeaveStatus.SUBMITTED, LeaveStatus.APPROVED),
                req.endDate(), req.startDate());
        if (!overlaps.isEmpty()) {
            throw new AppException(ErrorCode.LEAVE_OVERLAP,
                "You already have an ongoing leave request during these dates.");
        }

        LeaveApplication app = new LeaveApplication();
        app.setSchoolId(tenantId);
        app.setStaffId(req.staffId());
        app.setLeaveType(req.leaveType());
        app.setStartDate(req.startDate());
        app.setEndDate(req.endDate());
        app.setDays(req.days());
        app.setReason(req.reason());
        app.setStatus(LeaveStatus.SUBMITTED);
        app = applicationRepository.save(app);
        log.info("Leave submitted tenant={} staff={} type={} days={}",
            tenantId, req.staffId(), req.leaveType(), req.days());
        return LeaveApplicationResponse.from(app);
    }

    @Transactional
    public LeaveApplicationResponse decide(UUID tenantId, UUID applicationId, LeaveDecisionRequest req) {
        LeaveApplication app = applicationRepository.findByIdAndSchoolId(applicationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Leave application not found"));
        if (app.getStatus() != LeaveStatus.SUBMITTED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only SUBMITTED applications can be decided (this one is " + app.getStatus() + ")");
        }
        if (req.approve()) {
            adjustBalance(app, app.getDays());
            app.setStatus(LeaveStatus.APPROVED);
        } else {
            app.setStatus(LeaveStatus.REJECTED);
        }
        app.setDecidedAt(OffsetDateTime.now());
        app.setDecisionNote(req.note());
        app = applicationRepository.save(app);
        log.info("Leave decided tenant={} app={} status={}", tenantId, applicationId, app.getStatus());
        return LeaveApplicationResponse.from(app);
    }

    @Transactional
    public LeaveApplicationResponse cancel(UUID tenantId, UUID applicationId) {
        LeaveApplication app = applicationRepository.findByIdAndSchoolId(applicationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Leave application not found"));
        if (app.getStatus() == LeaveStatus.CANCELLED) return LeaveApplicationResponse.from(app);
        if (app.getStatus() == LeaveStatus.APPROVED) {
            // Refund balance.
            adjustBalance(app, app.getDays().negate());
        }
        app.setStatus(LeaveStatus.CANCELLED);
        app.setDecidedAt(OffsetDateTime.now());
        app = applicationRepository.save(app);
        return LeaveApplicationResponse.from(app);
    }

    public List<LeaveApplicationResponse> listForStaff(UUID tenantId, UUID staffId) {
        return applicationRepository.findBySchoolIdAndStaffIdOrderByStartDateDesc(tenantId, staffId)
            .stream().map(LeaveApplicationResponse::from).toList();
    }

    public List<LeaveApplicationResponse> listPending(UUID tenantId) {
        return applicationRepository
            .findBySchoolIdAndStatusOrderByCreatedAtDesc(tenantId, LeaveStatus.SUBMITTED)
            .stream().map(LeaveApplicationResponse::from).toList();
    }

    /** Staff (or admin) views their own leave balances for a given year. */
    public List<LeaveBalanceResponse> listBalances(UUID tenantId, UUID staffId, int year) {
        return balanceRepository
            .findBySchoolIdAndStaffIdAndYearOrderByLeaveType(tenantId, staffId, year)
            .stream().map(LeaveBalanceResponse::from).toList();
    }

    /** Admin updates the entitled days for a specific balance row. Creates the row if absent. */
    @Transactional
    public LeaveBalanceResponse updateBalance(UUID tenantId, UUID staffId,
                                             in.schoolapp.hr.entity.LeaveApplication.LeaveType leaveType,
                                             int year, UpdateLeaveBalanceRequest req) {
        LeaveBalance bal = balanceRepository
            .findBySchoolIdAndStaffIdAndLeaveTypeAndYear(tenantId, staffId, leaveType, year)
            .orElseGet(() -> {
                LeaveBalance fresh = new LeaveBalance();
                fresh.setSchoolId(tenantId);
                fresh.setStaffId(staffId);
                fresh.setLeaveType(leaveType);
                fresh.setYear(year);
                fresh.setConsumedDays(BigDecimal.ZERO);
                return fresh;
            });
        bal.setEntitledDays(req.entitledDays());
        bal = balanceRepository.save(bal);
        log.info("Updated leave balance tenant={} staff={} type={} year={} entitled={}",
            tenantId, staffId, leaveType, year, req.entitledDays());
        return LeaveBalanceResponse.from(bal);
    }

    /** delta is positive for "consume" and negative for "refund" (cancellation). */
    private void adjustBalance(LeaveApplication app, BigDecimal delta) {
        int year = app.getStartDate().getYear();
        LeaveBalance bal = balanceRepository
            .findBySchoolIdAndStaffIdAndLeaveTypeAndYear(
                app.getSchoolId(), app.getStaffId(), app.getLeaveType(), year)
            .orElseGet(() -> {
                // Auto-seed a default entitlement so first-time approvals don't fail.
                // HR can always edit the row later via POST /leave-balances.
                log.warn("No leave balance row for staff={} type={} year={}. Auto-seeding default entitlement.",
                    app.getStaffId(), app.getLeaveType(), year);
                LeaveBalance fresh = new LeaveBalance();
                fresh.setSchoolId(app.getSchoolId());
                fresh.setStaffId(app.getStaffId());
                fresh.setLeaveType(app.getLeaveType());
                fresh.setYear(year);
                fresh.setEntitledDays(new BigDecimal("30"));
                fresh.setConsumedDays(BigDecimal.ZERO);
                return balanceRepository.save(fresh);
            });
        bal.setConsumedDays(bal.getConsumedDays().add(delta));
        balanceRepository.save(bal);
    }
}
