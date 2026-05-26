package in.schoolapp.attendance.dto;

import java.util.UUID;

public record ChronicAbsenteeResponse(
    UUID studentId,
    String studentName,
    UUID sectionId,
    String sectionName,
    String className,
    long absentDays,
    int windowDays
) {}
