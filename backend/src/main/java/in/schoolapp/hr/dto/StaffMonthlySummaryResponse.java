package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record StaffMonthlySummaryResponse(
    UUID staffId,
    int year,
    int month,
    int totalCalendarDays,
    Map<StaffAttendanceStatus, Integer> counts,
    /** Half-day counts as 0.5; matches payroll's working-day computation. */
    BigDecimal workingDays
) {}
