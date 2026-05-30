package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.AdmitCard;
import in.schoolapp.academics.entity.AdmitCardStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdmitCardResponse(
    UUID id,
    UUID examId,
    UUID studentId,
    String studentName,
    String admissionNumber,
    String className,
    String sectionName,
    Integer rollNumber,
    String admitCardNo,
    String seatNumber,
    AdmitCardStatus status,
    boolean feeCleared,
    long outstandingPaiseSnapshot,
    String pdfUrl,
    OffsetDateTime generatedAt
) {
    /** Build from entity + student / enrollment details. */
    public static AdmitCardResponse from(
        AdmitCard card,
        String studentName,
        String admissionNumber,
        String className,
        String sectionName,
        Integer rollNumber
    ) {
        return new AdmitCardResponse(
            card.getId(),
            card.getExamId(),
            card.getStudentId(),
            studentName,
            admissionNumber,
            className,
            sectionName,
            rollNumber,
            card.getAdmitCardNo(),
            card.getSeatNumber(),
            card.getStatus(),
            card.isFeeCleared(),
            card.getOutstandingPaiseSnapshot(),
            card.getPdfUrl(),
            card.getGeneratedAt()
        );
    }
}
