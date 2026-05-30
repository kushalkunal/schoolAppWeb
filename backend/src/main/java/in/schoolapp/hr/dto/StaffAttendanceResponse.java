package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffAttendanceResponse(
    UUID id,
    UUID staffId,
    String staffName,
    LocalDate date,
    StaffAttendanceStatus status,
    String notes,
    boolean approved,
    UUID approvedById,
    String approvedByName,
    OffsetDateTime approvedAt
) {
    public static StaffAttendanceResponse from(StaffAttendance r) {
        return new StaffAttendanceResponse(
            r.getId(), r.getStaffId(), null, r.getAttendanceDate(), r.getStatus(), r.getNotes(),
            r.isApproved(), r.getApprovedById(), null, r.getApprovedAt());
    }
}
