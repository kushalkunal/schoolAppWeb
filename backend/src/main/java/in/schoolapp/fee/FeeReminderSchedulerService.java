package in.schoolapp.fee;

import in.schoolapp.fee.dto.BulkReminderRequest;
import in.schoolapp.fee.entity.FeeReminderSchedule;
import in.schoolapp.fee.entity.ReminderTriggerType;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.repository.FeeReminderScheduleRepository;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Daily cron at 09:30 IST that fires configured reminder schedules. For each active schedule
 * per tenant, the "window" of due dates is computed from the schedule's trigger type +
 * daysOffset; any invoices with {@code due_date} matching get their student queued into
 * {@link FeeReminderService#dispatchReminders}.
 * <p>
 * {@link FeeReminderService} already builds the body + payment link; this service is just a
 * driver that decides which students to trigger and on which days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeReminderSchedulerService {

    private final SchoolRepository schoolRepository;
    private final FeeReminderScheduleRepository scheduleRepository;
    private final FeeInvoiceRepository invoiceRepository;
    private final FeeReminderService feeReminderService;

    /** 09:30 IST every day. Keeps reminders before morning break so parents see them early. */
    @Scheduled(cron = "0 30 9 * * *", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("FeeReminderScheduler starting date={}", today);
        int tenants = 0, reminders = 0;
        for (School s : schoolRepository.findAll()) {
            if (!s.isActive()) continue;
            reminders += scanTenant(s.getId(), today);
            tenants++;
        }
        log.info("FeeReminderScheduler finished tenants={} reminders={}", tenants, reminders);
    }

    /** Public entrypoint — lets an admin manually trigger the scan without waiting for cron. */
    public int scanTenant(UUID tenantId, LocalDate today) {
        List<FeeReminderSchedule> schedules = scheduleRepository.findBySchoolIdAndActiveTrue(tenantId);
        if (schedules.isEmpty()) return 0;

        // Dedupe across overlapping schedules — e.g. "3 after due" and "5 after due" for the
        // same invoice shouldn't double-message.
        java.util.Set<UUID> alreadyQueued = new java.util.HashSet<>();
        int total = 0;
        for (FeeReminderSchedule sched : schedules) {
            LocalDate dueDateToMatch = computeDueDate(today, sched);
            if (dueDateToMatch == null) continue;
            List<UUID> students = invoiceRepository.findStudentsWithInvoicesDueOn(tenantId, dueDateToMatch);
            List<UUID> fresh = newStudents(students, alreadyQueued);
            if (fresh.isEmpty()) continue;
            feeReminderService.dispatchReminders(tenantId,
                new BulkReminderRequest(fresh, null));
            total += fresh.size();
            log.info("Scheduler fired tenant={} schedule={} dueDate={} students={}",
                tenantId, sched.getName(), dueDateToMatch, fresh.size());
        }
        return total;
    }

    private static LocalDate computeDueDate(LocalDate today, FeeReminderSchedule s) {
        int offset = Math.max(0, s.getDaysOffset());
        return switch (s.getTriggerType()) {
            case BEFORE_DUE -> today.plusDays(offset);
            case ON_DUE -> today;
            case AFTER_DUE -> today.minusDays(offset);
            case null -> null;
        };
    }

    private static List<UUID> newStudents(List<UUID> candidates, Set<UUID> alreadyQueued) {
        List<UUID> out = new ArrayList<>();
        for (UUID id : candidates) {
            if (alreadyQueued.add(id)) out.add(id);
        }
        return out;
    }
}
