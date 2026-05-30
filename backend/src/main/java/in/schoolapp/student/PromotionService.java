package in.schoolapp.student;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.AcademicYearRepository;
import in.schoolapp.student.dto.BulkPromotionRequest;
import in.schoolapp.student.dto.BulkPromotionResponse;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the annual student promotion workflow:
 * <ol>
 *   <li>Each student in the source section is closed (PROMOTED or RETAINED).</li>
 *   <li>A new {@link StudentEnrollment} is created in the target section under the new
 *       academic year (or retained section for failed students).</li>
 *   <li>Students already enrolled in the new year are skipped to make the operation
 *       idempotent.</li>
 * </ol>
 *
 * <p>Also provides {@link #rolloverAcademicYear} which creates the next academic year and
 * marks the current one as no-longer-current.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final StudentEnrollmentRepository enrollmentRepository;
    private final ClassSectionService classSectionService;
    private final AcademicYearService academicYearService;
    private final AcademicYearRepository academicYearRepository;
    private final AuditLogger auditLogger;

    // -----------------------------------------------------------------------
    // Bulk Promotion
    // -----------------------------------------------------------------------

    @Transactional
    public BulkPromotionResponse promoteSection(UUID tenantId, BulkPromotionRequest req) {
        Section sourceSection = classSectionService.getSectionOrThrow(tenantId, req.sourceSectionId());
        Section targetSection = classSectionService.getSectionOrThrow(tenantId, req.targetSectionId());

        // Target section must belong to a DIFFERENT (newer) academic year.
        if (sourceSection.getAcademicYearId().equals(targetSection.getAcademicYearId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Source and target sections must be in different academic years. "
                    + "Create the new academic year first via POST /academic-years/rollover.");
        }

        // Optional retained section
        Section retainedSection = req.retainedSectionId() != null
            ? classSectionService.getSectionOrThrow(tenantId, req.retainedSectionId())
            : null;
        // If retained section provided, it must be in the same new year as target section
        if (retainedSection != null
                && !retainedSection.getAcademicYearId().equals(targetSection.getAcademicYearId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "retainedSectionId must be in the same academic year as targetSectionId.");
        }

        UUID newYearId = targetSection.getAcademicYearId();
        Set<UUID> failedSet = Set.copyOf(req.failedStudentIds());

        List<StudentEnrollment> activeRoster = enrollmentRepository
            .findBySectionIdAndStatus(req.sourceSectionId(), EnrollmentStatus.ACTIVE);

        List<UUID> promoted = new ArrayList<>();
        List<UUID> retained = new ArrayList<>();
        List<UUID> skipped  = new ArrayList<>();

        for (StudentEnrollment old : activeRoster) {
            UUID studentId = old.getStudentId();

            // Idempotency: skip if already enrolled in the new academic year.
            if (enrollmentRepository.findByStudentIdAndAcademicYearId(studentId, newYearId).isPresent()) {
                skipped.add(studentId);
                continue;
            }

            boolean isFailed = failedSet.contains(studentId);
            UUID newSectionId = isFailed
                ? (retainedSection != null ? retainedSection.getId() : req.sourceSectionId())
                : targetSection.getId();

            // Close old enrollment
            old.setStatus(isFailed ? EnrollmentStatus.RETAINED : EnrollmentStatus.PROMOTED);
            enrollmentRepository.save(old);

            // Create new enrollment in the new year
            StudentEnrollment newEnrollment = new StudentEnrollment();
            newEnrollment.setSchoolId(tenantId);
            newEnrollment.setStudentId(studentId);
            newEnrollment.setAcademicYearId(newYearId);
            newEnrollment.setSectionId(newSectionId);
            newEnrollment.setStatus(EnrollmentStatus.ACTIVE);
            enrollmentRepository.save(newEnrollment);

            if (isFailed) {
                retained.add(studentId);
            } else {
                promoted.add(studentId);
            }
        }

        auditLogger.logAction(tenantId, "StudentPromotion", req.sourceSectionId(),
            "BULK_PROMOTE",
            Map.of(
                "sourceSectionId", req.sourceSectionId(),
                "targetSectionId", req.targetSectionId(),
                "promoted", promoted.size(),
                "retained", retained.size(),
                "skipped", skipped.size(),
                "runAt", OffsetDateTime.now().toString()
            ));

        log.info("Bulk promotion: tenant={} source={} target={} promoted={} retained={} skipped={}",
            tenantId, req.sourceSectionId(), req.targetSectionId(),
            promoted.size(), retained.size(), skipped.size());

        return new BulkPromotionResponse(
            promoted.size(), retained.size(), skipped.size(),
            promoted, retained, skipped);
    }

    // -----------------------------------------------------------------------
    // Academic Year Rollover
    // -----------------------------------------------------------------------

    /**
     * Creates the next academic year for the school and flips {@code isCurrent} from the old
     * year to the new one. The old year is preserved with all its data — only the {@code current}
     * flag changes.
     *
     * <p>Indian school years run April → March. Calling this in March 2026 creates 2026-2027
     * and deactivates 2025-2026.
     */
    @Transactional
    public AcademicYear rolloverAcademicYear(UUID tenantId) {
        AcademicYear current = academicYearService.getCurrentOrThrow(tenantId);

        // Compute next year dates
        int nextStartYear = current.getEndDate().getYear(); // e.g. 2026 for 2025-2026
        LocalDate nextStart = LocalDate.of(nextStartYear, Month.APRIL, 1);
        LocalDate nextEnd   = LocalDate.of(nextStartYear + 1, Month.MARCH, 31);
        String nextName     = nextStartYear + "-" + (nextStartYear + 1);

        if (academicYearRepository.existsBySchoolIdAndName(tenantId, nextName)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Academic year " + nextName + " already exists. Rollover already done.");
        }

        // Deactivate current year
        current.setCurrent(false);
        academicYearRepository.save(current);

        // Create new year
        AcademicYear next = new AcademicYear();
        next.setSchoolId(tenantId);
        next.setName(nextName);
        next.setStartDate(nextStart);
        next.setEndDate(nextEnd);
        next.setCurrent(true);
        next = academicYearRepository.save(next);

        auditLogger.logCreate(tenantId, "AcademicYear", next.getId(), Map.of(
            "name", next.getName(),
            "startDate", next.getStartDate().toString(),
            "endDate", next.getEndDate().toString(),
            "previousYearId", current.getId().toString()
        ));

        log.info("Academic year rolled over: tenant={} old={} new={}", tenantId, current.getName(), next.getName());
        return next;
    }

    public List<AcademicYear> listAcademicYears(UUID tenantId) {
        return academicYearRepository.findBySchoolIdOrderByStartDateDesc(tenantId);
    }
}
