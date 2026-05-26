package in.schoolapp.analytics.detector;

import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily detector (15:30 IST, after most schools have submitted) that finds students absent on
 * at least {@code threshold} of the last {@code window} school days and writes a
 * {@link AlertType#CONSECUTIVE_ABSENCE} alert. Per-student idempotency inside
 * {@link AlertService#recordStudentAlert} ensures a second run of the job on the same day is a
 * no-op.
 * <p>
 * The query is "absences in the last N days" rather than a true consecutive run because most
 * schools won't have attendance on weekends, and a Monday run after a 3-day absence
 * (Wed-Thu-Fri) is exactly what we want flagged. Gap analysis §8.1.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsecutiveAbsenceDetector {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final AlertService alertService;

    @Value("${app.analytics.consecutive-absence.window-days:5}")
    private int windowDays;

    @Value("${app.analytics.consecutive-absence.threshold:3}")
    private int threshold;

    /** Runs every day at 15:30 IST. Cron fields: sec min hour dom mon dow. */
    @Scheduled(cron = "0 30 15 * * *", zone = "Asia/Kolkata")
    public void run() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(windowDays);
        log.info("ConsecutiveAbsenceDetector starting window=[{},{}] threshold={}", from, to, threshold);

        int totalAlerts = 0;
        for (School school : schoolRepository.findAll()) {
            if (!school.isActive()) continue;
            totalAlerts += scanTenant(school.getId(), from, to);
        }
        log.info("ConsecutiveAbsenceDetector finished alertsCreated={}", totalAlerts);
    }

    /** Public entrypoint so tests can drive the detector without waiting for the cron. */
    public int scanTenant(UUID tenantId, LocalDate from, LocalDate to) {
        List<UUID> candidateIds = attendanceRepository
            .findStudentsWithAbsencesInWindow(tenantId, from, to, threshold);
        if (candidateIds.isEmpty()) return 0;

        Map<UUID, Student> loaded = new HashMap<>();
        studentRepository.findAllById(candidateIds).forEach(s -> {
            if (s.getSchoolId().equals(tenantId) && s.isActive()) loaded.put(s.getId(), s);
        });

        int created = 0;
        for (UUID studentId : candidateIds) {
            Student st = loaded.get(studentId);
            if (st == null) continue;
            String title = st.displayName() + " absent " + threshold + "+ days in last " + windowDays;
            String desc = "Student has been absent for at least " + threshold
                + " of the last " + windowDays + " school days. Please follow up with the family.";
            if (alertService.recordStudentAlert(tenantId, AlertType.CONSECUTIVE_ABSENCE,
                    AlertSeverity.HIGH, studentId, title, desc,
                    "/students/" + studentId + "/attendance") != null) {
                created++;
            }
        }
        return created;
    }
}
