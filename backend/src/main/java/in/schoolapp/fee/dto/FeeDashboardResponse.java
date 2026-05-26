package in.schoolapp.fee.dto;

public record FeeDashboardResponse(
    long collectedTodayPaise,
    long collectedThisMonthPaise,
    long totalOutstandingPaise,
    long totalOverduePaise,
    long studentsWithDues,
    long paymentsCollectedToday
) {}
