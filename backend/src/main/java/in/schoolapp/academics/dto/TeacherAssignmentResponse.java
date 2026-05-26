package in.schoolapp.academics.dto;

import in.schoolapp.academics.entity.TeacherSubjectAssignment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeacherAssignmentResponse(
    UUID id,
    UUID staffId,
    UUID subjectId,
    UUID sectionId,
    UUID academicYearId,
    OffsetDateTime createdAt
) {
    public static TeacherAssignmentResponse from(TeacherSubjectAssignment a) {
        return new TeacherAssignmentResponse(
            a.getId(), a.getStaffId(), a.getSubjectId(), a.getSectionId(),
            a.getAcademicYearId(), a.getCreatedAt());
    }
}
