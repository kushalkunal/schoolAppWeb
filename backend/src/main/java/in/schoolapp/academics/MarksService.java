package in.schoolapp.academics;

import in.schoolapp.academics.dto.BulkMarksRequest;
import in.schoolapp.academics.dto.ExamCompletionStatusResponse;
import in.schoolapp.academics.dto.ExamCompletionStatusResponse.SubjectCompletion;
import in.schoolapp.academics.dto.MarkEntryDto;
import in.schoolapp.academics.dto.MarkResponse;
import in.schoolapp.academics.dto.MarksEntrySheetResponse;
import in.schoolapp.academics.dto.MarksEntrySheetResponse.StudentRow;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ExamMarkRepository.SubjectCompletionRow;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.Section;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Grid-style marks lifecycle — fetch sheet, save batch. Auto-derives {@code grade} from
 * {@link GradeCalculator} on every upsert so stored rows can never drift out of sync with
 * their percentage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarksService {

    private final ExamMarkRepository markRepository;
    private final ExamService examService;
    private final SubjectRepository subjectRepository;
    private final ClassSectionService classSectionService;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolService schoolService;
    private final GradeCalculator gradeCalculator;
    private final TeacherSubjectAssignmentRepository teacherAssignmentRepository;

    @Transactional
    public List<MarkResponse> submitBulk(UUID tenantId, UUID examId, BulkMarksRequest req) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        if (exam.isPublished()) {
            throw new AppException(ErrorCode.MARKS_ALREADY_FINALIZED,
                "Cannot modify marks — exam has been published. Contact an admin to reopen.");
        }
        Section section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());
        UUID staffId = TenantContext.getStaffId();

        var board = schoolService.getSchoolEntity(tenantId).getBoard();

        // Validate all submitted subjectIds belong to this tenant — prevents cross-tenant
        // subject injection via a JWT bound to a different school.
        List<UUID> submittedSubjectIds = req.entries().stream()
            .map(MarkEntryDto::subjectId).distinct().toList();
        Map<UUID, Subject> subjectsById = subjectRepository.findAllById(submittedSubjectIds).stream()
            .filter(s -> s.getSchoolId().equals(tenantId))
            .collect(Collectors.toMap(Subject::getId, s -> s));
        for (UUID sid : submittedSubjectIds) {
            if (!subjectsById.containsKey(sid)) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Subject not found in this tenant: " + sid);
            }
        }

        // Subject-teacher authorization: a SUBJECT_TEACHER may only enter marks for subjects
        // they are explicitly assigned to teach in this section and academic year.
        // PRINCIPAL, SCHOOL_OWNER, ADMIN, and CLASS_TEACHER are not subject-restricted.
        String requesterRole = TenantContext.getRole();
        if ("SUBJECT_TEACHER".equals(requesterRole)) {
            UUID academicYearId = section.getAcademicYearId();
            for (UUID subjectId : submittedSubjectIds) {
                boolean isAssigned = teacherAssignmentRepository
                    .existsByStaffIdAndSubjectIdAndSectionIdAndAcademicYearId(
                        staffId, subjectId, section.getId(), academicYearId);
                if (!isAssigned) {
                    Subject subject = subjectsById.get(subjectId);
                    throw new AppException(ErrorCode.FORBIDDEN,
                        "You are not assigned to teach " + subject.getName()
                            + " in this section. Only the assigned subject teacher may enter marks.");
                }
            }
        }

        List<MarkResponse> result = new java.util.ArrayList<>(req.entries().size());
        for (MarkEntryDto entry : req.entries()) {
            if (!entry.absent()) {
                if (entry.obtainedMarks() != null
                        && entry.obtainedMarks().compareTo(entry.maxMarks()) > 0) {
                    throw new AppException(ErrorCode.MAX_MARKS_EXCEEDED,
                        "Obtained marks exceed max for student " + entry.studentId());
                }
                if (entry.obtainedMarks() != null
                        && entry.obtainedMarks().compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Negative obtained marks for student " + entry.studentId());
                }
            }

            ExamMark mark = markRepository
                .findByExamIdAndStudentIdAndSubjectId(examId, entry.studentId(), entry.subjectId())
                .orElseGet(() -> {
                    ExamMark m = new ExamMark();
                    m.setSchoolId(tenantId);
                    m.setExamId(examId);
                    m.setStudentId(entry.studentId());
                    m.setSubjectId(entry.subjectId());
                    m.setSectionId(section.getId());
                    return m;
                });
            mark.setMaxMarks(entry.maxMarks());
            mark.setObtainedMarks(entry.absent() ? null : entry.obtainedMarks());
            mark.setAbsent(entry.absent());
            mark.setEnteredById(staffId);
            mark.setDraft(!req.submitFinal());
            mark.setGrade(gradeCalculator.calculate(mark.percentage(), board));
            mark = markRepository.save(mark);
            result.add(MarkResponse.from(mark));
        }

        log.info("Saved {} marks exam={} section={} tenantId={} submitFinal={}",
            result.size(), examId, section.getId(), tenantId, req.submitFinal());
        return result;
    }

    /**
     * Builds the grid the teacher sees: rows = roster, existing marks pre-populating the cells
     * that have already been entered. The teacher only needs to change what's new.
     */
    @Transactional(readOnly = true)
    public MarksEntrySheetResponse getEntrySheet(UUID tenantId, UUID examId, UUID sectionId) {
        examService.getExamOrThrow(tenantId, examId);
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);

        List<StudentEnrollment> enrollments = enrollmentRepository
            .findBySectionIdAndStatus(section.getId(), EnrollmentStatus.ACTIVE);

        Map<UUID, Student> studentsById = studentRepository
            .findAllById(enrollments.stream().map(StudentEnrollment::getStudentId).toList())
            .stream().collect(Collectors.toMap(Student::getId, s -> s));

        List<StudentRow> rows = enrollments.stream()
            .map(e -> {
                Student s = studentsById.get(e.getStudentId());
                return new StudentRow(
                    s.getId(),
                    s.displayName(),
                    s.getAdmissionNumber(),
                    e.getRollNumber()
                );
            })
            .sorted(Comparator.comparing((StudentRow r) -> r.rollNumber() != null ? r.rollNumber() : Integer.MAX_VALUE)
                .thenComparing(StudentRow::displayName))
            .toList();

        List<MarkResponse> existing = markRepository
            .findByExamIdAndSectionIdOrderByStudentId(examId, section.getId()).stream()
            .map(MarkResponse::from)
            .toList();

        return new MarksEntrySheetResponse(examId, section.getId(), rows, existing);
    }

    @Transactional(readOnly = true)
    public ExamCompletionStatusResponse getCompletionStatus(UUID tenantId, UUID examId, UUID sectionId) {
        examService.getExamOrThrow(tenantId, examId);
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);

        long rosterCount = enrollmentRepository.countBySectionIdAndStatus(
            section.getId(), EnrollmentStatus.ACTIVE);
        List<Subject> subjects = subjectRepository.findBySchoolIdOrderByName(tenantId);

        Map<UUID, Long> enteredBySubject = markRepository
            .countBySubjectForExamSection(examId, section.getId()).stream()
            .collect(Collectors.toMap(
                SubjectCompletionRow::getSubjectId,
                SubjectCompletionRow::getEnteredCount));

        List<SubjectCompletion> perSubject = subjects.stream()
            .map(subj -> {
                int entered = enteredBySubject.getOrDefault(subj.getId(), 0L).intValue();
                return new SubjectCompletion(
                    subj.getId(),
                    subj.getName(),
                    entered,
                    (int) rosterCount,
                    entered >= rosterCount
                );
            })
            .toList();

        return new ExamCompletionStatusResponse(examId, section.getId(), (int) rosterCount, perSubject);
    }

    /**
     * Upserts a single ExamMark migrated from a paper mark-sheet. Treats the record as
     * finalised ({@code is_draft=false}) since paper marks don't need further teacher review.
     * Grade is auto-derived the same way as the live path — board-specific.
     * <p>
     * Called by the migration commit flow. Does NOT check {@code exam.isPublished()} — the
     * whole point of migrating historical marks is that they pre-date publish semantics.
     */
    @Transactional
    public ExamMark createHistorical(UUID tenantId, UUID examId, UUID sectionId,
                                     UUID studentId, UUID subjectId,
                                     BigDecimal maxMarks, BigDecimal obtainedMarks, boolean absent) {
        var board = schoolService.getSchoolEntity(tenantId).getBoard();
        ExamMark mark = markRepository
            .findByExamIdAndStudentIdAndSubjectId(examId, studentId, subjectId)
            .orElseGet(() -> {
                ExamMark m = new ExamMark();
                m.setSchoolId(tenantId);
                m.setExamId(examId);
                m.setStudentId(studentId);
                m.setSubjectId(subjectId);
                m.setSectionId(sectionId);
                return m;
            });
        mark.setMaxMarks(maxMarks);
        mark.setObtainedMarks(absent ? null : obtainedMarks);
        mark.setAbsent(absent);
        mark.setDraft(false);
        mark.setGrade(gradeCalculator.calculate(mark.percentage(), board));
        return markRepository.save(mark);
    }
}
