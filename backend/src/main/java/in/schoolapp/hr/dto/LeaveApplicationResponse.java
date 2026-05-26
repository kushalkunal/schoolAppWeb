package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.LeaveApplication.LeaveStatus;
import in.schoolapp.hr.entity.LeaveApplication.LeaveType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LeaveApplicationResponse(
    UUID id,
    UUID staffId,
    LeaveType leaveType,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal days,
    String reason,
    LeaveStatus status,
    UUID decidedById,
    OffsetDateTime decidedAt,
    String decisionNote,
    OffsetDateTime createdAt
) {
    public static LeaveApplicationResponse from(LeaveApplication a) {
        return new LeaveApplicationResponse(
            a.getId(), a.getStaffId(), a.getLeaveType(),
            a.getStartDate(), a.getEndDate(), a.getDays(),
            a.getReason(), a.getStatus(),
            a.getDecidedById(), a.getDecidedAt(), a.getDecisionNote(),
            a.getCreatedAt());
    }
}
