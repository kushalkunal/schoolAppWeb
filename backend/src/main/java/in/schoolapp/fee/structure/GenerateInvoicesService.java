package in.schoolapp.fee.structure;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.structure.dto.GenerateInvoicesRequest;
import in.schoolapp.fee.structure.dto.GenerateInvoicesResponse;
import in.schoolapp.fee.structure.entity.FeeStructureRow;
import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.repository.FeeStructureRowRepository;
import in.schoolapp.fee.structure.repository.FeeStructureTermRepository;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk-generates {@link FeeInvoice}s for every active student whose class is referenced by a
 * given fee-structure version. Idempotent: the partial unique index
 * {@code uq_fee_invoices_per_structure} (Flyway V20) catches duplicate (student × head × term ×
 * version) tuples and we simply skip them.
 * <p>
 * Behavior with terms:
 *   - If {@code termNumber} is given, generates invoices only for matrix rows that match that
 *     term (and only annual rows are excluded).
 *   - If no term is given AND the version has terms, generates for every term and the annual
 *     rows. This is the "create everything for the year" mode used at session start.
 *   - If the version has zero terms, the whole version is treated as a single annual billing.
 */
@Service
@RequiredArgsConstructor
public class GenerateInvoicesService {

    private static final Logger log = LoggerFactory.getLogger(GenerateInvoicesService.class);

    private final FeeStructureService structureService;
    private final FeeStructureTermRepository termRepo;
    private final FeeStructureRowRepository rowRepo;
    private final FeeInvoiceRepository invoiceRepo;
    private final SectionRepository sectionRepo;
    private final StudentEnrollmentRepository enrollmentRepo;
    private final AuditLogger auditLogger;

    @Transactional
    public GenerateInvoicesResponse generate(UUID tenantId, UUID versionId,
                                             GenerateInvoicesRequest req) {
        FeeStructureVersion version = structureService.requireActive(tenantId, versionId);

        List<FeeStructureTerm> allTerms = termRepo.findByVersionIdOrderByTermNumberAsc(versionId);
        List<FeeStructureRow> allRows = rowRepo.findByVersionId(versionId);
        if (allRows.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Fee structure has no rows — cannot generate invoices");
        }

        // Decide which terms to materialize.
        List<Integer> termsToProcess;
        if (req != null && req.termNumber() != null) {
            int t = req.termNumber();
            if (allTerms.stream().noneMatch(x -> x.getTermNumber() == t)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Term " + t + " is not defined in this version");
            }
            termsToProcess = List.of(t);
        } else if (allTerms.isEmpty()) {
            // Annual-only version: process a single "null term" pass.
            termsToProcess = java.util.Collections.singletonList(null);
        } else {
            termsToProcess = new java.util.ArrayList<>();
            for (FeeStructureTerm t : allTerms) termsToProcess.add(t.getTermNumber());
            termsToProcess.add(null); // annual rows ride along
        }

        // Index sections in this academic year by classId for fast student-lookup.
        List<Section> sectionsInYear =
            sectionRepo.findBySchoolIdAndAcademicYearId(tenantId, version.getAcademicYearId());

        int invoicesCreated = 0;
        int duplicatesSkipped = 0;
        long totalAmountPaise = 0;
        java.util.Set<UUID> studentsTouched = new java.util.HashSet<>();

        for (Integer termNumber : termsToProcess) {
            LocalDate dueDate = dueDateFor(termNumber, allTerms, version);

            // For each row applicable to this term…
            for (FeeStructureRow row : allRows) {
                if (!java.util.Objects.equals(row.getTermNumber(), termNumber)) continue;
                if (row.isOptional()) continue; // per-student opt-in deferred to a later slice
                if (row.getAmountPaise() == 0) continue;

                // …enumerate students enrolled in any section of that class for this year.
                for (Section sec : sectionsInYear) {
                    if (!sec.getClassId().equals(row.getClassId())) continue;
                    List<StudentEnrollment> enrollments =
                        enrollmentRepo.findBySectionIdAndStatus(sec.getId(), EnrollmentStatus.ACTIVE);

                    for (StudentEnrollment enr : enrollments) {
                        try {
                            FeeInvoice inv = new FeeInvoice();
                            inv.setSchoolId(tenantId);
                            inv.setStudentId(enr.getStudentId());
                            inv.setFeeHeadId(row.getFeeHeadId());
                            inv.setAmountDuePaise(row.getAmountPaise());
                            inv.setDueDate(dueDate);
                            inv.setStatus(InvoiceStatus.PENDING);
                            inv.setAcademicYearId(version.getAcademicYearId());
                            inv.setStructureVersionId(versionId);
                            inv.setStructureTermNumber(termNumber);
                            inv.setDescription(version.getName()
                                + (termNumber == null ? " — Annual" : " — Term " + termNumber));
                            invoiceRepo.saveAndFlush(inv);
                            invoicesCreated++;
                            totalAmountPaise += row.getAmountPaise();
                            studentsTouched.add(enr.getStudentId());
                        } catch (DataIntegrityViolationException dup) {
                            // Partial unique index hit — invoice already exists for this
                            // (student, head, version, term). Silently skip; that's the
                            // whole point of the idempotency guard.
                            duplicatesSkipped++;
                        }
                    }
                }
            }
        }

        auditLogger.logCreate(tenantId, "FeeStructureInvoiceBatch", versionId, Map.of(
            "termNumber", req == null || req.termNumber() == null ? "all" : req.termNumber(),
            "invoicesCreated", invoicesCreated,
            "duplicatesSkipped", duplicatesSkipped,
            "totalAmountPaise", totalAmountPaise
        ));
        log.info("Generated {} invoices ({} duplicates skipped) for version={} term={}",
            invoicesCreated, duplicatesSkipped, versionId,
            req == null ? null : req.termNumber());

        return new GenerateInvoicesResponse(
            studentsTouched.size(), invoicesCreated, duplicatesSkipped, totalAmountPaise);
    }

    private LocalDate dueDateFor(Integer termNumber, List<FeeStructureTerm> terms,
                                 FeeStructureVersion version) {
        if (termNumber == null) {
            // Annual: due at the earliest known term start, or today + 30 days as a safe default.
            return terms.stream().map(FeeStructureTerm::getDueDate)
                .min(LocalDate::compareTo).orElse(LocalDate.now().plusDays(30));
        }
        return terms.stream()
            .filter(t -> t.getTermNumber() == termNumber)
            .map(FeeStructureTerm::getDueDate)
            .findFirst()
            .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                "No due date defined for term " + termNumber));
    }
}
