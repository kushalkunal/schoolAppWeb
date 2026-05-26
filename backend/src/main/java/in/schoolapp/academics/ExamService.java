package in.schoolapp.academics;

import in.schoolapp.academics.dto.CreateExamRequest;
import in.schoolapp.academics.dto.ExamResponse;
import in.schoolapp.academics.entity.Exam;
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
    private final AcademicYearService academicYearService;
    private final AuditLogger auditLogger;

    @Transactional
    public ExamResponse createExam(UUID tenantId, CreateExamRequest req) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);

        Exam exam = new Exam();
        exam.setSchoolId(tenantId);
        exam.setAcademicYearId(year.getId());
        exam.setName(req.name().trim());
        exam.setExamType(req.examType());
        exam.setStartDate(req.startDate());
        exam.setEndDate(req.endDate());
        exam = examRepository.save(exam);
        log.info("Created exam id={} tenantId={} name={}", exam.getId(), tenantId, exam.getName());
        return ExamResponse.from(exam);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listCurrentYearExams(UUID tenantId) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        return examRepository
            .findBySchoolIdAndAcademicYearIdOrderByStartDateDesc(tenantId, year.getId()).stream()
            .map(ExamResponse::from)
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
        return ExamResponse.from(exam);
    }

    Exam getExamOrThrow(UUID tenantId, UUID examId) {
        return examRepository.findByIdAndSchoolId(examId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.EXAM_NOT_FOUND, "Exam", examId));
    }
}
