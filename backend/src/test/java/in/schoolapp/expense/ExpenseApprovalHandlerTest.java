package in.schoolapp.expense;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.TenantContext;
import in.schoolapp.expense.entity.Expense;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseApprovalHandlerTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock AuditLogger audit;
    @InjectMocks ExpenseApprovalHandler handler;

    final UUID tenant = UUID.randomUUID();
    final UUID approver = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void approvalMarksExpenseApprovedAndStampsApprover() {
        UUID expenseId = UUID.randomUUID();
        Expense e = new Expense();
        e.setId(expenseId);
        e.setSchoolId(tenant);
        e.setAmountPaise(50_000);
        e.setApproved(false);
        when(expenseRepository.findByIdAndSchoolId(expenseId, tenant)).thenReturn(Optional.of(e));

        TenantContext.set(tenant, approver, "PRINCIPAL");
        ApprovalRequest req = new ApprovalRequest();
        req.setSchoolId(tenant);
        req.setSubjectId(expenseId);

        handler.apply(req);

        assertThat(e.isApproved()).isTrue();
        assertThat(e.getApprovedById()).isEqualTo(approver);
        verify(expenseRepository).save(e);
        verify(audit).logAction(org.mockito.ArgumentMatchers.eq(tenant),
            org.mockito.ArgumentMatchers.eq("Expense"), org.mockito.ArgumentMatchers.eq(expenseId),
            org.mockito.ArgumentMatchers.eq("EXPENSE_APPROVED"), org.mockito.ArgumentMatchers.any());
    }
}
