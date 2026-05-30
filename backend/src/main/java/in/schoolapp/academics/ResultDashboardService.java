package in.schoolapp.academics;

import in.schoolapp.academics.dto.ResultDashboardResponse;
import in.schoolapp.academics.dto.ResultDashboardResponse.SectionSummary;
import in.schoolapp.academics.dto.ResultDashboardResponse.SubjectAverage;
import in.schoolapp.academics.dto.ResultDashboardResponse.TopperDto;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.ExamResult;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ExamResultRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only analytics over computed exam results.
 * Returns per-section pass rates, toppers, subject averages, and grade distributions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultDashboardService {

    private final ExamResultRepository resultRepository;
    private final ExamMarkRepository markRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSectionService classSectionService;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final ExamService examService;

    @Transactional(readOnly = true)
    public ResultDashboardResponse getDashboard(UUID tenantId, UUID examId) {
        var exam = examService.getExamOrThrow(tenantId, examId);

        // All results for this exam
        List<ExamResult> allResults = resultRepository.findByExamIdOrderByPercentageDesc(examId);

        // Group by section
        Map<UUID, List<ExamResult>> bySectionId = allResults.stream()
            .collect(Collectors.groupingBy(ExamResult::getSectionId));

        // Load sections
        Map<UUID, Section> sections = bySectionId.keySet().stream()
            .map(sid -> classSectionService.getSectionOrThrow(tenantId, sid))
            .collect(Collectors.toMap(Section::getId, s -> s));

        // Subject map
        Map<UUID, Subject> subjectsById = subjectRepository.findBySchoolIdOrderByName(tenantId).stream()
            .collect(Collectors.toMap(Subject::getId, s -> s));

        // Students
        List<UUID> allStudentIds = allResults.stream().map(ExamResult::getStudentId).toList();
        Map<UUID, Student> studentsById = studentRepository.findAllById(allStudentIds).stream()
            .collect(Collectors.toMap(Student::getId, s -> s));

        List<SectionSummary> sectionSummaries = new ArrayList<>();

        for (Map.Entry<UUID, List<ExamResult>> entry : bySectionId.entrySet()) {
            UUID sectionId = entry.getKey();
            List<ExamResult> sectionResults = entry.getValue();
            Section section = sections.get(sectionId);

            long passCount = sectionResults.stream().filter(ExamResult::isPass).count();
            long failCount = sectionResults.size() - passCount;
            BigDecimal passPct = sectionResults.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(passCount)
                    .divide(BigDecimal.valueOf(sectionResults.size()), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Topper = rank 1
            ExamResult topperResult = sectionResults.stream()
                .min(Comparator.comparing(r -> r.getRankInSection() != null ? r.getRankInSection() : Integer.MAX_VALUE))
                .orElse(null);
            TopperDto topper = null;
            if (topperResult != null) {
                Student ts = studentsById.get(topperResult.getStudentId());
                String topperName = ts != null
                    ? ts.getFirstName() + (ts.getLastName() != null ? " " + ts.getLastName() : "")
                    : topperResult.getStudentId().toString();
                topper = new TopperDto(topperResult.getStudentId(), topperName,
                    topperResult.getPercentage(), topperResult.getGrade());
            }

            // Subject averages
            List<ExamMark> sectionMarks = markRepository
                .findByExamIdAndSectionIdOrderByStudentId(examId, sectionId);
            Map<UUID, List<ExamMark>> marksBySubject = sectionMarks.stream()
                .collect(Collectors.groupingBy(ExamMark::getSubjectId));
            List<SubjectAverage> subjectAverages = marksBySubject.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<ExamMark> subjectMarks = e.getValue();
                    BigDecimal avgObtained = subjectMarks.stream()
                        .filter(m -> !m.isAbsent() && m.getObtainedMarks() != null)
                        .map(ExamMark::getObtainedMarks)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long nonAbsent = subjectMarks.stream().filter(m -> !m.isAbsent()).count();
                    if (nonAbsent > 0) {
                        avgObtained = avgObtained.divide(BigDecimal.valueOf(nonAbsent), 2, RoundingMode.HALF_UP);
                    }
                    BigDecimal maxMarks = subjectMarks.stream()
                        .map(ExamMark::getMaxMarks)
                        .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                    Subject sub = subjectsById.get(e.getKey());
                    return new SubjectAverage(e.getKey(),
                        sub != null ? sub.getName() : e.getKey().toString(),
                        avgObtained, maxMarks);
                })
                .toList();

            // Grade distribution (preserve insertion order)
            Map<String, Long> gradeDist = new LinkedHashMap<>();
            sectionResults.stream()
                .filter(r -> r.getGrade() != null)
                .collect(Collectors.groupingBy(ExamResult::getGrade, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> gradeDist.put(e.getKey(), e.getValue()));

            String sectionLabel = section != null ? section.getName() : sectionId.toString();
            sectionSummaries.add(new SectionSummary(
                sectionId, sectionLabel, sectionResults.size(),
                passCount, failCount, passPct, topper, subjectAverages, gradeDist
            ));
        }

        sectionSummaries.sort(Comparator.comparing(SectionSummary::sectionName));
        return new ResultDashboardResponse(examId, exam.getName(), sectionSummaries);
    }
}
