package in.schoolapp.analytics.detector;

import in.schoolapp.analytics.AlertService;
import in.schoolapp.analytics.entity.Alert;
import in.schoolapp.analytics.entity.AlertSeverity;
import in.schoolapp.analytics.entity.AlertType;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsecutiveAbsenceDetectorTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock StudentRepository studentRepository;
    @Mock SchoolRepository schoolRepository;
    @Mock AlertService alertService;

    @InjectMocks ConsecutiveAbsenceDetector detector;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(detector, "windowDays", 5);
        ReflectionTestUtils.setField(detector, "threshold", 3);
    }

    @Test
    void scanTenant_createsOneAlertPerAbsentStudent() {
        UUID tenant = UUID.randomUUID();
        UUID student1 = UUID.randomUUID();
        UUID student2 = UUID.randomUUID();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(5);

        when(attendanceRepository.findStudentsWithAbsencesInWindow(
            eq(tenant), eq(from), eq(to), eq(3)))
            .thenReturn(List.of(student1, student2));

        Student s1 = new Student();
        s1.setId(student1);
        s1.setSchoolId(tenant);
        s1.setActive(true);
        s1.setFirstName("Aarav");
        Student s2 = new Student();
        s2.setId(student2);
        s2.setSchoolId(tenant);
        s2.setActive(true);
        s2.setFirstName("Meera");

        when(studentRepository.findAllById(List.of(student1, student2)))
            .thenReturn(List.of(s1, s2));
        when(alertService.recordStudentAlert(eq(tenant), eq(AlertType.CONSECUTIVE_ABSENCE),
            eq(AlertSeverity.HIGH), any(UUID.class), any(), any(), any()))
            .thenReturn(new Alert());

        int created = detector.scanTenant(tenant, from, to);

        assertThat(created).isEqualTo(2);
        verify(alertService).recordStudentAlert(eq(tenant), eq(AlertType.CONSECUTIVE_ABSENCE),
            eq(AlertSeverity.HIGH), eq(student1), any(), any(), any());
        verify(alertService).recordStudentAlert(eq(tenant), eq(AlertType.CONSECUTIVE_ABSENCE),
            eq(AlertSeverity.HIGH), eq(student2), any(), any(), any());
    }

    @Test
    void scanTenant_skipsStudentsFromOtherTenants() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID student1 = UUID.randomUUID();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(5);

        when(attendanceRepository.findStudentsWithAbsencesInWindow(
            eq(tenant), eq(from), eq(to), anyInt()))
            .thenReturn(List.of(student1));

        // Student matches id but belongs to a different tenant — should be dropped defensively.
        Student crossTenant = new Student();
        crossTenant.setId(student1);
        crossTenant.setSchoolId(otherTenant);
        crossTenant.setActive(true);
        crossTenant.setFirstName("X");
        when(studentRepository.findAllById(List.of(student1))).thenReturn(List.of(crossTenant));

        int created = detector.scanTenant(tenant, from, to);

        assertThat(created).isZero();
        verify(alertService, never()).recordStudentAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanTenant_emptyCandidateList_doesNothing() {
        UUID tenant = UUID.randomUUID();
        when(attendanceRepository.findStudentsWithAbsencesInWindow(any(), any(), any(), anyInt()))
            .thenReturn(List.of());

        int created = detector.scanTenant(tenant, LocalDate.now().minusDays(5), LocalDate.now());

        assertThat(created).isZero();
        verify(studentRepository, never()).findAllById(any());
    }

    @Test
    void scanTenant_suppressedDuplicate_notCounted() {
        UUID tenant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(5);

        when(attendanceRepository.findStudentsWithAbsencesInWindow(any(), any(), any(), anyInt()))
            .thenReturn(List.of(student));
        Student s = new Student();
        s.setId(student);
        s.setSchoolId(tenant);
        s.setActive(true);
        s.setFirstName("Dup");
        when(studentRepository.findAllById(List.of(student))).thenReturn(List.of(s));
        // AlertService returns null when duplicate was suppressed
        when(alertService.recordStudentAlert(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(null);

        int created = detector.scanTenant(tenant, from, to);

        assertThat(created).isZero();
    }
}
