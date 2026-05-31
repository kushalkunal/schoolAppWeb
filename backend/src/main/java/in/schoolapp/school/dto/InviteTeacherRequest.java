package in.schoolapp.school.dto;

import in.schoolapp.school.entity.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for the teacher-invite endpoint — a single-step registration that captures all
 * the teacher's details at once (personal info + optional class-teacher assignment), instead of
 * inviting first and editing details afterwards.
 * Only CLASS_TEACHER / SUBJECT_TEACHER / LIBRARIAN are allowed. Phone is optional — the teacher
 * logs in via email + system-generated temp password.
 */
public record InviteTeacherRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 255) String email,
    String phone,
    @NotNull StaffRole role,
    // Optional details, previously collected in a second edit step:
    @Size(max = 20) String gender,
    LocalDate dateOfJoining,
    /** If set and role is CLASS_TEACHER, the teacher is made class teacher of this section. */
    UUID classTeacherSectionId
) {}
