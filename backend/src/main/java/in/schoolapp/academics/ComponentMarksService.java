package in.schoolapp.academics;

import in.schoolapp.academics.dto.BulkComponentMarksRequest;
import in.schoolapp.academics.dto.ComponentMarkEntryDto;
import in.schoolapp.academics.dto.ComponentMarksSheetResponse;
import in.schoolapp.academics.dto.ComponentMarksSheetResponse.ComponentEntry;
import in.schoolapp.academics.dto.ComponentMarksSheetResponse.StudentRow;
import in.schoolapp.academics.dto.ComponentMarksSheetResponse.SubjectEntry;
import in.schoolapp.academics.dto.ExamCompletionStatusResponse;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamComponentMark;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.ExamSectionSubmission;
import in.schoolapp.academics.entity.ExamSubjectConfig;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.entity.TeacherSubjectAssignment;
import in.schoolapp.academics.repository.ExamSectionSubmissionRepository;
import in.schoolapp.academics.repository.ExamComponentMarkRepository;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ExamSubjectConfigRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.repository.StaffRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Teacher marks entry — component-aware.
 * <p>
 * Each call to {@link #submitBulk} upserts entries in {@code exam_component_marks} and rolls
 * up per-subject totals into {@code exam_marks} so the existing {@link ReportCardService}
 * works without modification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentMarksService {

    private final ExamComponentMarkRepository componentMarkRepository;
    private final ExamMarkRepository markRepository;
    private final ExamSubjectConfigRepository configRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;
    private final ExamSectionSubmissionRepository sectionSubmissionRepository;
    private final StaffRepository staffRepository;
    private final ExamService examService;
    private final ExamStructureService structureService;
    private final ClassSectionService classSectionService;
    private final AcademicYearService academicYearService;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolService schoolService;
    private final GradeCalculator gradeCalculator;
    private final ResultService resultService;

    // ------------------------------------------------------------------
    // Entry sheet
    // ------------------------------------------------------------------

    /**
     * Returns the full marks-entry grid for a section: all students × all configured
     * subject-components. Existing {@code obtained} values are pre-filled.
     */
    @Transactional(readOnly = true)
    public ComponentMarksSheetResponse getEntrySheet(UUID tenantId, UUID examId, UUID sectionId) {
        examService.getExamOrThrow(tenantId, examId);
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);

        List<StudentEnrollment> roster = enrollmentRepository
            .findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE);
        roster.sort(Comparator.comparing(e -> e.getRollNumber() != null ? e.getRollNumber() : Integer.MAX_VALUE));

        Map<UUID, Student> studentsById = studentRepository
            .findAllById(roster.stream().map(StudentEnrollment::getStudentId).toList())
            .stream().collect(Collectors.toMap(Student::getId, s -> s));

        // Configs: subjectId → sorted components
        Map<UUID, List<ExamSubjectConfig>> configsBySubject = structureService.getStructureMap(examId);

        // Filter to only assigned subjects for SUBJECT_TEACHER, or CLASS_TEACHER accessing
        // a section they are not the class teacher of.
        String role = TenantContext.getRole();
        UUID staffId = TenantContext.getStaffId();
        boolean filterByAssignment = false;
        if (AppRoles.SUBJECT_TEACHER.equals(role)) {
            filterByAssignment = true;
        } else if (AppRoles.CLASS_TEACHER.equals(role) && staffId != null
                && !staffId.equals(section.getClassTeacherId())) {
            filterByAssignment = true;
        }
        if (filterByAssignment && staffId != null) {
            UUID academicYearId = academicYearService.getCurrentOrThrow(tenantId).getId();
            Set<UUID> assignedSubjectIds = teacherSubjectAssignmentRepository
                .findBySectionIdAndAcademicYearId(sectionId, academicYearId).stream()
                .filter(a -> a.getStaffId().equals(staffId))
                .map(TeacherSubjectAssignment::getSubjectId)
                .collect(Collectors.toSet());
            configsBySubject = configsBySubject.entrySet().stream()
                .filter(e -> assignedSubjectIds.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        Map<UUID, Subject> subjectsById = subjectRepository
            .findAllById(configsBySubject.keySet()).stream()
            .collect(Collectors.toMap(Subject::getId, s -> s));

        // Existing marks: (configId, studentId) → mark
        List<ExamComponentMark> existing = componentMarkRepository.findByExamIdAndSectionId(examId, sectionId);
        Map<String, ExamComponentMark> markIndex = existing.stream()
            .collect(Collectors.toMap(
                m -> m.getConfigId() + "|" + m.getStudentId(),
                m -> m
            ));

        List<StudentRow> rows = new ArrayList<>();
        for (StudentEnrollment enr : roster) {
            Student student = studentsById.get(enr.getStudentId());
            if (student == null) continue;

            List<SubjectEntry> subjects = configsBySubject.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    UUID subjectId = e.getKey();
                    Subject subject = subjectsById.get(subjectId);
                    List<ComponentEntry> components = e.getValue().stream()
                        .map(cfg -> {
                            ExamComponentMark m = markIndex.get(cfg.getId() + "|" + enr.getStudentId());
                            return new ComponentEntry(
                                cfg.getId(),
                                cfg.getComponentName(),
                                cfg.getMaxMarks(),
                                cfg.getPassingMarks(),
                                m != null ? m.getObtained() : null,
                                m != null && m.isAbsent(),
                                m == null || m.isDraft(),
                                m != null ? m.getRemarks() : null
                            );
                        })
                        .toList();
                    return new SubjectEntry(subjectId,
                        subject != null ? subject.getName() : subjectId.toString(),
                        components);
                })
                .toList();

            String name = student.getFirstName()
                + (student.getLastName() != null ? " " + student.getLastName() : "");
            rows.add(new StudentRow(student.getId(), name, enr.getRollNumber(), subjects));
        }

        // Check section lock
        Optional<ExamSectionSubmission> lockOpt =
            sectionSubmissionRepository.findByExamIdAndSectionId(examId, sectionId);
        boolean locked = lockOpt.isPresent();
        String lockedByName = null;
        Instant lockedAt = null;
        if (locked) {
            lockedAt = lockOpt.get().getSubmittedAt();
            lockedByName = staffRepository.findById(lockOpt.get().getSubmittedBy())
                .map(s -> s.getFirstName() + (s.getLastName() != null ? " " + s.getLastName() : ""))
                .orElse("Unknown");
        }

        boolean isOwnClassTeacher = AppRoles.CLASS_TEACHER.equals(role)
            && staffId != null && staffId.equals(section.getClassTeacherId());

        return new ComponentMarksSheetResponse(examId, sectionId, locked, lockedByName, lockedAt,
            isOwnClassTeacher, rows);
    }

    // ------------------------------------------------------------------
    // Bulk upsert
    // ------------------------------------------------------------------

    /**
     * Saves component marks for a batch of (student, component) pairs.
     * <ul>
     *   <li>Validates {@code obtained ≤ maxMarks}.</li>
     *   <li>Upserts via {@code unique(config_id, student_id)}.</li>
     *   <li>Rolls up per-subject totals into {@code exam_marks}.</li>
     *   <li>If {@code submitFinal=true}: flips all entries to {@code is_draft=false} and
     *       triggers result computation for the section.</li>
     * </ul>
     */
    @Transactional
    public void submitBulk(UUID tenantId, UUID examId, BulkComponentMarksRequest req) {
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        if (exam.isPublished()) {
            throw new AppException(ErrorCode.MARKS_ALREADY_FINALIZED,
                "Cannot modify marks — exam has been published.");
        }
        Section section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());
        String role = TenantContext.getRole();
        UUID staffId = TenantContext.getStaffId();

        // Enforce section lock: only Principal/Admin/Owner can override after class teacher submits
        boolean isPrincipalOrAdmin = AppRoles.PRINCIPAL.equals(role)
            || AppRoles.SCHOOL_OWNER.equals(role)
            || AppRoles.ADMIN.equals(role);
        if (!isPrincipalOrAdmin && sectionSubmissionRepository
                .existsByExamIdAndSectionId(examId, req.sectionId())) {
            throw new AppException(ErrorCode.MARKS_ALREADY_FINALIZED,
                "Marks have been locked by the class teacher. Only the Principal can make changes.");
        }

        // Load configs for validation
        Map<UUID, ExamSubjectConfig> configsById = configRepository
            .findByExamIdOrderBySubjectIdAscSortOrderAsc(examId).stream()
            .collect(Collectors.toMap(ExamSubjectConfig::getId, c -> c));

        for (ComponentMarkEntryDto entry : req.entries()) {
            ExamSubjectConfig cfg = configsById.get(entry.configId());
            if (cfg == null || !cfg.getSchoolId().equals(tenantId)) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Config not found in this tenant: " + entry.configId());
            }
            if (!entry.absent() && entry.obtained() != null
                    && entry.obtained().compareTo(cfg.getMaxMarks()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Obtained marks (" + entry.obtained() + ") exceed max marks ("
                        + cfg.getMaxMarks() + ") for component " + cfg.getComponentName());
            }

            ExamComponentMark mark = componentMarkRepository
                .findByConfigIdAndStudentId(entry.configId(), entry.studentId())
                .orElseGet(() -> {
                    ExamComponentMark m = new ExamComponentMark();
                    m.setSchoolId(tenantId);
                    m.setConfigId(entry.configId());
                    m.setStudentId(entry.studentId());
                    m.setSectionId(req.sectionId());
                    return m;
                });

            mark.setObtained(entry.absent() ? BigDecimal.ZERO : entry.obtained());
            mark.setAbsent(entry.absent());
            mark.setDraft(!req.submitFinal());
            if (entry.remarks() != null) mark.setRemarks(entry.remarks());
            mark.setEnteredBy(staffId);
            componentMarkRepository.save(mark);
        }

        // Roll up to exam_marks per (exam, student, subject)
        rollUpToExamMarks(tenantId, examId, req.sectionId(), configsById,
            req.entries(), req.submitFinal());

        if (req.submitFinal()) {
            resultService.computeForSection(tenantId, examId, req.sectionId());

            // Lock the section when the class teacher for this section submits final.
            // Principal/Admin override is also final but does not re-lock (or updates the lock).
            if (AppRoles.CLASS_TEACHER.equals(role) && staffId != null
                    && staffId.equals(section.getClassTeacherId())) {
                ExamSectionSubmission lock = sectionSubmissionRepository
                    .findByExamIdAndSectionId(examId, req.sectionId())
                    .orElseGet(() -> {
                        ExamSectionSubmission l = new ExamSectionSubmission();
                        l.setSchoolId(tenantId);
                        l.setExamId(examId);
                        l.setSectionId(req.sectionId());
                        return l;
                    });
                lock.setSubmittedBy(staffId);
                lock.setSubmittedAt(Instant.now());
                sectionSubmissionRepository.save(lock);
            }
        }
    }

    // ------------------------------------------------------------------
    // Roll-up helper
    // ------------------------------------------------------------------

    /**
     * Aggregates component marks into {@code exam_marks} rows (one per subject per student).
     * Keeps the existing {@link ReportCardService} compatible without changes.
     */
    private void rollUpToExamMarks(UUID tenantId, UUID examId, UUID sectionId,
                                   Map<UUID, ExamSubjectConfig> configsById,
                                   List<ComponentMarkEntryDto> entries,
                                   boolean submitFinal) {
        var board = schoolService.getSchoolEntity(tenantId).getBoard();

        // Group entries by (studentId, subjectId)
        Map<String, List<ComponentMarkEntryDto>> grouped = entries.stream()
            .collect(Collectors.groupingBy(e -> {
                UUID subjectId = configsById.get(e.configId()).getSubjectId();
                return e.studentId() + "|" + subjectId;
            }));

        for (Map.Entry<String, List<ComponentMarkEntryDto>> g : grouped.entrySet()) {
            String[] parts = g.getKey().split("\\|");
            UUID studentId = UUID.fromString(parts[0]);
            UUID subjectId = UUID.fromString(parts[1]);

            List<ComponentMarkEntryDto> comps = g.getValue();
            boolean allAbsent = comps.stream().allMatch(ComponentMarkEntryDto::absent);
            BigDecimal totalMax = comps.stream()
                .map(e -> configsById.get(e.configId()).getMaxMarks())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalObtained = allAbsent ? BigDecimal.ZERO
                : comps.stream()
                    .filter(e -> !e.absent() && e.obtained() != null)
                    .map(ComponentMarkEntryDto::obtained)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Double pct = allAbsent ? null
                : (totalMax.compareTo(BigDecimal.ZERO) > 0
                    ? totalObtained.doubleValue() / totalMax.doubleValue() * 100.0
                    : null);
            String grade = gradeCalculator.calculate(pct, board);

            ExamMark mark = markRepository.findByExamIdAndStudentIdAndSubjectId(examId, studentId, subjectId)
                .orElseGet(() -> {
                    ExamMark m = new ExamMark();
                    m.setSchoolId(tenantId);
                    m.setExamId(examId);
                    m.setStudentId(studentId);
                    m.setSubjectId(subjectId);
                    m.setSectionId(sectionId);
                    m.setEnteredById(TenantContext.getStaffId());
                    return m;
                });

            mark.setMaxMarks(totalMax);
            mark.setObtainedMarks(totalObtained);
            mark.setAbsent(allAbsent);
            mark.setGrade(grade);
            mark.setDraft(!submitFinal);
            markRepository.save(mark);
        }
    }
}
