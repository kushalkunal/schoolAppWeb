package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.ReportCard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportCardResponse(
    UUID id,
    UUID studentId,
    UUID examId,
    BigDecimal totalMarks,
    BigDecimal obtainedMarks,
    BigDecimal percentage,
    String grade,
    Integer rankInClass,
    String pdfUrl,
    OffsetDateTime waSentAt
) {
    public static ReportCardResponse from(ReportCard r) {
        return new ReportCardResponse(
            r.getId(),
            r.getStudentId(),
            r.getExamId(),
            r.getTotalMarks(),
            r.getObtainedMarks(),
            r.getPercentage(),
            r.getGrade(),
            r.getRankInClass(),
            r.getPdfUrl(),
            r.getWaSentAt()
        );
    }
}
