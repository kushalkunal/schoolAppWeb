package in.schoolapp.communication.parent;

import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 33d — daily cron that nudges parents when fees fall due or go overdue.
 * <p>
 * Two notification windows, one cron tick at 07:00 IST:
 * <ul>
 *   <li><b>Due-soon</b> (PARENT_NOTIFY_FEE_DUE) — invoices due today, in 1 day, in 3 days.
 *       One message per (student × bucket) so the same invoice doesn't go three times.</li>
 *   <li><b>Overdue</b> (PARENT_NOTIFY_FEE_OVERDUE) — invoices past due. Grouped per student
 *       so a defaulter with five overdue invoices gets a single combined message, not five.</li>
 * </ul>
 * Per-tenant feature flags gate everything; an empty {@code findAll()} pass means zero sends.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeReminderScheduler {

    /** Days-from-today buckets we'll remind on. Tweak via {@code app.fee.reminder.due-in-days}. */
    private static final int[] DUE_BUCKETS = new int[] { 0, 1, 3 };

    private final FeeInvoiceRepository invoiceRepo;
    private final SchoolService schoolService;
    private final ParentNotificationService parentNotificationService;

    /** Daily at 07:00 IST. Override via {@code app.fee.reminder.cron}. */
    @Scheduled(cron = "${app.fee.reminder.cron:0 0 7 * * *}", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void tick() {
        long t0 = System.currentTimeMillis();
        List<School> schools = schoolService.findAllActiveSchools();
        int duePings = 0, overduePings = 0;
        for (School s : schools) {
            duePings += dueSoon(s.getId());
            overduePings += overdue(s.getId());
        }
        log.info("[FEE-REMINDER-CRON] schools={} duePings={} overduePings={} in {}ms",
            schools.size(), duePings, overduePings, System.currentTimeMillis() - t0);
    }

    /** Returns the count of notify() calls dispatched (regardless of channel outcome). */
    int dueSoon(UUID tenantId) {
        LocalDate today = LocalDate.now();
        int count = 0;
        // De-dup: the same invoice can match multiple buckets if (somehow) bucket dates overlap;
        // notification dispatcher is idempotent in practice but we guard here just to be tidy.
        Set<UUID> seenInvoices = new HashSet<>();
        for (int days : DUE_BUCKETS) {
            LocalDate target = today.plusDays(days);
            List<FeeInvoice> invs = invoiceRepo.findBySchoolIdAndStatusInAndDueDate(
                tenantId, List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIAL), target);
            for (FeeInvoice inv : invs) {
                if (!seenInvoices.add(inv.getId())) continue;
                String when = days == 0 ? "today" : days == 1 ? "tomorrow" : ("in " + days + " days");
                long balance = inv.getAmountDuePaise() - inv.getAmountPaidPaise();
                String body = String.format(
                    "💰 *Fee due %s*\n\nAmount: ₹%.2f\nDue date: %s\n\nKindly pay before the due date to avoid late fees.",
                    when, balance / 100.0, target);
                parentNotificationService.notify(
                    tenantId, inv.getStudentId(),
                    FeatureKey.PARENT_NOTIFY_FEE_DUE,
                    "Fee reminder — due " + when, body, MessageType.FEE_REMINDER);
                count++;
            }
        }
        return count;
    }

    int overdue(UUID tenantId) {
        LocalDate today = LocalDate.now();
        List<FeeInvoice> invs = invoiceRepo.findBySchoolIdAndStatusInAndDueDateBefore(
            tenantId, List.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIAL), today);

        // Group per student so one parent gets one combined message even if five invoices are overdue.
        Map<UUID, List<FeeInvoice>> byStudent = new LinkedHashMap<>();
        for (FeeInvoice inv : invs) {
            byStudent.computeIfAbsent(inv.getStudentId(), k -> new java.util.ArrayList<>()).add(inv);
        }
        int count = 0;
        for (Map.Entry<UUID, List<FeeInvoice>> e : byStudent.entrySet()) {
            UUID studentId = e.getKey();
            List<FeeInvoice> list = e.getValue();
            long total = list.stream()
                .mapToLong(i -> i.getAmountDuePaise() - i.getAmountPaidPaise())
                .sum();
            LocalDate oldest = list.stream()
                .map(FeeInvoice::getDueDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(today);
            String body = String.format(
                "⚠️ *Overdue fee reminder*\n\nOutstanding: ₹%.2f across %d invoice(s)\nOldest due: %s\n\nPlease clear the dues at your earliest convenience.",
                total / 100.0, list.size(), oldest);
            parentNotificationService.notify(
                tenantId, studentId,
                FeatureKey.PARENT_NOTIFY_FEE_OVERDUE,
                "Overdue fees — kindly clear", body, MessageType.FEE_REMINDER);
            count++;
        }
        return count;
    }
}
