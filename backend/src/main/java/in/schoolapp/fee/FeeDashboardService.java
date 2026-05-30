package in.schoolapp.fee;

import in.schoolapp.fee.dto.ClassCollectionRow;
import in.schoolapp.fee.dto.DefaulterResponse;
import in.schoolapp.fee.dto.FeeDashboardResponse;
import in.schoolapp.fee.dto.RecentPaymentRow;
import in.schoolapp.fee.entity.PaymentMode;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.repository.FeeInvoiceRepository.DefaulterRow;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.fee.repository.FeePaymentRepository.ClassCollectionProjection;
import in.schoolapp.fee.repository.FeePaymentRepository.RecentPaymentProjection;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregated read-side for the principal's fee dashboard + defaulters list. All queries are
 * tenant-scoped via the school_id column; no cross-tenant leakage possible even for native
 * SQL, since every query parameterises school_id.
 */
@Service
@RequiredArgsConstructor
public class FeeDashboardService {

    private final FeeInvoiceRepository invoiceRepository;
    private final FeePaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final SchoolClassRepository schoolClassRepository;

    @Transactional(readOnly = true)
    public FeeDashboardResponse getDashboard(UUID tenantId) {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        long collectedToday = paymentRepository.sumCollectedBetween(tenantId, today, today);
        long collectedMonth = paymentRepository.sumCollectedBetween(tenantId, firstOfMonth, lastOfMonth);
        long paymentsToday = paymentRepository.countCollectedBetween(tenantId, today, today);
        long outstanding = invoiceRepository.sumOutstandingBySchool(tenantId);
        long overdue = invoiceRepository.sumOverdueBySchool(tenantId, today);
        long defaultersCount = invoiceRepository.countStudentsWithDues(tenantId);

        return new FeeDashboardResponse(
            collectedToday, collectedMonth, outstanding, overdue, defaultersCount, paymentsToday
        );
    }

    /**
     * Defaulters list with enrichment (student name + class + section + days overdue). One
     * native query for the aggregate stats, then an in-memory join with student/enrollment data
     * — acceptable because the result is paginated (20-50 rows typical).
     */
    @Transactional(readOnly = true)
    public List<DefaulterResponse> listDefaulters(UUID tenantId, int page, int size) {
        LocalDate today = LocalDate.now();
        Page<DefaulterRow> defaulters = invoiceRepository.findDefaulters(
            tenantId, today, PageRequest.of(page, size));

        List<UUID> studentIds = defaulters.getContent().stream()
            .map(DefaulterRow::getStudentId)
            .toList();
        if (studentIds.isEmpty()) return List.of();

        Map<UUID, String> studentNames = studentRepository.findAllById(studentIds).stream()
            .collect(Collectors.toMap(s -> s.getId(), s -> s.displayName()));

        Map<UUID, StudentEnrollment> enrollmentByStudent = studentIds.stream()
            .flatMap(sid -> enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream().limit(1))
            .collect(Collectors.toMap(StudentEnrollment::getStudentId, e -> e));

        return defaulters.getContent().stream().map(row -> {
            StudentEnrollment enr = enrollmentByStudent.get(row.getStudentId());
            String className = null, sectionName = null;
            if (enr != null) {
                var section = sectionRepository.findById(enr.getSectionId()).orElse(null);
                if (section != null) {
                    sectionName = section.getName();
                    className = schoolClassRepository.findById(section.getClassId())
                        .map(c -> c.getName()).orElse(null);
                }
            }
            int daysOverdue = (int) ChronoUnit.DAYS.between(row.getOldestDueDate(), today);
            return new DefaulterResponse(
                row.getStudentId(),
                studentNames.getOrDefault(row.getStudentId(), "—"),
                className,
                sectionName,
                row.getOutstandingPaise(),
                row.getOldestDueDate(),
                row.getInvoiceCount(),
                daysOverdue
            );
        }).toList();
    }

    /** Recent payments for the cashier dashboard (last {@code size} payments, newest first). */
    @Transactional(readOnly = true)
    public List<RecentPaymentRow> listRecentPayments(UUID tenantId, int size) {
        return paymentRepository.findRecentBySchool(tenantId, size).stream()
            .map(p -> new RecentPaymentRow(
                p.getPaymentId(),
                p.getStudentId(),
                p.getStudentName(),
                p.getAmountPaise(),
                PaymentMode.valueOf(p.getPaymentMode()),
                p.getReceiptNumber(),
                p.getReceiptPdfUrl(),
                p.getPaymentDate()
            ))
            .toList();
    }

    /** Class-wise collection report for a date range. */
    @Transactional(readOnly = true)
    public List<ClassCollectionRow> classWiseReport(UUID tenantId, LocalDate from, LocalDate to) {
        return paymentRepository.classWiseReport(tenantId, from, to).stream()
            .map(r -> new ClassCollectionRow(
                r.getClassId(),
                r.getClassName(),
                r.getCollectedPaise(),
                r.getPaymentCount(),
                r.getOutstandingPaise(),
                r.getStudentCount()
            ))
            .toList();
    }
}
