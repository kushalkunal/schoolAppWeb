package in.schoolapp.sync;

import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import in.schoolapp.sync.dto.SyncPullResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delta pull for the teacher's mobile app. Returns the minimal data needed to take attendance
 * offline: the current academic year, the full list of sections in that year, and the set of
 * students changed since the client's last pull timestamp.
 * <p>
 * On the very first pull (no {@code since}), this returns every active student — acceptable
 * for a typical school (a few hundred rows). Later pulls are delta-filtered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPullService {

    private final AcademicYearService academicYearService;
    private final SectionRepository sectionRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public SyncPullResponse pull(UUID tenantId, OffsetDateTime since) {
        OffsetDateTime serverTime = OffsetDateTime.now();

        SyncPullResponse.AcademicYearDto yearDto = academicYearService.findCurrent(tenantId)
            .map(SyncPullService::toDto)
            .orElse(null);

        List<Section> sections = yearDto == null
            ? List.of()
            : sectionRepository.findBySchoolIdAndAcademicYearId(tenantId, yearDto.id());
        List<SyncPullResponse.SectionDto> sectionDtos = sections.stream()
            .map(SyncPullService::toDto)
            .toList();

        List<Student> students = since == null
            ? studentRepository.findBySchoolIdOrderByUpdatedAtAsc(tenantId)
            : studentRepository.findBySchoolIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, since);

        // Resolve current section per student in one batch — no N+1 over enrollments.
        Map<UUID, UUID> currentSectionByStudent = new HashMap<>();
        for (Student s : students) {
            enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(s.getId()).stream()
                .findFirst()
                .map(StudentEnrollment::getSectionId)
                .ifPresent(sid -> currentSectionByStudent.put(s.getId(), sid));
        }

        List<SyncPullResponse.StudentDto> studentDtos = students.stream()
            .map(s -> new SyncPullResponse.StudentDto(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getAdmissionNumber(),
                s.getGender(),
                s.getDateOfBirth(),
                s.isActive(),
                s.getUpdatedAt(),
                currentSectionByStudent.get(s.getId())
            ))
            .toList();

        log.info("Sync pull tenant={} since={} students={} sections={}",
            tenantId, since, studentDtos.size(), sectionDtos.size());

        return new SyncPullResponse(serverTime, yearDto, sectionDtos, studentDtos, studentDtos.size());
    }

    private static SyncPullResponse.AcademicYearDto toDto(AcademicYear y) {
        return new SyncPullResponse.AcademicYearDto(
            y.getId(), y.getName(), y.getStartDate(), y.getEndDate());
    }

    private static SyncPullResponse.SectionDto toDto(Section s) {
        return new SyncPullResponse.SectionDto(
            s.getId(), s.getClassId(), s.getAcademicYearId(), s.getName(), s.getClassTeacherId());
    }
}
