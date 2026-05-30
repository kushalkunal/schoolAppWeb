package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ExamResult;
import in.schoolapp.academics.entity.ResultStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Computed result for one student in one exam. */
public record ExamResultResponse(
    UUID id,
    UUID studentId,
    String studentName,
    String admissionNumber,
    Integer rollNumber,
    UUID sectionId,
    BigDecimal totalMax,
    BigDecimal totalObtained,
    BigDecimal percentage,
    String grade,
    Integer rankInSection,
    boolean pass,
    ResultStatus status,
    OffsetDateTime computedAt,
    OffsetDateTime publishedAt
) {
    /** Constructs from entity; caller injects the student display fields. */
    public static ExamResultResponse from(ExamResult r, String studentName,
                                          String admissionNumber, Integer rollNumber) {
        return new ExamResultResponse(
            r.getId(),
            r.getStudentId(),
            studentName,
            admissionNumber,
            rollNumber,
            r.getSectionId(),
            r.getTotalMax(),
            r.getTotalObtained(),
            r.getPercentage(),
            r.getGrade(),
            r.getRankInSection(),
            r.isPass(),
            r.getStatus(),
            r.getComputedAt(),
            r.getPublishedAt()
        );
    }
}
