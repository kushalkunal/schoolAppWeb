package in.schoolapp.academics;

import in.schoolapp.academics.dto.ExamEligibilityResponse;
import in.schoolapp.academics.dto.ExamEligibilityResponse.StudentEligibility;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-section exam eligibility — LLD §5.2 + gap §5.2. Flags whether each enrolled student
 * meets the school's {@code minAttendancePct} over the configured window (default last 90
 * days). Threshold is read from {@code school.settings.minAttendancePct} (set at signup).
 */
@Service
@RequiredArgsConstructor
public class ExamEligibilityService {

    private static final int DEFAULT_WINDOW_DAYS = 90;
    private static final int DEFAULT_MIN_ATTENDANCE_PCT = 75;

    private final ClassSectionService classSectionService;
    private final SchoolService schoolService;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public ExamEligibilityResponse forSection(UUID tenantId, UUID sectionId, Integer windowDays) {
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        School school = schoolService.getSchoolEntity(tenantId);
        int minPct = resolveMinPct(school);
        int days = windowDays == null || windowDays <= 0 ? DEFAULT_WINDOW_DAYS : windowDays;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<StudentEnrollment> roster = enrollmentRepository
            .findBySectionIdAndStatus(section.getId(), EnrollmentStatus.ACTIVE);
        if (roster.isEmpty()) {
            return new ExamEligibilityResponse(section.getId(), from, to, minPct, List.of());
        }

        Map<UUID, Integer> rollByStudent = new HashMap<>();
        Map<UUID, Student> studentById = new HashMap<>();
        List<UUID> studentIds = roster.stream().map(StudentEnrollment::getStudentId).toList();
        for (StudentEnrollment e : roster) {
            rollByStudent.put(e.getStudentId(), e.getRollNumber());
        }
        studentRepository.findAllById(studentIds).forEach(s -> studentById.put(s.getId(), s));

        Map<UUID, long[]> countsByStudent = new HashMap<>();
        attendanceRepository.countPerStudentInWindow(tenantId, from, to).forEach(row ->
            countsByStudent.put(row.getStudentId(),
                new long[]{row.getTotalCount(), row.getAbsentCount()}));

        List<StudentEligibility> out = studentIds.stream()
            .map(sid -> buildEligibility(sid, studentById.get(sid),
                rollByStudent.get(sid), countsByStudent.get(sid), minPct))
            .sorted((a, b) -> {
                Integer ra = a.rollNumber(), rb = b.rollNumber();
                if (ra == null && rb == null) return a.studentName().compareToIgnoreCase(b.studentName());
                if (ra == null) return 1;
                if (rb == null) return -1;
                return Integer.compare(ra, rb);
            })
            .toList();

        return new ExamEligibilityResponse(section.getId(), from, to, minPct, out);
    }

    private static StudentEligibility buildEligibility(UUID studentId, Student student,
                                                       Integer rollNumber, long[] counts, int minPct) {
        String name = student == null ? "(deleted)" : student.displayName();
        if (counts == null || counts[0] == 0) {
            return new StudentEligibility(studentId, name, rollNumber, 0, 0, null, false);
        }
        long total = counts[0];
        long absent = counts[1];
        long present = total - absent;
        BigDecimal pct = BigDecimal.valueOf(100.0 * present / total)
            .setScale(2, RoundingMode.HALF_UP);
        boolean eligible = pct.compareTo(BigDecimal.valueOf(minPct)) >= 0;
        return new StudentEligibility(studentId, name, rollNumber, total, absent, pct, eligible);
    }

    private static int resolveMinPct(School school) {
        Object v = school.getSettings() == null ? null : school.getSettings().get("minAttendancePct");
        if (v instanceof Number n) return clampPct(n.intValue());
        if (v instanceof String s) {
            try { return clampPct(Integer.parseInt(s.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return DEFAULT_MIN_ATTENDANCE_PCT;
    }

    private static int clampPct(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
