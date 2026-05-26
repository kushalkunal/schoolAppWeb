package in.schoolapp.admissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Public-facing enquiry payload. Only the bare minimum is required; the school converts
 * to an application later and asks for the rest.
 */
public record EnquiryRequest(
    @Size(max = 200) String parentName,
    @NotBlank @Size(max = 20) String parentPhone,
    @Email @Size(max = 255) String parentEmail,
    @NotBlank @Size(max = 100) String studentFirstName,
    @Size(max = 100) String studentLastName,
    LocalDate studentDateOfBirth,
    @Size(max = 10) String studentGender,
    @NotBlank @Size(max = 50) String intendedClass,
    @Size(max = 20) String intendedSection,
    @Size(max = 20) String intendedAcademicYear,
    @Size(max = 40) String source,
    @Size(max = 200) String referrerName,
    @Size(max = 1000) String notes
) {}
