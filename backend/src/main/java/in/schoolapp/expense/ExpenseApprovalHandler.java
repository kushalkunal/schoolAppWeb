package in.schoolapp.expense;

import in.schoolapp.approval.ApprovalHandler;
import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.expense.entity.Expense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Marks an expense approved once its maker-checker request is granted, stamping the approver. Until
 * then the expense is {@code approved=false} and excluded from totals. Depends only on the
 * repository (never {@link ExpenseService}) so there is no cycle through the approval engine.
 */
@Component
@RequiredArgsConstructor
public class ExpenseApprovalHandler implements ApprovalHandler {

    private final ExpenseRepository expenseRepository;
    private final AuditLogger audit;

    @Override
    public ApprovalType type() {
        return ApprovalType.EXPENSE;
    }

    @Override
    public void apply(ApprovalRequest request) {
        Expense e = expenseRepository.findByIdAndSchoolId(request.getSubjectId(), request.getSchoolId())
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Expense", request.getSubjectId()));
        e.setApproved(true);
        e.setApprovedById(TenantContext.getStaffId());
        expenseRepository.save(e);
        audit.logAction(request.getSchoolId(), "Expense", e.getId(), "EXPENSE_APPROVED",
            Map.of("amountPaise", e.getAmountPaise(), "vendor", String.valueOf(e.getVendor())));
    }
}
