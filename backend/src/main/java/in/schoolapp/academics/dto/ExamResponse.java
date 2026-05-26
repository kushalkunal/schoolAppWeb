package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamType;

import java.time.LocalDate;
import java.util.UUID;

public record ExamResponse(
    UUID id,
    UUID academicYearId,
    String name,
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    boolean published
) {
    public static ExamResponse from(Exam e) {
        return new ExamResponse(
            e.getId(),
            e.getAcademicYearId(),
            e.getName(),
            e.getExamType(),
            e.getStartDate(),
            e.getEndDate(),
            e.isPublished()
        );
    }
}
