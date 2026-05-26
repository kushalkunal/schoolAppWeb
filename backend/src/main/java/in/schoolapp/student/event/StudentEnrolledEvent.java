package in.schoolapp.student.event;

import java.util.UUID;

/**
 * Fired when a {@code student_enrollments} row is created — i.e. a student becomes ACTIVE in a
 * section for an academic year. Listeners include the fee-structure auto-invoice generator
 * (Slice 31d): mid-year admits get pro-rated invoices for current + future terms of the
 * ACTIVE fee structure, without anyone clicking "Generate" again.
 * <p>
 * Published from {@code StudentService.createStudent} and {@code createHistoricalStudent}.
 * The admission flow ({@code AdmissionService.enrollStudent}) calls {@code createStudent} so
 * a single publish point covers both new-student and converted-admission paths.
 */
public record StudentEnrolledEvent(
    UUID tenantId,
    UUID studentId,
    UUID sectionId,
    UUID academicYearId,
    UUID classId
) {}
