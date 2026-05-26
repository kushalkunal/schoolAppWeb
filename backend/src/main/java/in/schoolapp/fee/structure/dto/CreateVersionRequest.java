package in.schoolapp.fee.structure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVersionRequest(
    @NotNull UUID academicYearId,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 5000) String notes
) {}
