package in.schoolapp.fee.structure.dto;

import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TermDto(
    @Min(1) int termNumber,
    @NotBlank @Size(max = 80) String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull LocalDate dueDate
) {
    public static TermDto from(FeeStructureTerm t) {
        return new TermDto(t.getTermNumber(), t.getName(), t.getStartDate(), t.getEndDate(), t.getDueDate());
    }
}
