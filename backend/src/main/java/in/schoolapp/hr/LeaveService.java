package in.schoolapp.hr;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hr.dto.LeaveApplicationRequest;
import in.schoolapp.hr.dto.LeaveApplicationResponse;
import in.schoolapp.hr.dto.LeaveDecisionRequest;
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

    /** delta is positive for "consume" and negative for "refund" (cancellation). */
    private void adjustBalance(LeaveApplication app, BigDecimal delta) {
        int year = app.getStartDate().getYear();
        LeaveBalance bal = balanceRepository
            .findBySchoolIdAndStaffIdAndLeaveTypeAndYear(
                app.getSchoolId(), app.getStaffId(), app.getLeaveType(), year)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "No leave balance row for staff=" + app.getStaffId()
                    + " type=" + app.getLeaveType() + " year=" + year
                    + ". Seed entitlements first."));
        bal.setConsumedDays(bal.getConsumedDays().add(delta));
        balanceRepository.save(bal);
    }
}
