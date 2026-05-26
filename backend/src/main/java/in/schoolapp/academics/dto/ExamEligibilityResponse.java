package in.schoolapp.academics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Attendance-%-based exam eligibility flag per student. A student is marked eligible when
 * their attendance percentage in the window meets or exceeds the school's configured
 * {@code minAttendancePct} (set at signup, stored in {@code school.settings.minAttendancePct}).
 * Students with zero recorded attendance days are returned with {@code eligible=false} and
 * {@code attendancePct=null} so the UI can flag them for manual review.
 */
public record ExamEligibilityResponse(
    UUID sectionId,
    LocalDate windowFrom,
    LocalDate windowTo,
    int minAttendancePct,
    List<StudentEligibility> students
) {
    public record StudentEligibility(
        UUID studentId,
        String studentName,
        Integer rollNumber,
        long markedDays,
        long absentDays,
        BigDecimal attendancePct,
        boolean eligible
    ) {}
}
