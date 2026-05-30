package in.schoolapp.school.dto;

import in.schoolapp.school.entity.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for the teacher-invite endpoint.
 * Only CLASS_TEACHER / SUBJECT_TEACHER are allowed via this flow.
 * Phone is optional — the teacher logs in via email + system-generated temp password.
 */
public record InviteTeacherRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 255) String email,
    String phone,
    @NotNull StaffRole role   // must be CLASS_TEACHER or SUBJECT_TEACHER
) {}
