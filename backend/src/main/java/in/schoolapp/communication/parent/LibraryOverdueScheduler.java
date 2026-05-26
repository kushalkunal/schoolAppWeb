package in.schoolapp.communication.parent;

import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.library.entity.BookIssue;
import in.schoolapp.library.repository.BookIssueRepository;
import in.schoolapp.library.repository.BookRepository;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slice 33 — library overdue reminder cron. Sends one combined notification per student per
 * day for any unreturned issues whose due date has passed. Same gating model as
 * {@link FeeReminderScheduler}: master flag + {@link FeatureKey#PARENT_NOTIFY_LIBRARY_OVERDUE}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LibraryOverdueScheduler {

    private final BookIssueRepository issueRepo;
    private final BookRepository bookRepo;
    private final SchoolService schoolService;
    private final ParentNotificationService parentNotificationService;

    /** Daily at 07:15 IST. Override via {@code app.library.overdue.cron}. */
    @Scheduled(cron = "${app.library.overdue.cron:0 15 7 * * *}", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void tick() {
        long t0 = System.currentTimeMillis();
        List<School> schools = schoolService.findAllActiveSchools();
        int pings = 0;
        for (School s : schools) {
            pings += notifyOneSchool(s.getId());
        }
        log.info("[LIBRARY-OVERDUE-CRON] schools={} pings={} in {}ms",
            schools.size(), pings, System.currentTimeMillis() - t0);
    }

    int notifyOneSchool(UUID tenantId) {
        LocalDate today = LocalDate.now();
        List<BookIssue> overdue =
            issueRepo.findBySchoolIdAndReturnedAtIsNullAndDueDateBefore(tenantId, today);
        if (overdue.isEmpty()) return 0;

        // One message per student covering all their overdue books.
        Map<UUID, List<BookIssue>> byStudent = new LinkedHashMap<>();
        for (BookIssue iss : overdue) {
            byStudent.computeIfAbsent(iss.getStudentId(), k -> new java.util.ArrayList<>()).add(iss);
        }

        int count = 0;
        for (Map.Entry<UUID, List<BookIssue>> e : byStudent.entrySet()) {
            List<BookIssue> list = e.getValue();
            StringBuilder lines = new StringBuilder();
            for (BookIssue iss : list) {
                String title = bookRepo.findById(iss.getBookId())
                    .map(b -> b.getTitle()).orElse("(book)");
                lines.append("• ").append(title)
                    .append(" — due ").append(iss.getDueDate()).append('\n');
            }
            String body = String.format(
                "📚 *Library books overdue (%d)*\n\n%s\nPlease return them at the earliest to avoid fines.",
                list.size(), lines.toString());
            parentNotificationService.notify(
                tenantId, e.getKey(),
                FeatureKey.PARENT_NOTIFY_LIBRARY_OVERDUE,
                "Library books overdue", body, MessageType.FEE_REMINDER);
            count++;
        }
        return count;
    }
}
