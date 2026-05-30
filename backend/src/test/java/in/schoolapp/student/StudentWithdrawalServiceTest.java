package in.schoolapp.student;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Student exit + fee-clearance gate (audit #13). */
@ExtendWith(MockitoExtension.class)
class StudentWithdrawalServiceTest {

    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock FeeInvoiceRepository feeInvoiceRepository;
    @Mock StudentAccessGuard studentAccessGuard;
    @Mock AuditLogger audit;
    @InjectMocks StudentWithdrawalService service;

    final UUID tenant = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();

    private StudentEnrollment activeEnrollment() {
        StudentEnrollment e = new StudentEnrollment();
        e.setSchoolId(tenant);
        e.setStudentId(studentId);
        e.setStatus(EnrollmentStatus.ACTIVE);
        return e;
    }

    @Test
    void withdrawsWhenNoDues() {
        when(feeInvoiceRepository.sumOutstandingByStudent(studentId)).thenReturn(0L);
        StudentEnrollment active = activeEnrollment();
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(List.of(active));
        lenient().when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resp = service.withdraw(tenant, studentId, "Relocating", false, null);

        assertThat(resp.status()).isEqualTo(EnrollmentStatus.LEFT);
        assertThat(active.getStatus()).isEqualTo(EnrollmentStatus.LEFT);
        verify(audit).logAction(eq(tenant), eq("Student"), eq(studentId), eq("STUDENT_WITHDRAWN"), any());
    }

    @Test
    void blocksWithdrawalWhenDuesOutstandingAndNoOverride() {
        when(feeInvoiceRepository.sumOutstandingByStudent(studentId)).thenReturn(50_000L);

        assertThatThrownBy(() -> service.withdraw(tenant, studentId, "Relocating", false, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FEE_CLEARANCE_REQUIRED);

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void overrideAllowsWithdrawalDespiteDues() {
        when(feeInvoiceRepository.sumOutstandingByStudent(studentId)).thenReturn(50_000L);
        StudentEnrollment active = activeEnrollment();
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(List.of(active));
        lenient().when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resp = service.withdraw(tenant, studentId, "Defaulter, escalated", true, null);

        assertThat(resp.status()).isEqualTo(EnrollmentStatus.LEFT);
        assertThat(resp.duesOverridden()).isTrue();
        assertThat(resp.outstandingPaise()).isEqualTo(50_000L);
    }

    @Test
    void failsWhenNoActiveEnrollment() {
        when(feeInvoiceRepository.sumOutstandingByStudent(studentId)).thenReturn(0L);
        when(enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.withdraw(tenant, studentId, "x", false, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
