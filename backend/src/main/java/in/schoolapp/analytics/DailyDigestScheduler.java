package in.schoolapp.analytics;

import in.schoolapp.attendance.AttendanceAnalyticsService;
import in.schoolapp.attendance.dto.AttendanceSummaryResponse;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 07:00 IST — sends each school's principal a one-line WhatsApp summary of:
 *   • yesterday's attendance roll-up,
 *   • open alerts (high + critical),
 *   • month-to-date fee collection.
 * <p>
 * Principals told us in gap analysis §5.4 they "don't want to log in to see how yesterday
 * went" — this digest is the one push they've asked for. Schools with {@code phone=null} are
 * skipped (email-only signup).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyDigestScheduler {

    private final SchoolRepository schoolRepository;
    private final AttendanceAnalyticsService attendanceAnalytics;
    private final AlertService alertService;
    private final FeePaymentRepository feePaymentRepository;
    private final WhatsAppNotifier whatsAppNotifier;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void run() {
        log.info("DailyDigestScheduler starting");
        int sent = 0;
        for (School school : schoolRepository.findAll()) {
            if (!school.isActive()) continue;
            if (school.getPhone() == null || school.getPhone().isBlank()) continue;
            try {
                sendDigest(school);
                sent++;
            } catch (Exception e) {
                log.error("Digest failed school={} — {}", school.getId(), e.getMessage());
            }
        }
        log.info("DailyDigestScheduler finished sent={}", sent);
    }

    public void sendDigest(School school) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceSummaryResponse att = attendanceAnalytics.schoolSummary(school.getId(), yesterday);
        AlertService.AlertCounts alerts = alertService.countsForDigest(school.getId());

        YearMonth thisMonth = YearMonth.now();
        long mtd = feePaymentRepository.sumCollectedBetween(
            school.getId(), thisMonth.atDay(1), LocalDate.now());

        String body = String.format(
            "🏫 %s — Daily Digest\n\n"
                + "📊 Yesterday (%s): %d present · %d absent · %d late\n"
                + "🚨 Open alerts: %d (high+critical: %d)\n"
                + "💰 Fee collection MTD: ₹%,.2f\n",
            school.getName(),
            yesterday,
            att.present(), att.absent(), att.late(),
            alerts.total(),
            alerts.high() + alerts.critical(),
            mtd / 100.0
        );

        whatsAppNotifier.send(WhatsAppMessage.text(
            school.getPhone(), body, MessageType.EMERGENCY,
            WhatsAppMessage.Audit.forSchool(school.getId())));
    }
}
