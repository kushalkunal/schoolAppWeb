package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamType;
import in.schoolapp.academics.entity.FeePolicy;
import in.schoolapp.academics.entity.ResultStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExamResponse(
    UUID id,
    UUID academicYearId,
    String name,
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    UUID classId,
    UUID sectionId,
    List<UUID> participatingClassIds,
    FeePolicy feePolicy,
    boolean published,
    ResultStatus resultStatus
) {
    public static ExamResponse from(Exam e) {
        return from(e, List.of());
    }

    public static ExamResponse from(Exam e, List<UUID> participatingClassIds) {
        return new ExamResponse(
            e.getId(),
            e.getAcademicYearId(),
            e.getName(),
            e.getExamType(),
            e.getStartDate(),
            e.getEndDate(),
            e.getClassId(),
            e.getSectionId(),
            participatingClassIds,
            e.getFeePolicy(),
            e.isPublished(),
            e.getResultStatus()
        );
    }
}
