package in.schoolapp.homework.dto;

import in.schoolapp.homework.entity.HomeworkSubmission;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmissionDto(
    UUID id,
    @NotNull UUID assignmentId,
    @NotNull UUID studentId,
    String submissionText,
    String attachmentUrl,
    String teacherRemark,
    String grade,
    OffsetDateTime submittedAt,
    OffsetDateTime gradedAt
) {
    public static SubmissionDto from(HomeworkSubmission s) {
        return new SubmissionDto(s.getId(), s.getAssignmentId(), s.getStudentId(),
            s.getSubmissionText(), s.getAttachmentUrl(), s.getTeacherRemark(),
            s.getGrade(), s.getSubmittedAt(), s.getGradedAt());
    }
}
