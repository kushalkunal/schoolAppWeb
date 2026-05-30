package in.schoolapp.attendance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /sections/{sectionId}/attendance?date=
 * Wraps the attendance records alongside section-lock information.
 */
public record AttendanceSectionResponse(
    UUID sectionId,
    String date,
    boolean locked,
    String lockedByName,
    Instant lockedAt,
    List<AttendanceRecordResponse> records
) {}
