package in.schoolapp.hr.dto;

import jakarta.validation.constraints.Size;

public record LeaveDecisionRequest(
    boolean approve,
    @Size(max = 500) String note
) {}
