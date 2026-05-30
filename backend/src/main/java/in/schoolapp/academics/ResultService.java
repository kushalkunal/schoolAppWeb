package in.schoolapp.academics;

import in.schoolapp.academics.dto.ExamResultResponse;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.ExamResult;
import in.schoolapp.academics.entity.ExamSubjectConfig;
import in.schoolapp.academics.entity.ResultStatus;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ExamResultRepository;
import in.schoolapp.academics.repository.ExamSubjectConfigRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes, ranks, and publishes per-student exam results.
 * <p>
 * {@link #computeForSection} is idempotent — re-running after a mark correction updates existing
 * {@code ExamResult} rows in place without duplicating them.
 * <p>
 * {@link #publishSection} flips results to {@code PUBLISHED} and delegates PDF generation and
 * notification dispatch to {@link ReportCardService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultService {

    private final ExamResultRepository resultRepository;
    private final ExamMarkRepository markRepository;
    private final ExamSubjectConfigRepository configRepository;
    private final SubjectRepository subjectRepository;
    private final ExamService examService;
    private final ClassSectionService classSectionService;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolService schoolService;
    private final GradeCalculator gradeCalculator;
    private final ReportCardService reportCardService;
    private final AuditLogger auditLogger;

    // ------------------------------------------------------------------
    // Compute
    // ------------------------------------------------------------------

    /**
     * Aggregates all subject marks for every active student in the section, computes percentage,
     * derives grade, determines pass/fail, applies dense rank, and upserts {@code ExamResult} rows.
     */
    @Transactional
    public List<ExamResultResponse> computeForSection(UUID tenantId, UUID examId, UUID sectionId) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        classSectionService.getSectionOrThrow(tenantId, sectionId);
        var board = schoolService.getSchoolEntity(tenantId).getBoard();

        List<StudentEnrollment> roster = enrollmentRepository
            .findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE);
        if (roster.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "No active students found in this section.");
        }

        Map<UUID, Student> studentsById = studentRepository
            .findAllById(roster.stream().map(StudentEnrollment::getStudentId).toList())
            .stream().collect(Collectors.toMap(Student::getId, s -> s));

        // Subject passing-marks: subjectId → min passing across components
        Map<UUID, BigDecimal> subjectPassingMarks = computeSubjectPassingMarks(examId);

        // Pass 1 — aggregate per student
        record Agg(UUID studentId, BigDecimal totalMax, BigDecimal totalObtained,
                   BigDecimal pct, boolean pass, List<ExamMark> marks) {}

        List<Agg> aggregated = new ArrayList<>();
        for (StudentEnrollment enr : roster) {
            List<ExamMark> marks = markRepository
                .findByExamIdAndStudentId(examId, enr.getStudentId());

            BigDecimal totalMax = marks.stream()
                .map(ExamMark::getMaxMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalObtained = marks.stream()
                .filter(m -> !m.isAbsent() && m.getObtainedMarks() != null)
                .map(ExamMark::getObtainedMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pct = totalMax.compareTo(BigDecimal.ZERO) > 0
                ? totalObtained.divide(totalMax, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Pass = all subjects have obtained >= passing marks
            boolean pass = marks.stream().allMatch(m -> {
                BigDecimal passing = subjectPassingMarks.get(m.getSubjectId());
                if (passing == null) return true; // no passing marks configured → auto pass
                BigDecimal obtained = m.getObtainedMarks();
                return !m.isAbsent() && obtained != null && obtained.compareTo(passing) >= 0;
            });

            aggregated.add(new Agg(enr.getStudentId(), totalMax, totalObtained, pct, pass, marks));
        }

        // Pass 2 — dense rank by percentage descending
        List<Agg> sorted = aggregated.stream()
            .sorted(Comparator.comparing(Agg::pct).reversed())
            .toList();
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && sorted.get(i).pct().compareTo(sorted.get(i - 1).pct()) < 0) {
                rank = i + 1;
            }
            sorted.get(i); // read only — rank assigned below during upsert
        }

        // Pass 3 — upsert ExamResult + compute grade
        OffsetDateTime now = OffsetDateTime.now();
        List<ExamResultResponse> responses = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            Agg agg = sorted.get(i);
            int denseRank = i == 0 ? 1
                : (sorted.get(i).pct().compareTo(sorted.get(i - 1).pct()) == 0
                    ? responses.get(i - 1).rankInSection()
                    : i + 1);

            String grade = gradeCalculator.calculate(agg.pct().doubleValue(), board);

            ExamResult result = resultRepository.findByExamIdAndStudentId(examId, agg.studentId())
                .orElseGet(() -> {
                    ExamResult r = new ExamResult();
                    r.setSchoolId(tenantId);
                    r.setExamId(examId);
                    r.setStudentId(agg.studentId());
                    r.setSectionId(sectionId);
                    return r;
                });

            result.setTotalMax(agg.totalMax());
            result.setTotalObtained(agg.totalObtained());
            result.setPercentage(agg.pct());
            result.setGrade(grade);
            result.setRankInSection(denseRank);
            result.setPass(agg.pass());
            result.setStatus(ResultStatus.READY);
            result.setComputedAt(now);
            resultRepository.save(result);

            Student student = studentsById.get(agg.studentId());
            StudentEnrollment enr = roster.stream()
                .filter(e -> e.getStudentId().equals(agg.studentId()))
                .findFirst().orElse(null);
            String name = student != null
                ? student.getFirstName() + (student.getLastName() != null ? " " + student.getLastName() : "")
                : agg.studentId().toString();

            responses.add(ExamResultResponse.from(result, name,
                student != null ? student.getAdmissionNumber() : null,
                enr != null ? enr.getRollNumber() : null));
        }

        // Update exam result_status if not yet published
        exam = examService.getExamOrThrow(tenantId, examId);
        if (exam.getResultStatus() == in.schoolapp.academics.entity.ResultStatus.DRAFT) {
            exam.setResultStatus(in.schoolapp.academics.entity.ResultStatus.READY);
        }

        log.info("Computed results for examId={} sectionId={} count={}", examId, sectionId, responses.size());
        return responses;
    }

    // ------------------------------------------------------------------
    // Publish
    // ------------------------------------------------------------------

    /**
     * Publishes all {@code READY} results in the section, triggers PDF generation, and fires
     * notification events.
     */
    @Transactional
    public List<ExamResultResponse> publishSection(UUID tenantId, UUID examId, UUID sectionId) {
        examService.getExamOrThrow(tenantId, examId);
        classSectionService.getSectionOrThrow(tenantId, sectionId);

        List<ExamResult> readyResults = resultRepository
            .findByExamIdAndSectionIdAndStatus(examId, sectionId, ResultStatus.READY);
        if (readyResults.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "No READY results found. Run Compute Results first.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        readyResults.forEach(r -> {
            r.setStatus(ResultStatus.PUBLISHED);
            r.setPublishedAt(now);
            resultRepository.save(r);
        });

        // Generate PDFs + fire notification events
        reportCardService.generateForSection(tenantId, examId, sectionId);

        // Update exam status
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        exam.setPublished(true);
        exam.setResultStatus(in.schoolapp.academics.entity.ResultStatus.PUBLISHED);

        log.info("Published results for examId={} sectionId={} count={}", examId, sectionId, readyResults.size());

        return getResultsForSection(tenantId, examId, sectionId);
    }

    // ------------------------------------------------------------------
    // Unlock (admin reopen)
    // ------------------------------------------------------------------

    /**
     * Reverts a published section's results back to {@code READY} so that marks can be
     * corrected and results re-computed. Requires an explicit {@code reason} for the audit trail.
     * Only {@code OWNER} / {@code ADMIN} / {@code PRINCIPAL} roles may call this (enforced by
     * the controller's {@code @PreAuthorize}).
     */
    @Transactional
    public List<ExamResultResponse> unlockSection(UUID tenantId, UUID examId, UUID sectionId, String reason) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        classSectionService.getSectionOrThrow(tenantId, sectionId);

        if (!exam.isPublished()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Exam results are not published yet — nothing to unlock.");
        }

        List<ExamResult> published = resultRepository
            .findByExamIdAndSectionIdAndStatus(examId, sectionId, ResultStatus.PUBLISHED);

        OffsetDateTime now = OffsetDateTime.now();
        published.forEach(r -> {
            r.setStatus(ResultStatus.READY);
            r.setPublishedAt(null);
            resultRepository.save(r);
        });

        // Revert exam-level lock so marks can be re-entered
        exam.setPublished(false);
        exam.setResultStatus(ResultStatus.READY);

        auditLogger.logAction(tenantId, "ExamResult", examId,
            "UNLOCK_RESULTS",
            java.util.Map.of(
                "sectionId", sectionId,
                "reason", reason != null ? reason : "",
                "affectedRows", published.size(),
                "unlockedAt", now.toString()
            ));

        log.info("Unlocked results for examId={} sectionId={} count={} reason={}",
            examId, sectionId, published.size(), reason);

        return getResultsForSection(tenantId, examId, sectionId);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ExamResultResponse> getResultsForSection(UUID tenantId, UUID examId, UUID sectionId) {
        examService.getExamOrThrow(tenantId, examId);

        List<ExamResult> results = resultRepository
            .findByExamIdAndSectionIdOrderByRankInSectionAsc(examId, sectionId);

        List<UUID> studentIds = results.stream().map(ExamResult::getStudentId).toList();
        Map<UUID, Student> students = studentRepository.findAllById(studentIds).stream()
            .collect(Collectors.toMap(Student::getId, s -> s));
        Map<UUID, StudentEnrollment> enrollments = enrollmentRepository
            .findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE).stream()
            .collect(Collectors.toMap(StudentEnrollment::getStudentId, e -> e));

        return results.stream()
            .map(r -> {
                Student s = students.get(r.getStudentId());
                StudentEnrollment enr = enrollments.get(r.getStudentId());
                String name = s != null
                    ? s.getFirstName() + (s.getLastName() != null ? " " + s.getLastName() : "")
                    : r.getStudentId().toString();
                return ExamResultResponse.from(r, name,
                    s != null ? s.getAdmissionNumber() : null,
                    enr != null ? enr.getRollNumber() : null);
            })
            .toList();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * For each subject in the exam, computes the total passing marks by summing component
     * passing marks. If no passing marks are configured, returns empty map (auto-pass).
     */
    private Map<UUID, BigDecimal> computeSubjectPassingMarks(UUID examId) {
        List<ExamSubjectConfig> configs = configRepository
            .findByExamIdOrderBySubjectIdAscSortOrderAsc(examId);

        return configs.stream()
            .filter(c -> c.getPassingMarks() != null)
            .collect(Collectors.groupingBy(
                ExamSubjectConfig::getSubjectId,
                Collectors.reducing(BigDecimal.ZERO, ExamSubjectConfig::getPassingMarks, BigDecimal::add)
            ));
    }
}
