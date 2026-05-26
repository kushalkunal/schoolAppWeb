package in.schoolapp.attendance.dto;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceRecordResponse(
    UUID id,
    UUID studentId,
    UUID sectionId,
    LocalDate date,
    AttendanceStatus status,
    OffsetDateTime arrivalTime,
    String note
) {
    public static AttendanceRecordResponse from(AttendanceRecord r) {
        return new AttendanceRecordResponse(
            r.getId(), r.getStudentId(), r.getSectionId(),
            r.getDate(), r.getStatus(), r.getArrivalTime(), r.getNote()
        );
    }
}
