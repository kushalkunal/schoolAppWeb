package in.schoolapp.hr.dto;

import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveApplicationRequest(
    @NotNull UUID staffId,
    @NotNull LeaveType leaveType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull @DecimalMin("0.5") BigDecimal days,
    @Size(max = 1000) String reason
) {}
