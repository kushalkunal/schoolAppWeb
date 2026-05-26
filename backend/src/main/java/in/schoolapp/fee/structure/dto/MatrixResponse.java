package in.schoolapp.fee.structure.dto;

import java.util.List;

public record MatrixResponse(
    FeeStructureVersionResponse version,
    List<TermDto> terms,
    List<MatrixRowDto> rows
) {}
