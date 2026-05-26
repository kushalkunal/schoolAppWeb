package in.schoolapp.academics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/** One row in the grid-style marks entry sheet. */
public record MarkEntryDto(
    @NotNull UUID studentId,
    @NotNull UUID subjectId,
    @NotNull @PositiveOrZero BigDecimal maxMarks,
    BigDecimal obtainedMarks,
    boolean absent
) {}
