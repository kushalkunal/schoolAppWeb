package in.schoolapp.analytics.detector;

import in.schoolapp.ai.llm.LlmClient;
import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.analytics.entity.MarksTrend;
import in.schoolapp.analytics.entity.RiskFactor;
import in.schoolapp.analytics.entity.StudentRiskScore;
import in.schoolapp.analytics.repository.StudentRiskScoreRepository;
import in.schoolapp.academics.entity.ReportCard;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.fee.FeeInvoiceService;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import in.schoolapp.tenantconfig.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly composite-risk detector — Sunday 03:00 IST. Blends three signals into a 0-100 score:
 * <ul>
 *   <li><b>Attendance</b> — {@code 100 - attendancePct} over the last 30 days</li>
 *   <li><b>Fee</b> — {@code min(100, outstanding / benchmark * 100)} where benchmark is a
 *       configured rupee amount that represents "one term overdue"</li>
 *   <li><b>Marks</b> — 0 if trending UP or FLAT, 40 if trending DOWN, 30 if UNKNOWN (new
 *       student: we err a little toward flagging so the teacher notices them)</li>
 * </ul>
 * The final score is the max of the three sub-scores (not a sum — a student flagged only for
 * marks should still score ~40, not overwhelm the top-of-list view). The contributor with the
 * highest sub-score is recorded as {@code topFactor} so the UI can explain the flag.
 * <p>
 * Rows are upserted per (school, student) — the unique key in V4 ensures idempotency. An alert
 * of type {@link AlertType#AT_RISK_STUDENT} is emitted for any student scoring ≥
 * {@code alert-threshold} (default 70) so the principal dashboard surfaces them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtRiskDetectionService {

    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ReportCardRepository reportCardRepository;
    private final FeeInvoiceService feeInvoiceService;
    private final StudentRiskScoreRepository riskScoreRepository;
    private final AlertService alertService;
    /**
     * Injected by Spring — exactly one impl is active (LOGGING by default, OLLAMA/GROQ/
     * GEMINI/OPENROUTER when configured). The detector swallows LLM failures so a flaky
     * provider never aborts the weekly run.
     */
    private final LlmClient llmClient;

    @Value("${app.analytics.at-risk.window-days:30}")
    private int windowDays;

    /**
     * When true, every flagged student gets an LLM narrative call. Disable in resource-
     * constrained deployments where the templated string is enough. Defaults to true.
     */
    @Value("${app.analytics.at-risk.use-llm-summary:true}")
    private boolean useLlmSummary;

    @Value("${app.analytics.at-risk.fee-benchmark-paise:5000000}")
    private long feeBenchmarkPaise;

    @Value("${app.analytics.at-risk.alert-threshold:70}")
    private int alertThreshold;

    /** Sundays at 03:00 IST — the week's quietest moment. */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Asia/Kolkata")
    public void run() {
        log.info("AtRiskDetectionService starting windowDays={}", windowDays);
        int totalScored = 0;
        int totalAlerts = 0;
        for (School school : schoolRepository.findAll()) {
            if (!school.isActive()) continue;
            var result = scanTenant(school.getId());
            totalScored += result.scored();
            totalAlerts += result.alerts();
        }
        log.info("AtRiskDetectionService finished scored={} alerts={}", totalScored, totalAlerts);
    }

    /** Public so tests and a future manual-trigger endpoint can drive it. */
    @Transactional
    public Result scanTenant(UUID tenantId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(windowDays);

        Map<UUID, AttendanceRepository.StudentAttendanceCountRow> attendanceById = new HashMap<>();
        attendanceRepository.countPerStudentInWindow(tenantId, from, to)
            .forEach(row -> attendanceById.put(row.getStudentId(), row));

        int scored = 0, alerts = 0;
        // Paginate through active students — for Phase 1 schools that's tens to a few hundred.
        int page = 0, size = 200;
        while (true) {
            var pageResult = studentRepository.findBySchoolIdAndActiveTrue(
                tenantId, org.springframework.data.domain.PageRequest.of(page, size));
            if (pageResult.isEmpty()) break;
            for (Student student : pageResult.getContent()) {
                Score s = computeScore(student, attendanceById.get(student.getId()));
                upsert(student, s);
                scored++;
                if (s.total >= alertThreshold) {
                    if (alertService.recordStudentAlert(tenantId, AlertType.AT_RISK_STUDENT,
                            AlertSeverity.HIGH, student.getId(),
                            "At-risk: " + student.displayName(),
                            describe(s),
                            "/students/" + student.getId()) != null) {
                        alerts++;
                    }
                }
            }
            if (pageResult.isLast()) break;
            page++;
        }
        return new Result(scored, alerts);
    }

    Score computeScore(Student student, AttendanceRepository.StudentAttendanceCountRow attendance) {
        BigDecimal attendancePct = null;
        int attendanceSub = 0;
        if (attendance != null && attendance.getTotalCount() > 0) {
            long total = attendance.getTotalCount();
            long absent = attendance.getAbsentCount();
            attendancePct = BigDecimal.valueOf(100L * (total - absent) / (double) total)
                .setScale(2, RoundingMode.HALF_UP);
            // Linear: 100% → 0 risk, 60% → 40 risk, 0% → 100 risk
            attendanceSub = clamp0to100(100 - attendancePct.intValue());
        }

        long outstanding = feeInvoiceService.getOutstanding(student.getId());
        int feeSub = feeBenchmarkPaise <= 0 ? 0
            : clamp0to100((int) Math.round(100.0 * outstanding / feeBenchmarkPaise));

        MarksTrend trend = marksTrendFor(student.getId());
        int marksSub = switch (trend) {
            case DOWN -> 40;
            case UNKNOWN -> 30;
            case UP, FLAT -> 0;
        };

        int total = Math.max(Math.max(attendanceSub, feeSub), marksSub);
        RiskFactor top;
        if (attendanceSub >= feeSub && attendanceSub >= marksSub) top = RiskFactor.ATTENDANCE;
        else if (feeSub >= marksSub) top = RiskFactor.FEE;
        else top = RiskFactor.MARKS;

        return new Score(total, attendancePct, outstanding, trend, top);
    }

    private MarksTrend marksTrendFor(UUID studentId) {
        List<ReportCard> recent = reportCardRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (recent.size() < 2) return MarksTrend.UNKNOWN;
        BigDecimal latest = recent.get(0).getPercentage();
        BigDecimal prior = recent.get(1).getPercentage();
        if (latest == null || prior == null) return MarksTrend.UNKNOWN;
        BigDecimal delta = latest.subtract(prior);
        // ±5% threshold to avoid noise flipping students between UP and DOWN
        if (delta.compareTo(BigDecimal.valueOf(5)) > 0) return MarksTrend.UP;
        if (delta.compareTo(BigDecimal.valueOf(-5)) < 0) return MarksTrend.DOWN;
        return MarksTrend.FLAT;
    }

    private void upsert(Student student, Score s) {
        StudentRiskScore row = riskScoreRepository
            .findBySchoolIdAndStudentId(student.getSchoolId(), student.getId())
            .orElseGet(() -> {
                StudentRiskScore x = new StudentRiskScore();
                x.setSchoolId(student.getSchoolId());
                x.setStudentId(student.getId());
                return x;
            });
        row.setScore(s.total);
        row.setAttendancePct(s.attendancePct);
        row.setFeeOutstandingPaise(s.feeOutstandingPaise);
        row.setMarksTrend(s.marksTrend);
        row.setTopFactor(s.topFactor);
        row.setCalculatedAt(OffsetDateTime.now());
        row.setSummary(buildSummary(student, s));
        riskScoreRepository.save(row);
    }

    /**
     * Returns a 2–3 sentence narrative. When an LLM provider is configured the call goes
     * out for a humane summary; otherwise the deterministic template kicks in. Any LLM
     * failure is logged at WARN and the template returned — at-risk scoring must NEVER
     * fail because the model is down.
     */
    private String buildSummary(Student student, Score s) {
        if (!useLlmSummary || llmClient == null
            || ProviderType.LOGGING.equals(llmClient.providerName())) {
            return describe(s);
        }
        try {
            String prompt = String.format("""
                Student %s has a composite at-risk score of %d/100.
                Sub-scores: attendance %s%%, fees outstanding ₹%,.2f, marks trend %s.
                Top contributing factor: %s.

                Write a brief (2-3 sentences, max 60 words) summary for the class teacher
                explaining the situation in plain English. Don't restate the numbers
                literally — interpret them. Suggest one practical next step.
                """,
                student.displayName(),
                s.total,
                s.attendancePct != null ? s.attendancePct.toPlainString() : "n/a",
                s.feeOutstandingPaise / 100.0,
                s.marksTrend,
                s.topFactor);
            String reply = llmClient.chat(student.getSchoolId(),
                "You write concise, empathetic notes for school class teachers.",
                prompt);
            // Defensive: if model returns empty, fall back so the column is never null.
            return reply != null && !reply.isBlank() ? reply : describe(s);
        } catch (Exception e) {
            log.warn("LLM summary failed for student={} reason={} — falling back to template",
                student.getId(), e.getMessage());
            return describe(s);
        }
    }

    private static String describe(Score s) {
        return String.format(
            "Composite risk %d/100. Top factor: %s. Attendance: %s, fee due: ₹%,.2f, marks trend: %s.",
            s.total, s.topFactor,
            s.attendancePct == null ? "n/a" : (s.attendancePct + "%"),
            s.feeOutstandingPaise / 100.0,
            s.marksTrend);
    }

    private static int clamp0to100(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    public record Score(int total, BigDecimal attendancePct, long feeOutstandingPaise,
                        MarksTrend marksTrend, RiskFactor topFactor) {}

    public record Result(int scored, int alerts) {}
}
