package in.schoolapp.student.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cross-year record for a single student (LLD §5.1 "Full student timeline"). Three data
 * streams, each sorted most-recent first:
 * <ul>
 *   <li>{@code enrollments} — class/section per academic year</li>
 *   <li>{@code reportCards} — published exam results</li>
 *   <li>{@code attendanceSummaries} — year-over-year attendance rollup</li>
 * </ul>
 */
public record StudentTimelineResponse(
    UUID studentId,
    String studentName,
    List<EnrollmentEntry> enrollments,
    List<ReportCardEntry> reportCards,
    List<AttendanceYearSummary> attendanceSummaries
) {
    public record EnrollmentEntry(
        UUID enrollmentId,
        UUID academicYearId,
        String academicYearName,
        UUID sectionId,
        String sectionName,
        String className,
        Integer rollNumber,
        String status,
        OffsetDateTime createdAt
    ) {}

    public record ReportCardEntry(
        UUID reportCardId,
        UUID examId,
        String examName,
        BigDecimal percentage,
        String grade,
        Integer rankInClass,
        OffsetDateTime createdAt
    ) {}

    public record AttendanceYearSummary(
        UUID academicYearId,
        String academicYearName,
        LocalDate windowFrom,
        LocalDate windowTo,
        long totalDays,
        long absentDays,
        BigDecimal attendancePct
    ) {}
}
