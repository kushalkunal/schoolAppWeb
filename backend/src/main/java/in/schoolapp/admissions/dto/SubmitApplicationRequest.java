package in.schoolapp.admissions.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Partial update — every field optional; the service patches only non-null values. */
public record SubmitApplicationRequest(
    @Size(max = 200) String parentName,
    @Size(max = 255) String parentEmail,
    @Size(max = 100) String studentLastName,
    LocalDate studentDateOfBirth,
    @Size(max = 10) String studentGender,
    @Size(max = 20) String intendedSection,
    @Size(max = 1000) String notes
) {}
