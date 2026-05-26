package in.schoolapp.school.dto;

import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;

import java.util.UUID;

public record StaffResponse(
    UUID id,
    UUID schoolId,
    String firstName,
    String lastName,
    String displayName,
    String phone,
    String email,
    StaffRole role,
    boolean active
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
            s.getRole(),
            s.isActive()
        );
    }
}
