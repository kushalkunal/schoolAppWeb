package in.schoolapp.academics.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTeacherAssignmentRequest(
    @NotNull UUID staffId,
    @NotNull UUID subjectId,
    @NotNull UUID sectionId
) {}
