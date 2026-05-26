package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record StaffAttendanceRequest(
    @NotNull UUID staffId,
    @NotNull LocalDate date,
    @NotNull StaffAttendanceStatus status,
    @Size(max = 500) String notes
) {}
