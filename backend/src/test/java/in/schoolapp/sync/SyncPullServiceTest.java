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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncPullServiceTest {

    @Mock AcademicYearService academicYearService;
    @Mock SectionRepository sectionRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;

    @InjectMocks SyncPullService service;

    @Test
    void pullWithoutSince_returnsAllStudentsAndSections() {
        UUID tenant = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setName("2026-2027");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        when(academicYearService.findCurrent(tenant)).thenReturn(Optional.of(year));

        Section sec = new Section();
        sec.setId(sectionId);
        sec.setClassId(UUID.randomUUID());
        sec.setAcademicYearId(yearId);
        sec.setName("A");
        when(sectionRepository.findBySchoolIdAndAcademicYearId(tenant, yearId)).thenReturn(List.of(sec));

        Student stu = new Student();
        stu.setId(studentId);
        stu.setFirstName("Aarav");
        stu.setActive(true);
        when(studentRepository.findBySchoolIdOrderByUpdatedAtAsc(tenant)).thenReturn(List.of(stu));

        StudentEnrollment enr = new StudentEnrollment();
        enr.setStudentId(studentId);
        enr.setSectionId(sectionId);
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(List.of(enr));

        SyncPullResponse resp = service.pull(tenant, null);

        assertThat(resp.academicYear().id()).isEqualTo(yearId);
        assertThat(resp.sections()).hasSize(1);
        assertThat(resp.students()).hasSize(1);
        assertThat(resp.students().get(0).currentSectionId()).isEqualTo(sectionId);
        assertThat(resp.studentCount()).isEqualTo(1);
        verify(studentRepository, never())
            .findBySchoolIdAndUpdatedAtAfterOrderByUpdatedAtAsc(any(), any());
    }

    @Test
    void pullWithSince_usesDeltaQuery() {
        UUID tenant = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.now().minusDays(1);

        when(academicYearService.findCurrent(tenant)).thenReturn(Optional.empty());
        when(studentRepository.findBySchoolIdAndUpdatedAtAfterOrderByUpdatedAtAsc(eq(tenant), eq(since)))
            .thenReturn(List.of());

        SyncPullResponse resp = service.pull(tenant, since);

        assertThat(resp.academicYear()).isNull();
        assertThat(resp.sections()).isEmpty();
        assertThat(resp.students()).isEmpty();
        verify(studentRepository, never()).findBySchoolIdOrderByUpdatedAtAsc(any());
    }

    @Test
    void pullWhenNoCurrentYear_returnsEmptySectionsGracefully() {
        UUID tenant = UUID.randomUUID();
        when(academicYearService.findCurrent(tenant)).thenReturn(Optional.empty());
        when(studentRepository.findBySchoolIdOrderByUpdatedAtAsc(tenant)).thenReturn(List.of());

        SyncPullResponse resp = service.pull(tenant, null);

        assertThat(resp.academicYear()).isNull();
        assertThat(resp.sections()).isEmpty();
        assertThat(resp.students()).isEmpty();
        verify(sectionRepository, never()).findBySchoolIdAndAcademicYearId(any(), any());
    }
}
