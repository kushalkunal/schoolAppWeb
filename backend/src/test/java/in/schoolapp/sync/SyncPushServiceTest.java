package in.schoolapp.sync;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.sync.dto.SyncPushRequest;
import in.schoolapp.sync.dto.SyncPushResponse;
import in.schoolapp.sync.dto.SyncPushResponse.EntryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class SyncPushServiceTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock ClassSectionService classSectionService;
    @Mock StudentEnrollmentRepository enrollmentRepository;

    @InjectMocks SyncPushService service;

    @Test
    void newEntry_createsRowAndReportsCreated() {
        UUID tenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Section section = section(sectionId);
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section);
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId))
            .thenReturn(List.of(enrollment(studentId, sectionId)));
        when(attendanceRepository.findByStudentIdAndDate(studentId, LocalDate.now()))
            .thenReturn(Optional.empty());
        UUID savedId = UUID.randomUUID();
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> {
            AttendanceRecord r = i.getArgument(0);
            r.setId(savedId);
            return r;
        });

        SyncPushRequest req = new SyncPushRequest(List.of(new SyncPushRequest.AttendanceEntry(
            UUID.randomUUID(), sectionId, studentId, LocalDate.now(),
            AttendanceStatus.ABSENT, null, null)));
        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.rejected()).isZero();
        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).status()).isEqualTo(EntryStatus.CREATED);
        assertThat(resp.results().get(0).serverId()).isEqualTo(savedId);
    }

    @Test
    void existingEntry_updatesAndReportsUpdated() {
        UUID tenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(sectionId));
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId))
            .thenReturn(List.of(enrollment(studentId, sectionId)));

        AttendanceRecord existing = new AttendanceRecord();
        existing.setId(UUID.randomUUID());
        existing.setSchoolId(tenant);
        existing.setStudentId(studentId);
        existing.setDate(today);
        existing.setStatus(AttendanceStatus.PRESENT);
        when(attendanceRepository.findByStudentIdAndDate(studentId, today)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        SyncPushRequest req = new SyncPushRequest(List.of(new SyncPushRequest.AttendanceEntry(
            UUID.randomUUID(), sectionId, studentId, today,
            AttendanceStatus.ABSENT, null, "updated by sync")));
        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.results().get(0).status()).isEqualTo(EntryStatus.UPDATED);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(existing.isSyncedFromMobile()).isTrue();
    }

    @Test
    void studentNotInSection_rejectedWithoutAffectingOthers() {
        UUID tenant = UUID.randomUUID();
        UUID section1 = UUID.randomUUID();
        UUID section2 = UUID.randomUUID();
        UUID student1 = UUID.randomUUID();  // enrolled in section1
        UUID student2 = UUID.randomUUID();  // not in section2 (wrong batch row)
        LocalDate today = LocalDate.now();

        when(classSectionService.getSectionOrThrow(tenant, section1)).thenReturn(section(section1));
        when(classSectionService.getSectionOrThrow(tenant, section2)).thenReturn(section(section2));
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(student1))
            .thenReturn(List.of(enrollment(student1, section1)));
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(student2))
            .thenReturn(List.of(enrollment(student2, section1)));  // enrolled in section1, not 2
        when(attendanceRepository.findByStudentIdAndDate(eq(student1), any())).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> {
            AttendanceRecord r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        SyncPushRequest req = new SyncPushRequest(List.of(
            new SyncPushRequest.AttendanceEntry(UUID.randomUUID(), section1, student1, today,
                AttendanceStatus.ABSENT, null, null),
            new SyncPushRequest.AttendanceEntry(UUID.randomUUID(), section2, student2, today,
                AttendanceStatus.ABSENT, null, null)
        ));

        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.results().get(0).status()).isEqualTo(EntryStatus.CREATED);
        assertThat(resp.results().get(1).status()).isEqualTo(EntryStatus.REJECTED);
        assertThat(resp.results().get(1).error()).contains("not enrolled");
    }

    @Test
    void futureDate_rejected() {
        UUID tenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SyncPushRequest req = new SyncPushRequest(List.of(new SyncPushRequest.AttendanceEntry(
            UUID.randomUUID(), sectionId, studentId, LocalDate.now().plusDays(3),
            AttendanceStatus.ABSENT, null, null)));

        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.results().get(0).status()).isEqualTo(EntryStatus.REJECTED);
        assertThat(resp.results().get(0).error()).contains("future");
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void duplicateLocalIdInBatch_secondOneRejected() {
        UUID tenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();  // same id used twice
        LocalDate today = LocalDate.now();

        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(sectionId));
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId))
            .thenReturn(List.of(enrollment(studentId, sectionId)));
        when(attendanceRepository.findByStudentIdAndDate(studentId, today)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> {
            AttendanceRecord r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        SyncPushRequest req = new SyncPushRequest(List.of(
            new SyncPushRequest.AttendanceEntry(localId, sectionId, studentId, today,
                AttendanceStatus.ABSENT, null, null),
            new SyncPushRequest.AttendanceEntry(localId, sectionId, studentId, today,
                AttendanceStatus.PRESENT, null, null)
        ));

        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.results().get(1).error()).contains("duplicate localId");
    }

    @Test
    void crossTenantExistingRow_rejected() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(sectionId));
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId))
            .thenReturn(List.of(enrollment(studentId, sectionId)));
        AttendanceRecord cross = new AttendanceRecord();
        cross.setId(UUID.randomUUID());
        cross.setSchoolId(otherTenant);  // rogue row from another tenant
        when(attendanceRepository.findByStudentIdAndDate(studentId, today)).thenReturn(Optional.of(cross));

        SyncPushRequest req = new SyncPushRequest(List.of(new SyncPushRequest.AttendanceEntry(
            UUID.randomUUID(), sectionId, studentId, today,
            AttendanceStatus.ABSENT, null, null)));

        SyncPushResponse resp = service.apply(tenant, req);

        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.results().get(0).error()).contains("different tenant");
        verify(attendanceRepository, never()).save(any(AttendanceRecord.class));
    }

    // helpers

    private static Section section(UUID id) {
        Section s = new Section();
        s.setId(id);
        return s;
    }

    private static StudentEnrollment enrollment(UUID studentId, UUID sectionId) {
        StudentEnrollment e = new StudentEnrollment();
        e.setStudentId(studentId);
        e.setSectionId(sectionId);
        e.setStatus(EnrollmentStatus.ACTIVE);
        return e;
    }
}
