package in.schoolapp.analytics.detector;

import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * On the 5th of each month at 09:00 IST, compares the prior month's collection against the
 * 3-month trailing average. Drops beyond {@code threshold-pct} get a CRITICAL alert so the
 * principal sees the squeeze as early as possible — fee recovery is hardest after month 2.
 * <p>
 * Written as a tenant-scoped {@link #scanTenant} so the cron can iterate all active schools,
 * and tests can drive a single tenant without time-travel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeCollectionDropDetector {

    private final SchoolRepository schoolRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final AlertService alertService;

    @Value("${app.analytics.fee-drop.threshold-pct:20}")
    private int thresholdPct;

    @Value("${app.analytics.fee-drop.min-prior-collection-paise:100000}")
    private long minPriorCollectionPaise;

    /** 5th of every month at 09:00 IST. */
    @Scheduled(cron = "0 0 9 5 * *", zone = "Asia/Kolkata")
    public void run() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("FeeCollectionDropDetector starting forMonth={}", lastMonth);
        int totalAlerts = 0;
        for (School school : schoolRepository.findAll()) {
            if (!school.isActive()) continue;
            if (scanTenant(school.getId(), lastMonth)) totalAlerts++;
        }
        log.info("FeeCollectionDropDetector finished alerts={}", totalAlerts);
    }

    /**
     * Returns true if an alert was created.
     */
    public boolean scanTenant(UUID tenantId, YearMonth target) {
        long targetCollection = sumMonth(tenantId, target);
        long avg3mo = trailingAverage(tenantId, target, 3);

        if (avg3mo < minPriorCollectionPaise) {
            // Too little history to draw a conclusion — skip silently. A brand-new school
            // will hit this every month until 3 months of baseline exist.
            return false;
        }
        long dropPct = 100 - (targetCollection * 100 / avg3mo);
        if (dropPct < thresholdPct) return false;

        String title = "Fee collection dropped " + dropPct + "% in " + target;
        String desc = String.format(
            "Collection for %s was ₹%s vs a 3-month trailing average of ₹%s. Consider activating "
                + "reminder schedules and reviewing chronic defaulters.",
            target,
            paiseToRupees(targetCollection),
            paiseToRupees(avg3mo));
        // Use a synthetic "tenant-wide" key by creating a Student-less section-less alert —
        // recordSectionAlert expects a sectionId, so we instead call through a direct path.
        var alert = alertService.recordSectionAlert(
            tenantId, AlertType.FEE_COLLECTION_DROP, AlertSeverity.CRITICAL,
            syntheticDropKey(target), title, desc, "/fees/dashboard");
        return alert != null;
    }

    private long sumMonth(UUID tenantId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return feePaymentRepository.sumCollectedBetween(tenantId, from, to);
    }

    private long trailingAverage(UUID tenantId, YearMonth beforeExclusive, int months) {
        long sum = 0;
        for (int i = 1; i <= months; i++) {
            sum += sumMonth(tenantId, beforeExclusive.minusMonths(i));
        }
        return months == 0 ? 0 : sum / months;
    }

    /** Deterministic UUID from a month — re-running today yields the same key for idempotency. */
    private static UUID syntheticDropKey(YearMonth ym) {
        return UUID.nameUUIDFromBytes(("fee-drop-" + ym).getBytes());
    }

    private static String paiseToRupees(long paise) {
        return String.format("%,.2f", paise / 100.0);
    }
}
