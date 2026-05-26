package in.schoolapp.cashrecon.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record CloseDrawerRequest(
    @NotNull LocalDate date,
    @PositiveOrZero long countedCashPaise,
    @PositiveOrZero long countedUpiPaise,
    @PositiveOrZero long countedChequePaise,
    @PositiveOrZero long countedOtherPaise,
    String notes
) {}
