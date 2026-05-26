package in.schoolapp.attendance.event;

import in.schoolapp.attendance.entity.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published after a section's attendance is committed. Absence-alert / analytics listeners
 * consume this asynchronously — the HTTP response returns before notifications are dispatched.
 */
public record AttendanceSubmittedEvent(
    UUID tenantId,
    UUID sectionId,
    LocalDate date,
    List<AttendanceRecord> absentRecords,
    List<AttendanceRecord> lateRecords,
    UUID submittedByStaffId
) {}
