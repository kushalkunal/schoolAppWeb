package in.schoolapp.academics;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.repository.ExamRepository;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Runs every day at 07:00 server time.
 * For each exam whose start_date is exactly 4 or 5 days away, triggers bulk admit-card
 * generation so cards are ready before the exam day.
 *
 * <p>The job is idempotent: {@link AdmitCardService#generateForExam} skips students who
 * already have a GENERATED / DOWNLOADED card.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmitCardScheduler {

    private final ExamRepository examRepository;
    private final SchoolRepository schoolRepository;
    private final AdmitCardService admitCardService;

    /** Trigger window: generate when start date is this many days away. */
    private static final int[] DAYS_BEFORE = {5, 4};

    @Scheduled(cron = "0 0 7 * * *") // every day at 07:00
    public void autoGenerate() {
        LocalDate today = LocalDate.now();
        int total = 0;

        for (int daysBefore : DAYS_BEFORE) {
            LocalDate targetDate = today.plusDays(daysBefore);
            List<Exam> exams = examRepository.findAll().stream()
                .filter(e -> targetDate.equals(e.getStartDate()))
                .toList();

            for (Exam exam : exams) {
                UUID tenantId = exam.getSchoolId();
                try {
                    int generated = admitCardService.generateForExam(tenantId, exam.getId());
                    total += generated;
                    if (generated > 0) {
                        log.info("[AdmitCardScheduler] Generated {} cards for exam={} school={} ({}d before start)",
                            generated, exam.getId(), tenantId, daysBefore);
                    }
                } catch (Exception ex) {
                    log.warn("[AdmitCardScheduler] Failed for exam={} school={}: {}", exam.getId(), tenantId, ex.getMessage());
                }
            }
        }

        if (total > 0) {
            log.info("[AdmitCardScheduler] Finished — {} new admit cards generated", total);
        }
    }
}
