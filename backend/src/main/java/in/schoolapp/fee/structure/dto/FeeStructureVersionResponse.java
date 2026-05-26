package in.schoolapp.fee.structure.dto;

import in.schoolapp.fee.structure.entity.FeeStructureVersion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FeeStructureVersionResponse(
    UUID id,
    UUID academicYearId,
    String name,
    String status,
    String notes,
    OffsetDateTime activatedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt
) {
    public static FeeStructureVersionResponse from(FeeStructureVersion v) {
        return new FeeStructureVersionResponse(
            v.getId(),
            v.getAcademicYearId(),
            v.getName(),
            v.getStatus().name(),
            v.getNotes(),
            v.getActivatedAt(),
            v.getArchivedAt(),
            v.getCreatedAt()
        );
    }
}
