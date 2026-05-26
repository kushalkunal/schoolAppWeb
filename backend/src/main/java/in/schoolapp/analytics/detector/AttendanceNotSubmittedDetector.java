package in.schoolapp.analytics.detector;

import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.attendance.AttendanceAnalyticsService;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.school.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * At 10:00 IST, finds sections in the current academic year that haven't submitted today's
 * attendance yet and (a) WhatsApps the class teacher a gentle nudge and (b) writes an
 * {@link AlertType#ATTENDANCE_NOT_SUBMITTED} alert for the principal dashboard. The alert
 * {@code sectionId}-based idempotency guard keeps the run safe to retry within the same day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceNotSubmittedDetector {

    private final SchoolRepository schoolRepository;
    private final SectionRepository sectionRepository;
    private final AttendanceAnalyticsService analyticsService;
    private final WhatsAppNotifier whatsAppNotifier;
    private final AlertService alertService;

    @Scheduled(cron = "0 0 10 * * MON-SAT", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("AttendanceNotSubmittedDetector starting date={}", today);
        int totalAlerts = 0;
        int totalNudges = 0;
        for (School school : schoolRepository.findAll()) {
            if (!school.isActive()) continue;
            var result = scanTenant(school, today);
            totalAlerts += result.alerts();
            totalNudges += result.nudges();
        }
        log.info("AttendanceNotSubmittedDetector finished alerts={} nudges={}", totalAlerts, totalNudges);
    }

    /** Package-public for tests. */
    public Result scanTenant(School school, LocalDate date) {
        UUID tenantId = school.getId();
        var unmarkedSections = sectionRepository.findUnmarkedSectionsForDate(tenantId, date);
        if (unmarkedSections.isEmpty()) return new Result(0, 0);

        List<AttendanceAnalyticsService.TeacherSection> resolvable =
            analyticsService.resolveTeachersForSections(unmarkedSections);

        int alerts = 0, nudges = 0;
        for (var sec : unmarkedSections) {
            String title = "Attendance not submitted for section " + sec.getName();
            String desc = "Section " + sec.getName()
                + " has no attendance submitted for " + date + ". Class teacher nudged.";
            if (alertService.recordSectionAlert(tenantId, AlertType.ATTENDANCE_NOT_SUBMITTED,
                    AlertSeverity.MEDIUM, sec.getId(), title, desc,
                    "/sections/" + sec.getId() + "/attendance") != null) {
                alerts++;
            }
        }

        for (var ts : resolvable) {
            Staff teacher = ts.teacher();
            String body = String.format(
                "%s,\n\nReminder: today's (%s) attendance for section %s has not been submitted. "
                    + "Please submit at your earliest.\n\n— %s",
                teacher.displayName(), date, ts.section().getName(), school.getName());
            whatsAppNotifier.send(WhatsAppMessage.text(
                teacher.getPhone(), body, MessageType.EMERGENCY,
                WhatsAppMessage.Audit.forSchool(tenantId)));
            nudges++;
        }
        return new Result(alerts, nudges);
    }

    public record Result(int alerts, int nudges) {}
}
