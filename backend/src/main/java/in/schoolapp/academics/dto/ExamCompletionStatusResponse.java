package in.schoolapp.academics.dto;

import java.util.List;
import java.util.UUID;

/**
 * "How many subjects have marks entered for this exam + section" — drives the
 * class-teacher "which subjects are still pending" dashboard per LLD §6.1.
 */
public record ExamCompletionStatusResponse(
    UUID examId,
    UUID sectionId,
    int totalStudents,
    List<SubjectCompletion> perSubject
) {
    public record SubjectCompletion(
        UUID subjectId,
        String subjectName,
        int enteredCount,
        int totalStudents,
        boolean complete
    ) {}
}
