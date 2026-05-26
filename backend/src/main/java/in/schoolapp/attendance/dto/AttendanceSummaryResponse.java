package in.schoolapp.attendance.dto;

import java.time.LocalDate;

/**
 * School-wide attendance snapshot for a single day — feeds the principal dashboard and the
 * daily digest WhatsApp. Counts are totals across the school; the "marked" count excludes
 * sections that haven't submitted yet.
 */
public record AttendanceSummaryResponse(
    LocalDate date,
    long totalMarked,
    long present,
    long absent,
    long late,
    long halfDay,
    long leave
) {}
