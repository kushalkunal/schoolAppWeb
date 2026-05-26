package in.schoolapp.fee.structure;

import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.structure.entity.FeeStructureRow;
import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.entity.FeeStructureVersion.Status;
import in.schoolapp.fee.structure.repository.FeeStructureRowRepository;
import in.schoolapp.fee.structure.repository.FeeStructureTermRepository;
import in.schoolapp.fee.structure.repository.FeeStructureVersionRepository;
import in.schoolapp.student.event.StudentEnrolledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Slice 31d — auto-invoice mid-year admits.
 *
 * <p>When a {@link StudentEnrolledEvent} fires we check whether the tenant has an ACTIVE
 * {@link FeeStructureVersion} for the same academic year and, if so, issue invoices for the
 * newly-enrolled student covering:
 *
 * <ul>
 *   <li>every annual matrix row for their class (term_number is null), and</li>
 *   <li>every per-term row whose term has not yet ended on the day they joined.</li>
 * </ul>
 *
 * Terms that already ended are skipped — the assumption is that a fresh joiner shouldn't be
 * billed retroactively for a quarter they didn't attend. Schools that want full-year billing
 * can manually issue the back-term invoice from the matrix editor's "Generate" flow.
 *
 * <p>We listen <em>after commit</em> so the {@code student_enrollments} row is durable before
 * we start writing invoices. Failure here logs + swallows; the student is created either way.
 * Idempotency is enforced by the partial unique index {@code uq_fee_invoices_per_structure}
 * (Flyway V20) — duplicate inserts surface as {@link DataIntegrityViolationException} and we
 * skip silently, mirroring the bulk generator's behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MidYearAutoInvoiceListener {

    private final FeeStructureVersionRepository versionRepo;
    private final FeeStructureTermRepository termRepo;
    private final FeeStructureRowRepository rowRepo;
    private final FeeInvoiceRepository invoiceRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onStudentEnrolled(StudentEnrolledEvent ev) {
        try {
            generate(ev);
        } catch (Exception e) {
            // Never break the student-create flow over auto-invoicing — the school can always
            // hit "Generate" from the editor.
            log.warn("Auto-invoice failed for student={} tenant={}: {}",
                ev.studentId(), ev.tenantId(), e.getMessage());
        }
    }

    private void generate(StudentEnrolledEvent ev) {
        FeeStructureVersion active = versionRepo
            .findFirstBySchoolIdAndAcademicYearIdAndStatus(
                ev.tenantId(), ev.academicYearId(), Status.ACTIVE)
            .orElse(null);
        if (active == null) {
            log.debug("No ACTIVE fee structure for tenant={} year={} — skipping auto-invoice",
                ev.tenantId(), ev.academicYearId());
            return;
        }

        List<FeeStructureRow> rows = rowRepo.findByVersionIdAndClassId(active.getId(), ev.classId());
        if (rows.isEmpty()) return;
        List<FeeStructureTerm> terms = termRepo.findByVersionIdOrderByTermNumberAsc(active.getId());

        LocalDate today = LocalDate.now();
        int created = 0;
        int skipped = 0;

        for (FeeStructureRow row : rows) {
            if (row.isOptional()) continue;          // opt-in heads need explicit subscription
            if (row.getAmountPaise() == 0) continue;

            Integer termNumber = row.getTermNumber();
            LocalDate dueDate;
            if (termNumber == null) {
                // Annual row: due 30 days from today as a safe default, matching the bulk
                // generator's annual-version fallback when no terms exist.
                dueDate = today.plusDays(30);
            } else {
                FeeStructureTerm term = terms.stream()
                    .filter(t -> Objects.equals(t.getTermNumber(), termNumber))
                    .findFirst().orElse(null);
                if (term == null) continue;          // matrix row references missing term — skip defensively
                if (term.getEndDate().isBefore(today)) continue;  // term already over: don't bill retroactively
                // If the term's due date already passed, use today + 7 days as a grace window;
                // otherwise honour the configured due date.
                dueDate = term.getDueDate().isBefore(today) ? today.plusDays(7) : term.getDueDate();
            }

            try {
                FeeInvoice inv = new FeeInvoice();
                inv.setSchoolId(ev.tenantId());
                inv.setStudentId(ev.studentId());
                inv.setFeeHeadId(row.getFeeHeadId());
                inv.setAmountDuePaise(row.getAmountPaise());
                inv.setDueDate(dueDate);
                inv.setStatus(InvoiceStatus.PENDING);
                inv.setAcademicYearId(ev.academicYearId());
                inv.setStructureVersionId(active.getId());
                inv.setStructureTermNumber(termNumber);
                inv.setDescription(active.getName()
                    + (termNumber == null ? " — Annual (auto)" : " — Term " + termNumber + " (auto)"));
                invoiceRepo.saveAndFlush(inv);
                created++;
            } catch (DataIntegrityViolationException dup) {
                // Already issued — fine, the operator may have run the bulk generator first.
                skipped++;
            }
        }
        log.info("Auto-invoiced student={} version={} created={} skipped={}",
            ev.studentId(), active.getId(), created, skipped);
    }
}
