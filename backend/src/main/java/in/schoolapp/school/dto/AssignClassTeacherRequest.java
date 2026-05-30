package in.schoolapp.school.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for PATCH /sections/{sectionId}/class-teacher */
public record AssignClassTeacherRequest(
    @NotNull UUID staffId
) {}
