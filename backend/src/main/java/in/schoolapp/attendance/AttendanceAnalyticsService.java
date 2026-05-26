package in.schoolapp.attendance;

import in.schoolapp.attendance.dto.AttendanceSummaryResponse;
import in.schoolapp.attendance.dto.ChronicAbsenteeResponse;
import in.schoolapp.attendance.dto.UnmarkedSectionResponse;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only analytics over {@code attendance_records} — powers the principal dashboard tiles
 * and the scheduled detectors. Kept separate from {@link AttendanceService} because the write
 * path is transactional and tightly scoped; this service joins across sections, enrollments,
 * and staff to render dashboard-ready DTOs.
 */
@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private final AttendanceRepository attendanceRepository;
    private final SectionRepository sectionRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StaffRepository staffRepository;
    private final SchoolClassRepository schoolClassRepository;

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse schoolSummary(UUID tenantId, LocalDate date) {
        long present = attendanceRepository.countBySchoolIdAndDateAndStatus(tenantId, date, AttendanceStatus.PRESENT);
        long absent = attendanceRepository.countBySchoolIdAndDateAndStatus(tenantId, date, AttendanceStatus.ABSENT);
        long late = attendanceRepository.countBySchoolIdAndDateAndStatus(tenantId, date, AttendanceStatus.LATE);
        long halfDay = attendanceRepository.countBySchoolIdAndDateAndStatus(tenantId, date, AttendanceStatus.HALF_DAY);
        long leave = attendanceRepository.countBySchoolIdAndDateAndStatus(tenantId, date, AttendanceStatus.LEAVE);
        long total = present + absent + late + halfDay + leave;
        return new AttendanceSummaryResponse(date, total, present, absent, late, halfDay, leave);
    }

    @Transactional(readOnly = true)
    public List<UnmarkedSectionResponse> unmarkedSections(UUID tenantId, LocalDate date) {
        List<Section> sections = sectionRepository.findUnmarkedSectionsForDate(tenantId, date);
        if (sections.isEmpty()) return List.of();

        // Batch-fetch class + teacher names to avoid N+1
        Map<UUID, String> classNames = new HashMap<>();
        Map<UUID, String> teacherNames = new HashMap<>();
        schoolClassRepository.findAllById(sections.stream().map(Section::getClassId).toList())
            .forEach(c -> classNames.put(c.getId(), c.getName()));
        staffRepository.findAllById(sections.stream()
                .map(Section::getClassTeacherId)
                .filter(id -> id != null)
                .toList())
            .forEach(s -> teacherNames.put(s.getId(), s.displayName()));

        return sections.stream()
            .map(s -> new UnmarkedSectionResponse(
                s.getId(), s.getName(),
                classNames.get(s.getClassId()),
                s.getClassTeacherId(),
                s.getClassTeacherId() == null ? null : teacherNames.get(s.getClassTeacherId()),
                date))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChronicAbsenteeResponse> chronicAbsentees(UUID tenantId, int windowDays, int minAbsences) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(windowDays);
        List<AttendanceRepository.ChronicAbsenteeRow> rows = attendanceRepository
            .findChronicAbsentees(tenantId, from, to, minAbsences);
        if (rows.isEmpty()) return List.of();

        List<UUID> ids = rows.stream().map(AttendanceRepository.ChronicAbsenteeRow::getStudentId).toList();
        Map<UUID, Student> students = new HashMap<>();
        studentRepository.findAllById(ids).forEach(s -> {
            if (s.getSchoolId().equals(tenantId)) students.put(s.getId(), s);
        });

        // Most-recent enrollment per student for className/section
        Map<UUID, StudentEnrollment> enrollmentsByStudent = new HashMap<>();
        for (UUID sid : students.keySet()) {
            enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream().findFirst()
                .ifPresent(e -> enrollmentsByStudent.put(sid, e));
        }

        Map<UUID, Section> sectionsById = new HashMap<>();
        sectionRepository.findAllById(enrollmentsByStudent.values().stream()
                .map(StudentEnrollment::getSectionId).toList())
            .forEach(s -> sectionsById.put(s.getId(), s));

        Map<UUID, String> classNames = new HashMap<>();
        schoolClassRepository.findAllById(sectionsById.values().stream()
                .map(Section::getClassId).distinct().toList())
            .forEach(c -> classNames.put(c.getId(), c.getName()));

        return rows.stream()
            .filter(r -> students.containsKey(r.getStudentId()))
            .map(r -> {
                Student st = students.get(r.getStudentId());
                StudentEnrollment enr = enrollmentsByStudent.get(r.getStudentId());
                Section sec = enr == null ? null : sectionsById.get(enr.getSectionId());
                return new ChronicAbsenteeResponse(
                    st.getId(),
                    st.displayName(),
                    sec == null ? null : sec.getId(),
                    sec == null ? null : sec.getName(),
                    sec == null ? null : classNames.get(sec.getClassId()),
                    r.getAbsentCount(),
                    windowDays
                );
            })
            .sorted(Comparator.comparingLong(ChronicAbsenteeResponse::absentDays).reversed())
            .toList();
    }

    /**
     * Lookup helper used by {@link in.schoolapp.analytics.detector.AttendanceNotSubmittedDetector}
     * — returns the (sectionId → classTeacherId + teacherPhone) mapping needed to WhatsApp each
     * teacher directly. Only sections with a configured class teacher phone are returned.
     */
    @Transactional(readOnly = true)
    public List<TeacherSection> resolveTeachersForSections(List<Section> sections) {
        List<UUID> teacherIds = sections.stream()
            .map(Section::getClassTeacherId)
            .filter(id -> id != null)
            .toList();
        if (teacherIds.isEmpty()) return List.of();
        Map<UUID, Staff> staffById = new HashMap<>();
        staffRepository.findAllById(teacherIds)
            .forEach(s -> staffById.put(s.getId(), s));

        return sections.stream()
            .map(s -> {
                Staff teacher = s.getClassTeacherId() == null ? null : staffById.get(s.getClassTeacherId());
                return new TeacherSection(s, teacher);
            })
            .filter(ts -> ts.teacher() != null && ts.teacher().getPhone() != null)
            .toList();
    }

    public record TeacherSection(Section section, Staff teacher) {}
}
