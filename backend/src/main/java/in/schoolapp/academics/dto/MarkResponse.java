package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ExamMark;

import java.math.BigDecimal;
import java.util.UUID;

public record MarkResponse(
    UUID id,
    UUID examId,
    UUID studentId,
    UUID subjectId,
    BigDecimal maxMarks,
    BigDecimal obtainedMarks,
    boolean absent,
    String grade,
    boolean draft
) {
    public static MarkResponse from(ExamMark m) {
        return new MarkResponse(
            m.getId(),
            m.getExamId(),
            m.getStudentId(),
            m.getSubjectId(),
            m.getMaxMarks(),
            m.getObtainedMarks(),
            m.isAbsent(),
            m.getGrade(),
            m.isDraft()
        );
    }
}
