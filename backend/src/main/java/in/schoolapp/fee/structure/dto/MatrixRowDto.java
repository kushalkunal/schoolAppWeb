package in.schoolapp.fee.structure.dto;

import in.schoolapp.fee.structure.entity.FeeStructureRow;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * One cell in the (class × head × term) matrix. {@code termNumber == null} means the cell
 * applies to the whole academic year (annual billing).
 */
public record MatrixRowDto(
    @NotNull UUID classId,
    @NotNull UUID feeHeadId,
    Integer termNumber,
    @PositiveOrZero long amountPaise,
    boolean optional
) {
    public static MatrixRowDto from(FeeStructureRow r) {
        return new MatrixRowDto(
            r.getClassId(),
            r.getFeeHeadId(),
            r.getTermNumber(),
            r.getAmountPaise(),
            r.isOptional()
        );
    }
}
