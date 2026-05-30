package in.schoolapp.academics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full marks-entry sheet for a section — all students × all subject-components.
 * The teacher sees this as a grid; rows = students, columns = subject-components.
 * <p>
 * {@code locked=true} means the class teacher has done "Submit All (Final)" for this section.
 * Subject teachers and class teachers can no longer edit. Principal/Admin can still override.
 */
public record ComponentMarksSheetResponse(
    UUID examId,
    UUID sectionId,
    boolean locked,
    String lockedByName,
    Instant lockedAt,
    boolean isOwnClassTeacher,
    List<StudentRow> students
) {
    public record StudentRow(
        UUID studentId,
        String name,
        Integer rollNumber,
        List<SubjectEntry> subjects
    ) {}

    public record SubjectEntry(
        UUID subjectId,
        String subjectName,
        List<ComponentEntry> components
    ) {}

    public record ComponentEntry(
        UUID configId,
        String componentName,
        BigDecimal maxMarks,
        BigDecimal passingMarks,
        BigDecimal obtained,
        boolean absent,
        boolean draft,
        String remarks
    ) {}
}
