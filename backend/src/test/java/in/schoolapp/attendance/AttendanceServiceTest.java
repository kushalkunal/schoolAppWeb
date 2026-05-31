package in.schoolapp.attendance;

import in.schoolapp.attendance.dto.AttendanceEntryDto;
import in.schoolapp.attendance.dto.AttendanceRecordResponse;
import in.schoolapp.attendance.dto.AttendanceSectionResponse;
import in.schoolapp.attendance.dto.AttendanceSubmitResponse;
import in.schoolapp.attendance.dto.SubmitAttendanceRequest;
import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.attendance.repository.AttendanceSectionLockRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock AttendanceSectionLockRepository lockRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock ClassSectionService classSectionService;
    @Mock StaffRepository staffRepository;
    @Mock ApplicationEventPublisher events;
    @Mock in.schoolapp.calendar.SchoolCalendarService calendarService;

    @InjectMocks
    AttendanceService service;

    // ── shared fixtures ──────────────────────────────────────────────────────
    final UUID tenantId    = UUID.randomUUID();
    final UUID sectionId   = UUID.randomUUID();
    final UUID teacherId   = UUID.randomUUID();
    final UUID otherTeacher = UUID.randomUUID();
    final UUID studentId   = UUID.randomUUID();
    final LocalDate today  = LocalDate.now();

    Section section;
    StudentEnrollment enrollment;
    AttendanceRecord savedRecord;

    @BeforeEach
    void setUpFixtures() {
        // Default: the school is open. Tests that throw before the calendar gate (RBAC) never
        // call this — hence lenient to avoid unnecessary-stubbing failures.
        org.mockito.Mockito.lenient()
            .when(calendarService.isWorkingDay(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);

        section = new Section();
        section.setId(sectionId);
        section.setSchoolId(tenantId);
        section.setClassTeacherId(teacherId);

        enrollment = new StudentEnrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setStudentId(studentId);
        enrollment.setSectionId(sectionId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        savedRecord = new AttendanceRecord();
        savedRecord.setId(UUID.randomUUID());
        savedRecord.setSchoolId(tenantId);
        savedRecord.setStudentId(studentId);
        savedRecord.setSectionId(sectionId);
        savedRecord.setDate(today);
        savedRecord.setStatus(AttendanceStatus.PRESENT);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // =========================================================================
    // submitAttendance — ownership guard
    // =========================================================================
    @Nested
    class SubmitAttendance_OwnershipGuard {

        @Test
        void classTeacher_assignedToSection_canSubmit() {
            TenantContext.set(tenantId, teacherId, "CLASS_TEACHER");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(lockRepository.existsBySectionIdAndDate(sectionId, today)).thenReturn(false);
            when(lockRepository.findBySectionIdAndDate(sectionId, today)).thenReturn(Optional.empty());
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenReturn(savedRecord);
            when(lockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());
            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.total()).isEqualTo(1);
            assertThat(resp.present()).isEqualTo(1);
            verify(events).publishEvent(any(Object.class));
        }

        @Test
        void classTeacher_notAssignedToSection_isForbidden() {
            TenantContext.set(tenantId, otherTeacher, "CLASS_TEACHER");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());

            assertThatThrownBy(() -> service.submitAttendance(tenantId, sectionId, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.SECTION_NOT_ASSIGNED);

            verify(enrollmentRepository, never()).findBySectionIdAndStatus(any(), any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        void principal_canMarkAnySection_withoutOwnershipCheck() {
            TenantContext.set(tenantId, UUID.randomUUID(), "PRINCIPAL");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenReturn(savedRecord);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());
            // Must not throw even though section.classTeacherId != staffId
            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.total()).isEqualTo(1);
        }

        @Test
        void admin_canMarkAnySection_withoutOwnershipCheck() {
            TenantContext.set(tenantId, UUID.randomUUID(), "ADMIN");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenReturn(savedRecord);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());
            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.total()).isEqualTo(1);
        }

        @Test
        void classTeacher_lockedSection_throwsAlreadySubmitted() {
            TenantContext.set(tenantId, teacherId, "CLASS_TEACHER");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(lockRepository.existsBySectionIdAndDate(sectionId, today)).thenReturn(true);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());

            assertThatThrownBy(() -> service.submitAttendance(tenantId, sectionId, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.ATTENDANCE_ALREADY_SUBMITTED);

            verify(enrollmentRepository, never()).findBySectionIdAndStatus(any(), any());
        }

        @Test
        void principal_canOverrideLock() {
            TenantContext.set(tenantId, UUID.randomUUID(), "PRINCIPAL");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            // Lock exists but PRINCIPAL bypasses it
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenReturn(savedRecord);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());
            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.total()).isEqualTo(1);
        }
    }

    // =========================================================================
    // submitAttendance — reverse-marking logic
    // =========================================================================
    @Nested
    class SubmitAttendance_ReverseMarking {

        @BeforeEach
        void setupPrincipal() {
            TenantContext.set(tenantId, UUID.randomUUID(), "PRINCIPAL");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
        }

        @Test
        void studentNotInEntries_isMarkedPresent() {
            UUID student2 = UUID.randomUUID();
            StudentEnrollment enr2 = new StudentEnrollment();
            enr2.setId(UUID.randomUUID());
            enr2.setStudentId(student2);
            enr2.setSectionId(sectionId);
            enr2.setStatus(EnrollmentStatus.ACTIVE);

            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment, enr2));
            when(attendanceRepository.findByStudentIdAndDate(any(), any()))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Only mark student1 as ABSENT; student2 is not in entries
            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of(
                new AttendanceEntryDto(studentId, AttendanceStatus.ABSENT, null, null)
            ));

            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.absent()).isEqualTo(1);
            assertThat(resp.present()).isEqualTo(1);
            assertThat(resp.total()).isEqualTo(2);
        }

        @Test
        void emptySection_throwsValidationError() {
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of());

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());

            assertThatThrownBy(() -> service.submitAttendance(tenantId, sectionId, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);

            verify(events, never()).publishEvent(any());
        }

        @Test
        void studentNotInRoster_throwsValidationError() {
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));

            UUID outsider = UUID.randomUUID();
            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of(
                new AttendanceEntryDto(outsider, AttendanceStatus.ABSENT, null, null)
            ));

            assertThatThrownBy(() -> service.submitAttendance(tenantId, sectionId, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);
        }

        @Test
        void submitting_publishesAttendanceSubmittedEvent() {
            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenReturn(savedRecord);

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of());
            service.submitAttendance(tenantId, sectionId, req);

            ArgumentCaptor<in.schoolapp.attendance.event.AttendanceSubmittedEvent> cap =
                ArgumentCaptor.forClass(in.schoolapp.attendance.event.AttendanceSubmittedEvent.class);
            verify(events).publishEvent(cap.capture());

            assertThat(cap.getValue().tenantId()).isEqualTo(tenantId);
            assertThat(cap.getValue().sectionId()).isEqualTo(sectionId);
            assertThat(cap.getValue().date()).isEqualTo(today);
        }

        @Test
        void resubmit_upserts_existingRecord() {
            AttendanceRecord existing = new AttendanceRecord();
            existing.setId(UUID.randomUUID());
            existing.setSchoolId(tenantId);
            existing.setStudentId(studentId);
            existing.setSectionId(sectionId);
            existing.setDate(today);
            existing.setStatus(AttendanceStatus.PRESENT);

            when(enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
            when(attendanceRepository.findByStudentIdAndDate(studentId, today))
                .thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SubmitAttendanceRequest req = new SubmitAttendanceRequest(today, List.of(
                new AttendanceEntryDto(studentId, AttendanceStatus.ABSENT, null, null)
            ));
            AttendanceSubmitResponse resp = service.submitAttendance(tenantId, sectionId, req);

            assertThat(resp.absent()).isEqualTo(1);
            // save() called exactly once per student, not twice
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(attendanceRepository).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        }
    }

    // =========================================================================
    // getSectionAttendance — ownership guard
    // =========================================================================
    @Nested
    class GetSectionAttendance_OwnershipGuard {

        @Test
        void classTeacher_assignedToSection_canRead() {
            TenantContext.set(tenantId, teacherId, "CLASS_TEACHER");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(attendanceRepository.findBySchoolIdAndSectionIdAndDate(tenantId, sectionId, today))
                .thenReturn(List.of(savedRecord));
            when(lockRepository.findBySectionIdAndDate(sectionId, today)).thenReturn(Optional.empty());

            AttendanceSectionResponse result =
                service.getSectionAttendance(tenantId, sectionId, today);

            assertThat(result.records()).hasSize(1);
            assertThat(result.locked()).isFalse();
        }

        @Test
        void classTeacher_notAssignedToSection_isForbidden() {
            TenantContext.set(tenantId, otherTeacher, "CLASS_TEACHER");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);

            assertThatThrownBy(() -> service.getSectionAttendance(tenantId, sectionId, today))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.SECTION_NOT_ASSIGNED);

            verify(attendanceRepository, never())
                .findBySchoolIdAndSectionIdAndDate(any(), any(), any());
        }

        @Test
        void principal_canReadAnySection() {
            TenantContext.set(tenantId, UUID.randomUUID(), "PRINCIPAL");
            when(classSectionService.getSectionOrThrow(tenantId, sectionId)).thenReturn(section);
            when(attendanceRepository.findBySchoolIdAndSectionIdAndDate(tenantId, sectionId, today))
                .thenReturn(List.of());
            when(lockRepository.findBySectionIdAndDate(sectionId, today)).thenReturn(Optional.empty());

            AttendanceSectionResponse result =
                service.getSectionAttendance(tenantId, sectionId, today);

            assertThat(result.records()).isEmpty();
            assertThat(result.locked()).isFalse();
        }
    }
}
