package in.schoolapp.analytics;

import in.schoolapp.analytics.dto.DashboardResponse;
import in.schoolapp.analytics.entity.StudentRiskScore;
import in.schoolapp.analytics.repository.StudentRiskScoreRepository;
import in.schoolapp.attendance.AttendanceAnalyticsService;
import in.schoolapp.attendance.dto.AttendanceSummaryResponse;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composes the principal-dashboard payload from the analytics sub-services. Deliberately thin —
 * all the heavy lifting is in the individual services so the dashboard is just a
 * parallel-fetch aggregator. One HTTP call replaces what would otherwise be 4–5 round-trips.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int AT_RISK_TILE_SIZE = 5;
    private static final int AT_RISK_ALERT_THRESHOLD = 70;

    private final AttendanceAnalyticsService attendanceAnalyticsService;
    private final AlertService alertService;
    private final FeePaymentRepository feePaymentRepository;
    private final StudentRiskScoreRepository riskScoreRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse build(UUID tenantId) {
        LocalDate today = LocalDate.now();
        AttendanceSummaryResponse attendance = attendanceAnalyticsService.schoolSummary(tenantId, today);

        AlertService.AlertCounts alertCounts = alertService.countsForDigest(tenantId);
        DashboardResponse.AlertCounts alertDto = new DashboardResponse.AlertCounts(
            alertCounts.total(), alertCounts.high(), alertCounts.critical());

        YearMonth month = YearMonth.from(today);
        long mtd = feePaymentRepository.sumCollectedBetween(tenantId, month.atDay(1), today);
        long atRiskCount = riskScoreRepository
            .countBySchoolIdAndScoreGreaterThanEqual(tenantId, AT_RISK_ALERT_THRESHOLD);
        DashboardResponse.FeeSnapshot fees = new DashboardResponse.FeeSnapshot(mtd, atRiskCount);

        int unmarked = attendanceAnalyticsService.unmarkedSections(tenantId, today).size();

        List<StudentRiskScore> top = riskScoreRepository.findBySchoolIdOrderByScoreDesc(
            tenantId, PageRequest.of(0, AT_RISK_TILE_SIZE));
        Map<UUID, Student> studentsById = new HashMap<>();
        if (!top.isEmpty()) {
            studentRepository.findAllById(top.stream().map(StudentRiskScore::getStudentId).toList())
                .forEach(s -> studentsById.put(s.getId(), s));
        }
        List<DashboardResponse.AtRiskRow> atRiskRows = top.stream()
            .map(r -> {
                Student s = studentsById.get(r.getStudentId());
                return new DashboardResponse.AtRiskRow(
                    r.getStudentId(),
                    s == null ? null : s.displayName(),
                    r.getScore(),
                    r.getTopFactor() == null ? null : r.getTopFactor().name(),
                    r.getMarksTrend() == null ? null : r.getMarksTrend().name()
                );
            })
            .toList();

        return new DashboardResponse(today, attendance, alertDto, fees, unmarked, atRiskRows);
    }
}
