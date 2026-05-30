package in.schoolapp.hr;

import in.schoolapp.audit.AuditLogger;
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
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    private final StaffRepository staffRepository;
    private final AuditLogger audit;

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
            // Segregation of duties: an approver may not approve their own leave (audit #10).
            if (app.getStaffId().equals(TenantContext.getStaffId())) {
                throw new AppException(ErrorCode.APPROVAL_SELF_NOT_ALLOWED,
                    "You cannot approve your own leave application.");
            }
            // Approval hierarchy (audit #6): approver must outrank the applicant.
            requireSufficientApproverRank(tenantId, app.getStaffId());
            consume(app);   // balance-gated; throws if no entitlement or insufficient days
            app.setStatus(LeaveStatus.APPROVED);
        } else {
            app.setStatus(LeaveStatus.REJECTED);
        }
        app.setDecidedAt(OffsetDateTime.now());
        app.setDecidedById(TenantContext.getStaffId());
        app.setDecisionNote(req.note());
        app = applicationRepository.save(app);
        audit.logAction(tenantId, "LeaveApplication", app.getId(),
            req.approve() ? "LEAVE_APPROVED" : "LEAVE_REJECTED",
            Map.of("staffId", String.valueOf(app.getStaffId()),
                "leaveType", app.getLeaveType().name(),
                "days", app.getDays().toPlainString()));
        log.info("Leave decided tenant={} app={} status={} by={}",
            tenantId, applicationId, app.getStatus(), TenantContext.getStaffId());
        return LeaveApplicationResponse.from(app);
    }

    @Transactional
    public LeaveApplicationResponse cancel(UUID tenantId, UUID applicationId) {
        LeaveApplication app = applicationRepository.findByIdAndSchoolId(applicationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Leave application not found"));
        if (app.getStatus() == LeaveStatus.CANCELLED) return LeaveApplicationResponse.from(app);
        if (app.getStatus() == LeaveStatus.APPROVED) {
            refund(app);   // give the consumed days back
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

    /**
     * Consume balance for an approval. Requires a configured entitlement and enough remaining
     * days — no silent auto-seed, so an approval can never grant un-budgeted leave (audit #10).
     */
    private void consume(LeaveApplication app) {
        LeaveBalance bal = requireBalance(app);
        if (bal.remainingDays().compareTo(app.getDays()) < 0) {
            throw new AppException(ErrorCode.LEAVE_BALANCE_INSUFFICIENT,
                "Insufficient " + app.getLeaveType() + " balance: " + bal.remainingDays().toPlainString()
                    + " day(s) remaining, " + app.getDays().toPlainString() + " requested.");
        }
        bal.setConsumedDays(bal.getConsumedDays().add(app.getDays()));
        balanceRepository.save(bal);
    }

    /** Give days back when an approved leave is cancelled; never drives consumed below zero. */
    private void refund(LeaveApplication app) {
        LeaveBalance bal = requireBalance(app);
        bal.setConsumedDays(bal.getConsumedDays().subtract(app.getDays()).max(BigDecimal.ZERO));
        balanceRepository.save(bal);
    }

    private LeaveBalance requireBalance(LeaveApplication app) {
        int year = app.getStartDate().getYear();
        return balanceRepository
            .findBySchoolIdAndStaffIdAndLeaveTypeAndYear(
                app.getSchoolId(), app.getStaffId(), app.getLeaveType(), year)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "No " + app.getLeaveType() + " leave balance configured for " + year
                    + " — set the entitlement before approving."));
    }

    /**
     * Enforces the leave approval hierarchy (audit #6): the approver's role must be senior enough
     * for the applicant's role —
     * teacher/accountant/librarian → ADMIN+, ADMIN → PRINCIPAL+, PRINCIPAL/OWNER → OWNER.
     */
    private void requireSufficientApproverRank(UUID tenantId, UUID applicantStaffId) {
        Staff applicant = staffRepository.findByIdAndSchoolId(applicantStaffId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Applicant staff not found"));
        int required = requiredApproverRank(applicant.getRole());
        int approver = rank(parseRole(TenantContext.getRole()));
        if (approver < required) {
            throw new AppException(ErrorCode.LEAVE_APPROVER_TOO_JUNIOR,
                "Your role cannot approve leave for a " + applicant.getRole()
                    + " — it requires a more senior approver.");
        }
    }

    /** Authority rank of a role (higher = more senior). */
    private static int rank(StaffRole role) {
        if (role == null) return 0;
        return switch (role) {
            case SUPER_ADMIN, SCHOOL_OWNER -> 5;
            case PRINCIPAL -> 4;
            case ADMIN -> 3;
            default -> 1;   // class/subject teacher, accountant, librarian, viewer
        };
    }

    /** Minimum approver rank required to approve a given applicant's leave. */
    private static int requiredApproverRank(StaffRole applicantRole) {
        if (applicantRole == null) return 5;
        return switch (applicantRole) {
            case SUPER_ADMIN, SCHOOL_OWNER, PRINCIPAL -> 5;   // only the owner approves
            case ADMIN -> 4;                                   // principal or above
            default -> 3;                                      // teachers/accountant/librarian → admin+
        };
    }

    private static StaffRole parseRole(String role) {
        if (role == null) return null;
        try {
            return StaffRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
