package in.schoolapp.school.dto;

import in.schoolapp.school.entity.SubstituteAssignment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SubstituteResponse(
    UUID id,
    UUID absentTeacherId,
    UUID substituteId,
    UUID sectionId,
    LocalDate assignedDate,
    String note,
    UUID createdById,
    OffsetDateTime createdAt
) {
    public static SubstituteResponse from(SubstituteAssignment a) {
        return new SubstituteResponse(
            a.getId(),
            a.getAbsentTeacherId(),
            a.getSubstituteId(),
            a.getSectionId(),
            a.getAssignedDate(),
            a.getNote(),
            a.getCreatedById(),
            a.getCreatedAt()
        );
    }
}
