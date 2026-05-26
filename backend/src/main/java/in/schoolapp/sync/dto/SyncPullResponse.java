package in.schoolapp.sync.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Delta snapshot for the mobile client. {@code serverTime} is the wall-clock of this response —
 * the client stores it and passes it as {@code since} on the next pull so the server returns
 * only what changed between the two pulls.
 * <p>
 * {@code students} is delta-filtered by {@code updatedAt >= since}. {@code sections} and
 * {@code academicYear} are returned in full because neither has an {@code updated_at} column
 * yet and the volume is tiny (one school's worth of sections is typically under 100 rows).
 * Mobile clients should replace their local copies of these two on every pull.
 */
public record SyncPullResponse(
    OffsetDateTime serverTime,
    /** Null when no academic year is marked current for this tenant. */
    AcademicYearDto academicYear,
    List<SectionDto> sections,
    List<StudentDto> students,
    /** Number of student rows in the delta, for client-side progress bars. */
    int studentCount
) {
    public record AcademicYearDto(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record SectionDto(
        UUID id,
        UUID classId,
        UUID academicYearId,
        String name,
        UUID classTeacherId
    ) {}

    public record StudentDto(
        UUID id,
        String firstName,
        String lastName,
        String admissionNumber,
        String gender,
        LocalDate dateOfBirth,
        boolean active,
        OffsetDateTime updatedAt,
        /** Section id from the most recent enrollment — what the mobile app uses to display
         *  a student under the correct section. Null if the student has no enrollments. */
        UUID currentSectionId
    ) {}
}
