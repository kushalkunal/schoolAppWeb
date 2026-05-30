package in.schoolapp.expense;

import in.schoolapp.approval.ApprovalService;
import in.schoolapp.approval.dto.ApprovalRequestResponse;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.expense.dto.CategoryDto;
import in.schoolapp.expense.dto.CreateExpenseRequest;
import in.schoolapp.expense.dto.ExpenseResponse;
import in.schoolapp.expense.entity.Expense;
import in.schoolapp.expense.entity.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final ExpenseCategoryRepository categoryRepo;
    private final ApprovalService approvalService;

    // ---- Categories ----

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(UUID tenantId) {
        return categoryRepo.findBySchoolIdAndActiveTrueOrderByName(tenantId).stream()
            .map(CategoryDto::from).toList();
    }

    @Transactional
    public CategoryDto createCategory(UUID tenantId, String name) {
        ExpenseCategory c = new ExpenseCategory();
        c.setSchoolId(tenantId);
        c.setName(name.trim());
        c.setActive(true);
        return CategoryDto.from(categoryRepo.save(c));
    }

    // ---- Expenses ----

    /**
     * Stages an expense for maker-checker approval (audit #8). The {@link Expense} is saved
     * {@code approved=false} so it is excluded from totals until a different user approves the
     * returned request; {@link ExpenseApprovalHandler} then marks it approved.
     */
    @Transactional
    public ApprovalRequestResponse create(UUID tenantId, CreateExpenseRequest req) {
        Expense e = new Expense();
        e.setSchoolId(tenantId);
        e.setCategoryId(req.categoryId());
        e.setAmountPaise(req.amountPaise());
        e.setSpentOn(req.spentOn());
        e.setVendor(req.vendor());
        e.setDescription(req.description());
        e.setReceiptUrl(req.receiptUrl());
        e.setPaymentMode(req.paymentMode());
        e.setRecordedById(TenantContext.getStaffId());
        e.setApproved(false);
        e = expenseRepo.save(e);

        String summary = "Expense ₹" + (req.amountPaise() / 100)
            + (req.vendor() != null ? " to " + req.vendor() : "")
            + " on " + req.spentOn();
        var approval = approvalService.submit(
            tenantId, ApprovalType.EXPENSE, e.getId(), null, req.amountPaise(), summary);
        return ApprovalRequestResponse.from(approval);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        Expense e = expenseRepo.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Expense", id));
        expenseRepo.delete(e);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(UUID tenantId, int page, int size) {
        return expenseRepo.findBySchoolIdOrderBySpentOnDesc(tenantId, PageRequest.of(page, size))
            .map(ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    public long sumBetween(UUID tenantId, LocalDate from, LocalDate to) {
        return expenseRepo.sumBetween(tenantId, from, to);
    }
}
