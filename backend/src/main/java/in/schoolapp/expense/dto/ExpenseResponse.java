package in.schoolapp.expense.dto;

import in.schoolapp.expense.entity.Expense;

import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
    UUID id, UUID categoryId, long amountPaise, LocalDate spentOn,
    String vendor, String description, String receiptUrl, String paymentMode, boolean approved
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
            e.getId(), e.getCategoryId(), e.getAmountPaise(), e.getSpentOn(),
            e.getVendor(), e.getDescription(), e.getReceiptUrl(), e.getPaymentMode(), e.isApproved());
    }
}
