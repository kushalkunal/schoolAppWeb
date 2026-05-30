package in.schoolapp.academics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/** One cell in the component marks grid — one student × one component. */
public record ComponentMarkEntryDto(
    @NotNull UUID studentId,
    @NotNull UUID configId,
    @PositiveOrZero BigDecimal obtained,
    boolean absent,
    String remarks
) {}
