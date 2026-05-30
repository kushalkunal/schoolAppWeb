package in.schoolapp.hr;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.hr.dto.LeaveDecisionRequest;
import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.LeaveApplication.LeaveStatus;
import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.LeaveBalance;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.LeaveBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the leave segregation-of-duties + balance rules (audit #10): no self-approval, no approving
 * without a configured entitlement, no exceeding remaining balance.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock LeaveApplicationRepository applicationRepository;
    @Mock LeaveBalanceRepository balanceRepository;
    @Mock AuditLogger audit;
    LeaveService service;

    final UUID tenant = UUID.randomUUID();
    final UUID applicant = UUID.randomUUID();
    final UUID approver = UUID.randomUUID();
    final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LeaveService(applicationRepository, balanceRepository, audit);
        lenient().when(applicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(balanceRepository.save(any(LeaveBalance.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(applicationRepository.findByIdAndSchoolId(appId, tenant))
            .thenReturn(Optional.of(submittedApplication()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void approverCannotApproveOwnLeave() {
        TenantContext.set(tenant, applicant, "PRINCIPAL");   // approver == applicant

        assertThatThrownBy(() -> service.decide(tenant, appId, new LeaveDecisionRequest(true, "ok")))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_SELF_NOT_ALLOWED);

        verify(balanceRepository, never()).save(any());
    }

    @Test
    void approvalFailsWhenNoEntitlementConfigured() {
        TenantContext.set(tenant, approver, "PRINCIPAL");
        when(balanceRepository.findBySchoolIdAndStaffIdAndLeaveTypeAndYear(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(tenant, appId, new LeaveDecisionRequest(true, null)))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void approvalFailsWhenBalanceInsufficient() {
        TenantContext.set(tenant, approver, "PRINCIPAL");
        when(balanceRepository.findBySchoolIdAndStaffIdAndLeaveTypeAndYear(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(Optional.of(balance(new BigDecimal("1"), BigDecimal.ZERO)));   // 1 entitled, asking 2

        assertThatThrownBy(() -> service.decide(tenant, appId, new LeaveDecisionRequest(true, null)))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.LEAVE_BALANCE_INSUFFICIENT);
    }

    @Test
    void approvalConsumesBalanceAndAudits() {
        TenantContext.set(tenant, approver, "PRINCIPAL");
        LeaveBalance bal = balance(new BigDecimal("10"), BigDecimal.ZERO);
        when(balanceRepository.findBySchoolIdAndStaffIdAndLeaveTypeAndYear(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(Optional.of(bal));

        service.decide(tenant, appId, new LeaveDecisionRequest(true, "approved"));

        assertThat(bal.getConsumedDays()).isEqualByComparingTo("2");
        verify(audit).logAction(org.mockito.ArgumentMatchers.eq(tenant),
            org.mockito.ArgumentMatchers.eq("LeaveApplication"), any(),
            org.mockito.ArgumentMatchers.eq("LEAVE_APPROVED"), any());
    }

    private LeaveApplication submittedApplication() {
        LeaveApplication app = new LeaveApplication();
        app.setId(appId);
        app.setSchoolId(tenant);
        app.setStaffId(applicant);
        app.setLeaveType(LeaveType.CASUAL);
        app.setStartDate(LocalDate.of(2026, 6, 1));
        app.setEndDate(LocalDate.of(2026, 6, 2));
        app.setDays(new BigDecimal("2"));
        app.setStatus(LeaveStatus.SUBMITTED);
        return app;
    }

    private LeaveBalance balance(BigDecimal entitled, BigDecimal consumed) {
        LeaveBalance bal = new LeaveBalance();
        bal.setSchoolId(tenant);
        bal.setStaffId(applicant);
        bal.setLeaveType(LeaveType.CASUAL);
        bal.setYear(2026);
        bal.setEntitledDays(entitled);
        bal.setConsumedDays(consumed);
        return bal;
    }
}
