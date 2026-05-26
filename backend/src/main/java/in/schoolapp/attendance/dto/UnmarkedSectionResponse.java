package in.schoolapp.attendance.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UnmarkedSectionResponse(
    UUID sectionId,
    String sectionName,
    String className,
    UUID classTeacherId,
    String classTeacherName,
    LocalDate date
) {}
