package in.schoolapp.fee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record InstallmentPlanRequest(
    @NotEmpty @Valid List<Installment> installments,
    @Size(max = 500) String notes
) {
    public record Installment(
        @NotNull LocalDate dueDate,
        @Positive long amountPaise
    ) {}
}
