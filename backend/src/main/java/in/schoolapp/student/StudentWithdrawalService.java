package in.schoolapp.student;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.student.dto.WithdrawalResponse;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Graceful student exit with a fee-clearance gate (audit #13). A student can only be withdrawn —
 * closing their active enrollment — once outstanding fees are cleared, unless an authorised user
 * explicitly overrides (which is audited). Previously withdrawal didn't exist and TCs could be
 * issued to defaulters with a free-text "fees due" field nobody verified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentWithdrawalService {

    private final StudentEnrollmentRepository enrollmentRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditLogger audit;

    @Transactional
    public WithdrawalResponse withdraw(UUID tenantId, UUID studentId,
                                       String reason, boolean overrideDues, LocalDate leavingDate) {
        studentAccessGuard.assertInTenant(tenantId, studentId);

        long outstanding = feeInvoiceRepository.sumOutstandingByStudent(studentId);
        if (outstanding > 0 && !overrideDues) {
            throw new AppException(ErrorCode.FEE_CLEARANCE_REQUIRED,
                "Student has outstanding dues of ₹" + (outstanding / 100)
                    + ". Clear the dues or withdraw with an override.");
        }

        StudentEnrollment active = enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .filter(e -> e.getSchoolId().equals(tenantId) && e.getStatus() == EnrollmentStatus.ACTIVE)
            .findFirst()
            .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                "Student has no active enrollment to withdraw."));

        LocalDate effectiveDate = leavingDate != null ? leavingDate : LocalDate.now();
        active.setStatus(EnrollmentStatus.LEFT);
        active.setLeavingDate(effectiveDate);
        active.setLeavingReason(reason);
        enrollmentRepository.save(active);

        audit.logAction(tenantId, "Student", studentId, "STUDENT_WITHDRAWN",
            Map.of("reason", String.valueOf(reason),
                "outstandingPaise", outstanding,
                "duesOverridden", overrideDues));
        log.info("Student withdrawn tenant={} student={} outstanding={} override={}",
            tenantId, studentId, outstanding, overrideDues);

        return new WithdrawalResponse(studentId, EnrollmentStatus.LEFT, outstanding, overrideDues, effectiveDate);
    }
}
