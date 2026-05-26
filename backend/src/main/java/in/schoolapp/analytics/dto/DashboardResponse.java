package in.schoolapp.analytics.dto;

import in.schoolapp.attendance.dto.AttendanceSummaryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Single-call payload for the principal dashboard landing screen. Each field is an independent
 * tile — the frontend can hide one without losing the others. If a tile couldn't be computed
 * (e.g. a fresh school with no fee data) the value is {@code null} / zero rather than an error.
 */
public record DashboardResponse(
    LocalDate asOfDate,
    AttendanceSummaryResponse attendance,
    AlertCounts alerts,
    FeeSnapshot fees,
    int unmarkedSectionsCount,
    List<AtRiskRow> topAtRisk
) {
    public record AlertCounts(long total, long high, long critical) {}
    public record FeeSnapshot(long mtdCollectedPaise, long activeAtRiskCount) {}
    public record AtRiskRow(
        UUID studentId, String studentName, int score,
        String topFactor, String marksTrend
    ) {}
}
