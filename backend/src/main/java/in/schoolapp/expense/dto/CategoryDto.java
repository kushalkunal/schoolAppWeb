package in.schoolapp.expense.dto;

import in.schoolapp.expense.entity.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryDto(
    UUID id,
    @NotBlank @Size(max = 80) String name,
    boolean active
) {
    public static CategoryDto from(ExpenseCategory c) {
        return new CategoryDto(c.getId(), c.getName(), c.isActive());
    }
}
