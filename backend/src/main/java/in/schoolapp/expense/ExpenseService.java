package in.schoolapp.expense;

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

    @Transactional
    public ExpenseResponse create(UUID tenantId, CreateExpenseRequest req) {
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
        return ExpenseResponse.from(expenseRepo.save(e));
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
