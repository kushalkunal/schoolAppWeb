package in.schoolapp.hr.dto;

import java.math.BigDecimal;

public record UpdateLeaveBalanceRequest(
    BigDecimal entitledDays
) {}
