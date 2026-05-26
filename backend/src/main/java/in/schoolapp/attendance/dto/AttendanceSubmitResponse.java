package in.schoolapp.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AttendanceSubmitResponse(
    LocalDate date,
    UUID sectionId,
    int total,
    int present,
    int absent,
    int late,
    int halfDay,
    int onLeave,
    List<AttendanceRecordResponse> records,
    int notificationsQueued
) {}
