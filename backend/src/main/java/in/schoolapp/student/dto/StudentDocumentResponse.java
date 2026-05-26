package in.schoolapp.student.dto;

import in.schoolapp.student.entity.StudentDocument;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentDocumentResponse(
    UUID id,
    UUID studentId,
    String docType,
    String fileUrl,
    String fileName,
    String contentType,
    Long sizeBytes,
    OffsetDateTime createdAt
) {
    public static StudentDocumentResponse from(StudentDocument d) {
        return new StudentDocumentResponse(
            d.getId(),
            d.getStudentId(),
            d.getDocType() == null ? null : d.getDocType().name(),
            d.getFileUrl(),
            d.getFileName(),
            d.getContentType(),
            d.getSizeBytes(),
            d.getCreatedAt()
        );
    }
}
