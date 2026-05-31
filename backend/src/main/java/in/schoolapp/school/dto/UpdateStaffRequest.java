package in.schoolapp.school.dto;

import in.schoolapp.school.entity.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial-update payload for an existing staff member.
 * All fields are nullable — only non-null values are applied.
 */
public record UpdateStaffRequest(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 15)  String phone,
    @Email @Size(max = 255) String email,
    @Size(max = 10)  String gender,
    LocalDate dateOfJoining,
    StaffRole role,
    @jakarta.validation.Valid StaffProfile profile
) {}
