package in.schoolapp.academics;

import in.schoolapp.academics.dto.ReportCardResponse;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.ReportCard;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.event.ReportCardGeneratedEvent;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Section;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.storage.dto.StoredFile;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates report cards for every student in a section for a given exam. Aggregates marks →
 * total/percentage/grade, computes class rank, renders the PDF, persists, and fires an event
 * per student for the downstream WhatsApp delivery listener.
 * <p>
 * Re-runnable: the {@code (student, exam)} unique constraint makes regeneration an upsert, so
 * fixing a mark + regenerating produces an updated PDF without duplicating the row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCardService {

    private final ReportCardRepository reportCardRepository;
    private final ExamMarkRepository markRepository;
    private final SubjectRepository subjectRepository;
    private final ExamService examService;
    private final ClassSectionService classSectionService;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolService schoolService;
    private final GradeCalculator gradeCalculator;
    private final ReportCardPdfGenerator pdfGenerator;
    private final ApplicationEventPublisher events;
    private final FileStorageService fileStorage;

    /**
     * Generates or regenerates the full set of report cards for {@code section} at {@code exam}.
     * Runs synchronously inside one transaction — fine for class sizes up to ~50; Slice 6 will
     * switch to async job for the whole-school bulk case.
     */
    @Transactional
    public List<ReportCardResponse> generateForSection(UUID tenantId, UUID examId, UUID sectionId) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        School school = schoolService.getSchoolEntity(tenantId);

        List<StudentEnrollment> roster = enrollmentRepository
            .findBySectionIdAndStatus(section.getId(), EnrollmentStatus.ACTIVE);
        if (roster.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Cannot generate report cards — section has no active students");
        }

        Map<UUID, Subject> subjectsById = subjectRepository.findBySchoolIdOrderByName(tenantId).stream()
            .collect(Collectors.toMap(Subject::getId, s -> s));
        Map<UUID, Student> studentsById = studentRepository
            .findAllById(roster.stream().map(StudentEnrollment::getStudentId).toList())
            .stream().collect(Collectors.toMap(Student::getId, s -> s));

        // Pass 1 — compute totals + percentages for each student (needed for rank)
        record Aggregated(UUID studentId, BigDecimal totalMax, BigDecimal totalObtained,
                          BigDecimal percentage, List<ExamMark> marks) {}

        List<Aggregated> aggregated = new ArrayList<>();
        for (StudentEnrollment enr : roster) {
            List<ExamMark> marks = markRepository.findByExamIdAndStudentId(exam.getId(), enr.getStudentId());
            BigDecimal totalMax = marks.stream()
                .map(ExamMark::getMaxMarks)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalObtained = marks.stream()
                .filter(m -> !m.isAbsent() && m.getObtainedMarks() != null)
                .map(ExamMark::getObtainedMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal percentage = totalMax.compareTo(BigDecimal.ZERO) > 0
                ? totalObtained.multiply(BigDecimal.valueOf(100))
                    .divide(totalMax, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            aggregated.add(new Aggregated(enr.getStudentId(), totalMax, totalObtained, percentage, marks));
        }

        // Pass 2 — rank (dense ranking: ties share a rank, next rank continues sequentially)
        List<Aggregated> byPct = aggregated.stream()
            .sorted(Comparator.comparing(Aggregated::percentage).reversed())
            .toList();
        Map<UUID, Integer> rankByStudent = new java.util.HashMap<>();
        int rank = 0;
        BigDecimal lastPct = null;
        for (int i = 0; i < byPct.size(); i++) {
            Aggregated a = byPct.get(i);
            if (lastPct == null || a.percentage().compareTo(lastPct) != 0) {
                rank = i + 1;
                lastPct = a.percentage();
            }
            rankByStudent.put(a.studentId(), rank);
        }

        // Pass 3 — render + persist + fire events
        List<ReportCardResponse> results = new ArrayList<>();
        for (Aggregated a : aggregated) {
            Student student = studentsById.get(a.studentId());
            if (student == null) continue;

            String grade = gradeCalculator.calculate(a.percentage().doubleValue(), school.getBoard());
            Integer studentRank = rankByStudent.get(a.studentId());

            byte[] pdfBytes = pdfGenerator.generate(
                school, exam, student,
                a.marks(), subjectsById,
                a.totalMax(), a.totalObtained(), a.percentage(),
                grade, studentRank, null
            );

            ReportCard card = reportCardRepository.findByStudentIdAndExamId(a.studentId(), exam.getId())
                .orElseGet(() -> {
                    ReportCard c = new ReportCard();
                    c.setSchoolId(tenantId);
                    c.setStudentId(a.studentId());
                    c.setExamId(exam.getId());
                    return c;
                });
            card.setTotalMarks(a.totalMax());
            card.setObtainedMarks(a.totalObtained());
            card.setPercentage(a.percentage());
            card.setGrade(grade);
            card.setRankInClass(studentRank);
            card = reportCardRepository.save(card);

            String pdfUrl = storePdf(tenantId, card.getId(), pdfBytes);
            card.setPdfUrl(pdfUrl);
            card = reportCardRepository.save(card);

            events.publishEvent(new ReportCardGeneratedEvent(
                tenantId, card.getId(), student.getId(), exam.getId(), exam.getName(), pdfUrl));
            results.add(ReportCardResponse.from(card));
        }

        log.info("Generated {} report cards exam={} section={} tenantId={}",
            results.size(), examId, sectionId, tenantId);
        return results;
    }

    @Transactional(readOnly = true)
    public ReportCardResponse getReportCard(UUID tenantId, UUID studentId, UUID examId) {
        studentService.getStudentEntity(tenantId, studentId);
        ReportCard card = reportCardRepository.findByStudentIdAndExamId(studentId, examId)
            .orElseThrow(() -> new AppException(ErrorCode.REPORT_CARD_NOT_READY,
                "Report card has not been generated yet"));
        return ReportCardResponse.from(card);
    }

    /**
     * Persists via {@link FileStorageService} — LOCAL serves from disk, S3 returns a presigned
     * URL. The resulting URL is embedded in the WhatsApp message sent by
     * {@code ReportCardDeliveryListener}.
     */
    private String storePdf(UUID tenantId, UUID reportCardId, byte[] bytes) {
        String key = "report-cards/" + tenantId + "/" + reportCardId + ".pdf";
        StoredFile stored = fileStorage.store(key, bytes, "application/pdf");
        return stored.url();
    }
}
