package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;

import java.time.LocalDate;
import java.util.UUID;

public record StaffResponse(
    UUID id,
    UUID schoolId,
    String firstName,
    String lastName,
    String displayName,
    String phone,
    String email,
    String gender,
    LocalDate dateOfJoining,
    StaffRole role,
    boolean active,
    boolean mustResetPassword
) {
    public static StaffResponse from(Staff s) {
        return new StaffResponse(
            s.getId(),
            s.getSchoolId(),
            s.getFirstName(),
            s.getLastName(),
            s.displayName(),
            s.getPhone(),
            s.getEmail(),
            s.getGender(),
            s.getDateOfJoining(),
            s.getRole(),
            s.isActive(),
            s.isMustResetPassword()
        );
    }
}
