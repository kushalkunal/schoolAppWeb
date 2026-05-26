package in.schoolapp.student.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Patch-style update — only non-null fields are applied. */
public record UpdateStudentRequest(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 10) String gender,
    LocalDate dateOfBirth,
    @Size(max = 5) String bloodGroup,
    String address
) {}
