package in.schoolapp.academics.dto;

public record AdmitCardDashboardResponse(
    long total,
    long generated,
    long downloaded,
    long blocked,
    long pending
) {}
