package in.schoolapp.fee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID studentId,
    UUID feeHeadId,
    @NotNull @Positive Long amountDuePaise,
    LocalDate dueDate,
    @Size(max = 500) String description
) {}
