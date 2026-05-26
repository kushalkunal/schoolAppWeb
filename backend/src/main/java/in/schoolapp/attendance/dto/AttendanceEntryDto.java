package in.schoolapp.attendance.dto;

import in.schoolapp.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceEntryDto(
    @NotNull UUID studentId,
    @NotNull AttendanceStatus status,
    OffsetDateTime arrivalTime,
    String note
) {}
