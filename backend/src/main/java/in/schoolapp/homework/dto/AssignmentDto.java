package in.schoolapp.homework.dto;

import in.schoolapp.homework.entity.HomeworkAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentDto(
    UUID id,
    @NotNull UUID sectionId,
    UUID subjectId,
    @NotBlank String title,
    @NotBlank String body,
    String attachmentUrl,
    LocalDate dueDate,
    OffsetDateTime createdAt,
    UUID createdByStaffId
) {
    public static AssignmentDto from(HomeworkAssignment a) {
        return new AssignmentDto(a.getId(), a.getSectionId(), a.getSubjectId(),
            a.getTitle(), a.getBody(), a.getAttachmentUrl(), a.getDueDate(), a.getCreatedAt(),
            a.getCreatedByStaffId());
    }
}
