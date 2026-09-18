package in.schoolapp.academics.dto;

import java.util.List;
import java.util.UUID;

/** Auto-enrollment preview: how many active students each participating class contributes. */
public record ExamEnrollmentSummaryResponse(
    int totalStudents,
    List<ClassCount> classes
) {
    public record ClassCount(UUID classId, String className, int studentCount) {}
}
