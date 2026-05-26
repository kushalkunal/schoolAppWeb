package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;

import java.time.LocalDate;
import java.util.UUID;

public record StaffAttendanceResponse(
    UUID id,
    UUID staffId,
    LocalDate date,
    StaffAttendanceStatus status,
    String notes
) {
    public static StaffAttendanceResponse from(StaffAttendance r) {
        return new StaffAttendanceResponse(
            r.getId(), r.getStaffId(), r.getAttendanceDate(), r.getStatus(), r.getNotes());
    }
}
