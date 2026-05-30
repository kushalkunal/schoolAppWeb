package in.schoolapp.student.dto;

import in.schoolapp.student.entity.StudentLeaveApplication;
import in.schoolapp.student.entity.StudentLeaveApplication.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentLeaveDto(
    UUID id,
    UUID studentId,
    @NotNull UUID sectionId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Positive int days,
    String reason,
    LeaveStatus status,
    UUID appliedByStaffId,
    UUID decidedByStaffId,
    String decisionNote,
    OffsetDateTime decidedAt,
    OffsetDateTime createdAt
) {
    public static StudentLeaveDto from(StudentLeaveApplication a) {
        return new StudentLeaveDto(
            a.getId(), a.getStudentId(), a.getSectionId(),
            a.getStartDate(), a.getEndDate(), a.getDays(),
            a.getReason(), a.getStatus(),
            a.getAppliedByStaffId(), a.getDecidedByStaffId(),
            a.getDecisionNote(), a.getDecidedAt(), a.getCreatedAt()
        );
    }
}
