package in.schoolapp.academics;

import in.schoolapp.academics.dto.ConfigureExamStructureRequest;
import in.schoolapp.academics.dto.ExamStructureResponse;
import in.schoolapp.academics.dto.ExamStructureResponse.ComponentDto;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamSubjectConfig;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.ExamSubjectConfigRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the per-exam subject-component configuration (the marking scheme).
 * <p>
 * Calling {@link #configure} for a given {@code (examId, subjectId)} pair replaces all existing
 * components for that subject atomically — safe for admin corrections before marks entry begins.
 * Once the exam is published, structure changes are rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamStructureService {

    private final ExamSubjectConfigRepository configRepository;
    private final SubjectRepository subjectRepository;
    private final ExamService examService;

    /**
     * Replace the marking-scheme components for one {@code (exam, subject)} pair.
     * Existing components for this pair are deleted and replaced in one transaction.
     */
    @Transactional
    public ExamStructureResponse configure(UUID tenantId, UUID examId,
                                           ConfigureExamStructureRequest req) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        if (exam.isPublished()) {
            throw new AppException(ErrorCode.MARKS_ALREADY_FINALIZED,
                "Cannot change exam structure after the exam has been published.");
        }

        Subject subject = subjectRepository.findById(req.subjectId())
            .filter(s -> s.getSchoolId().equals(tenantId))
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                "Subject not found in this tenant: " + req.subjectId()));

        // Replace atomically
        configRepository.deleteByExamIdAndSubjectId(examId, req.subjectId());

        List<ExamSubjectConfig> saved = req.components().stream()
            .map(comp -> {
                ExamSubjectConfig c = new ExamSubjectConfig();
                c.setSchoolId(tenantId);
                c.setExamId(examId);
                c.setSubjectId(req.subjectId());
                c.setComponentName(comp.componentName().trim());
                c.setMaxMarks(comp.maxMarks());
                c.setPassingMarks(comp.passingMarks());
                c.setSortOrder(comp.sortOrder());
                return c;
            })
            .map(configRepository::save)
            .toList();

        log.info("Configured {} components for exam={} subject={}", saved.size(), examId, req.subjectId());
        return toResponse(subject, saved);
    }

    /**
     * Returns all subject configs for an exam, grouped into one response per subject.
     */
    @Transactional(readOnly = true)
    public List<ExamStructureResponse> listStructure(UUID tenantId, UUID examId) {
        examService.getExamOrThrow(tenantId, examId);

        List<ExamSubjectConfig> all = configRepository
            .findByExamIdOrderBySubjectIdAscSortOrderAsc(examId);

        Map<UUID, List<ExamSubjectConfig>> bySubject = all.stream()
            .collect(Collectors.groupingBy(ExamSubjectConfig::getSubjectId));

        List<UUID> subjectIds = bySubject.keySet().stream().toList();
        Map<UUID, Subject> subjects = subjectRepository.findAllById(subjectIds).stream()
            .collect(Collectors.toMap(Subject::getId, s -> s));

        return bySubject.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> toResponse(subjects.get(e.getKey()), e.getValue()))
            .toList();
    }

    /** Package-private helper — returns raw config list per subjectId. */
    Map<UUID, List<ExamSubjectConfig>> getStructureMap(UUID examId) {
        return configRepository.findByExamIdOrderBySubjectIdAscSortOrderAsc(examId).stream()
            .collect(Collectors.groupingBy(ExamSubjectConfig::getSubjectId));
    }

    private static ExamStructureResponse toResponse(Subject subject,
                                                     List<ExamSubjectConfig> configs) {
        BigDecimal total = configs.stream()
            .map(ExamSubjectConfig::getMaxMarks)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExamStructureResponse(
            subject.getId(),
            subject.getName(),
            configs.stream().map(ComponentDto::from).toList(),
            total
        );
    }
}
