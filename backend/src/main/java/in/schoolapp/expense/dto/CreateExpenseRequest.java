package in.schoolapp.expense.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseRequest(
    UUID categoryId,
    @PositiveOrZero long amountPaise,
    @NotNull LocalDate spentOn,
    @Size(max = 120) String vendor,
    String description,
    String receiptUrl,
    @Size(max = 20) String paymentMode
) {}
