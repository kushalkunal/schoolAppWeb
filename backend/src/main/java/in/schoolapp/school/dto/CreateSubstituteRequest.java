package in.schoolapp.school.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSubstituteRequest(
    @NotNull UUID absentTeacherId,
    @NotNull UUID substituteId,
    @NotNull UUID sectionId,
    @NotNull LocalDate assignedDate,
    String note
) {}
