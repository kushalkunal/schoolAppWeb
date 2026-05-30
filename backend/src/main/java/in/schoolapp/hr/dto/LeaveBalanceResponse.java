package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.LeaveBalance;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceResponse(
    UUID id,
    UUID staffId,
    LeaveType leaveType,
    int year,
    BigDecimal entitledDays,
    BigDecimal consumedDays,
    BigDecimal remainingDays
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        return new LeaveBalanceResponse(
            b.getId(),
            b.getStaffId(),
            b.getLeaveType(),
            b.getYear(),
            b.getEntitledDays(),
            b.getConsumedDays(),
            b.remainingDays()
        );
    }
}
