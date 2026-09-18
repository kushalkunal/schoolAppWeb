package in.schoolapp.academics;

import in.schoolapp.academics.dto.CreateExamRequest;
import in.schoolapp.academics.dto.ExamResponse;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamClass;
import in.schoolapp.academics.entity.FeePolicy;
import in.schoolapp.academics.repository.ExamClassRepository;
import in.schoolapp.academics.repository.ExamRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.entity.AcademicYear;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamClassRepository examClassRepository;
    private final in.schoolapp.academics.repository.ExamMarkRepository examMarkRepository;
    private final in.schoolapp.academics.repository.ReportCardRepository reportCardRepository;
    private final AcademicYearService academicYearService;
    private final AuditLogger auditLogger;

    @Transactional
    public ExamResponse createExam(UUID tenantId, CreateExamRequest req) {
        AcademicYear year = academicYearService.resolveOrCurrent(tenantId, req.academicYearId());

        Exam exam = new Exam();
        exam.setSchoolId(tenantId);
        exam.setAcademicYearId(year.getId());
        exam.setName(req.name().trim());
        exam.setExamType(req.examType());
        exam.setStartDate(req.startDate());
        exam.setEndDate(req.endDate());
        exam.setClassId(req.classId());
        exam.setSectionId(req.sectionId());
        exam.setFeePolicy(req.feePolicy() == null ? FeePolicy.BLOCK : req.feePolicy());
        exam = examRepository.save(exam);

        List<UUID> classIds = setParticipatingClasses(tenantId, exam.getId(), req.classIds());
        log.info("Created exam id={} tenantId={} name={} classes={}", exam.getId(), tenantId, exam.getName(), classIds.size());
        return ExamResponse.from(exam, classIds);
    }

    /** Replace the set of classes participating in the exam. Returns the resulting class ids. */
    @Transactional
    public List<UUID> setParticipatingClasses(UUID tenantId, UUID examId, List<UUID> classIds) {
        getExamOrThrow(tenantId, examId); // tenant-scope guard
        examClassRepository.deleteByExamId(examId);
        if (classIds == null || classIds.isEmpty()) return List.of();
        List<UUID> distinct = classIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        examClassRepository.saveAll(distinct.stream()
            .map(cid -> new ExamClass(examId, cid, tenantId)).toList());
        return distinct;
    }

    @Transactional(readOnly = true)
    public List<UUID> participatingClassIds(UUID examId) {
        return examClassRepository.findByExamId(examId).stream().map(ExamClass::getClassId).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listCurrentYearExams(UUID tenantId) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        return examRepository
            .findBySchoolIdAndAcademicYearIdOrderByStartDateDesc(tenantId, year.getId()).stream()
            .map(e -> ExamResponse.from(e, participatingClassIds(e.getId())))
            .toList();
    }

    @Transactional
    public ExamResponse publishExam(UUID tenantId, UUID examId) {
        Exam exam = getExamOrThrow(tenantId, examId);
        exam.setPublished(true);
        log.info("Published exam id={} tenantId={}", examId, tenantId);
        auditLogger.logAction(tenantId, "Exam", examId, "PUBLISH", java.util.Map.of(
            "examName", exam.getName() == null ? "" : exam.getName()
        ));
        return ExamResponse.from(exam, participatingClassIds(examId));
    }

    /**
     * Delete an exam and all its scoped data. The DB cascades exam_classes, exam_schedule,
     * exam_subject_configs (→ component marks), exam_results, exam_section_submissions and
     * admit_cards; the two NO-ACTION children (exam_marks, report_cards) are cleared first.
     */
    @Transactional
    public void deleteExam(UUID tenantId, UUID examId) {
        Exam exam = getExamOrThrow(tenantId, examId);
        examMarkRepository.deleteByExamId(examId);
        reportCardRepository.deleteByExamId(examId);
        examRepository.delete(exam);
        log.info("Deleted exam id={} tenantId={}", examId, tenantId);
        auditLogger.logAction(tenantId, "Exam", examId, "DELETE", java.util.Map.of(
            "examName", exam.getName() == null ? "" : exam.getName()
        ));
    }

    Exam getExamOrThrow(UUID tenantId, UUID examId) {
        return examRepository.findByIdAndSchoolId(examId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.EXAM_NOT_FOUND, "Exam", examId));
    }
}
