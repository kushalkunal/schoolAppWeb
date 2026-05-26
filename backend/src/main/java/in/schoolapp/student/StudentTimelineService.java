package in.schoolapp.student;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ReportCard;
import in.schoolapp.academics.repository.ExamRepository;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.AcademicYearRepository;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.dto.StudentTimelineResponse;
import in.schoolapp.student.dto.StudentTimelineResponse.AttendanceYearSummary;
import in.schoolapp.student.dto.StudentTimelineResponse.EnrollmentEntry;
import in.schoolapp.student.dto.StudentTimelineResponse.ReportCardEntry;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-year student profile — enrollments, published report cards, and per-year attendance
 * rollup. Read-only aggregate over existing tables (no new columns). LLD §5.1.
 */
@Service
@RequiredArgsConstructor
public class StudentTimelineService {

    private final StudentService studentService;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final ReportCardRepository reportCardRepository;
    private final ExamRepository examRepository;
    private final AttendanceRepository attendanceRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SectionRepository sectionRepository;
    private final SchoolClassRepository schoolClassRepository;

    @Transactional(readOnly = true)
    public StudentTimelineResponse get(UUID tenantId, UUID studentId) {
        Student student = studentService.getStudentEntity(tenantId, studentId);

        List<StudentEnrollment> enrollments = enrollmentRepository
            .findByStudentIdOrderByCreatedAtDesc(studentId);

        Map<UUID, AcademicYear> yearsById = new HashMap<>();
        academicYearRepository.findBySchoolIdOrderByStartDateDesc(tenantId)
            .forEach(y -> yearsById.put(y.getId(), y));

        Map<UUID, Section> sectionsById = new HashMap<>();
        sectionRepository.findAllById(enrollments.stream()
                .map(StudentEnrollment::getSectionId).toList())
            .forEach(s -> sectionsById.put(s.getId(), s));

        Map<UUID, String> classNames = new HashMap<>();
        schoolClassRepository.findAllById(sectionsById.values().stream()
                .map(Section::getClassId).distinct().toList())
            .forEach(c -> classNames.put(c.getId(), c.getName()));

        List<EnrollmentEntry> enrollmentDtos = enrollments.stream()
            .map(e -> {
                Section sec = sectionsById.get(e.getSectionId());
                AcademicYear y = yearsById.get(e.getAcademicYearId());
                return new EnrollmentEntry(
                    e.getId(),
                    e.getAcademicYearId(),
                    y == null ? null : y.getName(),
                    e.getSectionId(),
                    sec == null ? null : sec.getName(),
                    sec == null ? null : classNames.get(sec.getClassId()),
                    e.getRollNumber(),
                    e.getStatus() == null ? null : e.getStatus().name(),
                    e.getCreatedAt());
            })
            .toList();

        List<ReportCard> cards = reportCardRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        Map<UUID, Exam> examsById = new HashMap<>();
        examRepository.findAllById(cards.stream().map(ReportCard::getExamId).toList())
            .forEach(ex -> examsById.put(ex.getId(), ex));
        List<ReportCardEntry> cardDtos = cards.stream()
            .map(c -> {
                Exam ex = examsById.get(c.getExamId());
                return new ReportCardEntry(
                    c.getId(),
                    c.getExamId(),
                    ex == null ? null : ex.getName(),
                    c.getPercentage(),
                    c.getGrade(),
                    c.getRankInClass(),
                    c.getCreatedAt());
            })
            .toList();

        List<AttendanceYearSummary> attendanceDtos = enrollments.stream()
            .map(e -> {
                AcademicYear y = yearsById.get(e.getAcademicYearId());
                if (y == null) return null;
                long total = 0, absent = 0;
                for (var row : attendanceRepository.countPerStudentInWindow(
                        tenantId, y.getStartDate(), y.getEndDate())) {
                    if (row.getStudentId().equals(studentId)) {
                        total = row.getTotalCount();
                        absent = row.getAbsentCount();
                        break;
                    }
                }
                BigDecimal pct = total == 0 ? null
                    : BigDecimal.valueOf(100.0 * (total - absent) / total)
                        .setScale(2, RoundingMode.HALF_UP);
                return new AttendanceYearSummary(
                    e.getAcademicYearId(), y.getName(),
                    y.getStartDate(), y.getEndDate(),
                    total, absent, pct);
            })
            .filter(x -> x != null)
            .sorted(Comparator.comparing((AttendanceYearSummary a) -> a.windowFrom()).reversed())
            .toList();

        return new StudentTimelineResponse(
            student.getId(), student.displayName(),
            enrollmentDtos, cardDtos, attendanceDtos);
    }
}
