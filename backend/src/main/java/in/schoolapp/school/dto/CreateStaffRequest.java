package in.schoolapp.school.dto;

import in.schoolapp.school.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @NotBlank String phone,
    @Size(max = 255) String email,
    @NotNull StaffRole role
) {}
