package in.schoolapp.fee.structure.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replaces the entire matrix for a DRAFT version in one shot. Activated versions are immutable;
 * to change them, clone into a new DRAFT and re-activate.
 */
public record UpdateMatrixRequest(
    @NotNull @Valid List<TermDto> terms,
    @NotNull @Valid List<MatrixRowDto> rows
) {}
