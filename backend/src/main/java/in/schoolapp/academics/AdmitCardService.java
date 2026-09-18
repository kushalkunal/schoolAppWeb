package in.schoolapp.academics;

import in.schoolapp.academics.dto.AdmitCardDashboardResponse;
import in.schoolapp.academics.dto.AdmitCardResponse;
import in.schoolapp.academics.dto.AdmitCardValidationResponse;
import in.schoolapp.academics.dto.ExamEnrollmentSummaryResponse;
import in.schoolapp.academics.dto.ExamEnrollmentSummaryResponse.ClassCount;
import in.schoolapp.academics.entity.AdmitCard;
import in.schoolapp.academics.entity.AdmitCardStatus;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamClass;
import in.schoolapp.academics.entity.ExamScheduleEntry;
import in.schoolapp.academics.entity.FeePolicy;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.AdmitCardRepository;
import in.schoolapp.academics.repository.ExamClassRepository;
import in.schoolapp.academics.repository.ExamRepository;
import in.schoolapp.academics.repository.ExamScheduleRepository;
import in.schoolapp.academics.repository.ExamSubjectConfigRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.documents.DocumentService;
import in.schoolapp.documents.DocumentService.GeneratedDocument;
import in.schoolapp.documents.DocumentType;
import in.schoolapp.fee.FeeInvoiceService;
import in.schoolapp.school.entity.SchoolClass;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmitCardService {

    private final AdmitCardRepository admitCardRepository;
    private final ExamRepository examRepository;
    private final ExamClassRepository examClassRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamSubjectConfigRepository subjectConfigRepository;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SectionRepository sectionRepository;
    private final FeeInvoiceService feeInvoiceService;
    private final DocumentService documentService;
    private final org.springframework.context.ApplicationEventPublisher events;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    // ── Dashboard ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdmitCardDashboardResponse getDashboard(UUID tenantId, UUID examId) {
        requireExam(tenantId, examId);
        long total      = admitCardRepository.countByExamId(examId);
        long generated  = admitCardRepository.countByExamIdAndStatus(examId, AdmitCardStatus.GENERATED);
        long downloaded = admitCardRepository.countByExamIdAndStatus(examId, AdmitCardStatus.DOWNLOADED);
        long blocked    = admitCardRepository.countByExamIdAndStatus(examId, AdmitCardStatus.BLOCKED);
        long pending    = admitCardRepository.countByExamIdAndStatus(examId, AdmitCardStatus.PENDING);
        return new AdmitCardDashboardResponse(total, generated, downloaded, blocked, pending);
    }

    // ── Auto-enrollment preview ──────────────────────────────────────────────

    /** How many active students each participating class contributes (Step 4: auto-enrollment). */
    @Transactional(readOnly = true)
    public ExamEnrollmentSummaryResponse enrollmentSummary(UUID tenantId, UUID examId) {
        Exam exam = requireExam(tenantId, examId);
        List<UUID> classIds = participatingClassIds(exam);
        List<ClassCount> counts = new ArrayList<>();
        int total = 0;
        for (UUID classId : classIds) {
            SchoolClass cls = schoolClassRepository.findById(classId).orElse(null);
            int n = activeEnrollmentsForClass(classId, exam.getAcademicYearId()).size();
            total += n;
            counts.add(new ClassCount(classId, cls != null ? cls.getName() : "—", n));
        }
        return new ExamEnrollmentSummaryResponse(total, counts);
    }

    // ── Pre-flight validation (Step 9) ───────────────────────────────────────

    @Transactional(readOnly = true)
    public AdmitCardValidationResponse validate(UUID tenantId, UUID examId) {
        Exam exam = requireExam(tenantId, examId);
        List<UUID> classIds = participatingClassIds(exam);
        boolean hasClasses = !classIds.isEmpty();
        int activeStudents = resolveEnrollments(exam).size();
        boolean hasSubjects = !subjectConfigRepository.findByExamIdOrderBySubjectIdAscSortOrderAsc(examId).isEmpty();
        boolean hasSchedule = !scheduleRepository.findByExamIdOrderByExamDateAscStartTimeAsc(examId).isEmpty();

        List<String> warnings = new ArrayList<>();
        if (!hasClasses) warnings.add("No participating classes selected.");
        if (activeStudents == 0) warnings.add("No active students found in the participating classes.");
        if (!hasSubjects) warnings.add("Subjects & marks are not configured for this exam.");
        if (!hasSchedule) warnings.add("Examination schedule is not set — admit cards will omit the subject timetable.");

        boolean ready = hasClasses && activeStudents > 0;
        return new AdmitCardValidationResponse(ready, activeStudents, hasClasses, hasSubjects, hasSchedule, warnings);
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdmitCardResponse> listForExam(UUID tenantId, UUID examId, String statusFilter) {
        requireExam(tenantId, examId);

        List<AdmitCard> cards;
        if (statusFilter != null && !statusFilter.isBlank()) {
            AdmitCardStatus status = AdmitCardStatus.valueOf(statusFilter.toUpperCase());
            cards = admitCardRepository.findByExamIdAndStatusOrderByStudentId(examId, status);
        } else {
            cards = admitCardRepository.findByExamIdOrderByStudentId(examId);
        }

        if (cards.isEmpty()) return List.of();

        List<UUID> studentIds = cards.stream().map(AdmitCard::getStudentId).toList();
        Map<UUID, Student> studentsById = studentRepository.findAllById(studentIds)
            .stream().collect(Collectors.toMap(Student::getId, Function.identity()));

        Map<UUID, StudentEnrollment> enrollments = studentIds.stream()
            .flatMap(sid -> enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .limit(1))
            .collect(Collectors.toMap(StudentEnrollment::getStudentId, Function.identity(), (a, b) -> a));

        java.util.Set<UUID> sectionIds = enrollments.values().stream()
            .map(StudentEnrollment::getSectionId).collect(Collectors.toSet());
        Map<UUID, Section> sections = sectionRepository.findAllById(sectionIds)
            .stream().collect(Collectors.toMap(Section::getId, Function.identity()));
        java.util.Set<UUID> classIds = sections.values().stream()
            .map(Section::getClassId).collect(Collectors.toSet());
        Map<UUID, SchoolClass> classes = schoolClassRepository.findAllById(classIds)
            .stream().collect(Collectors.toMap(SchoolClass::getId, Function.identity()));

        return cards.stream().map(card -> {
            Student student = studentsById.get(card.getStudentId());
            StudentEnrollment enrollment = enrollments.get(card.getStudentId());

            String studentName = student != null ? student.displayName() : "—";
            String admissionNumber = student != null ? student.getAdmissionNumber() : null;
            String className = null, sectionName = null;
            Integer rollNumber = null;

            if (enrollment != null) {
                rollNumber = enrollment.getRollNumber();
                Section sec = sections.get(enrollment.getSectionId());
                if (sec != null) {
                    sectionName = sec.getName();
                    SchoolClass cls = classes.get(sec.getClassId());
                    if (cls != null) className = cls.getName();
                }
            }
            return AdmitCardResponse.from(card, studentName, admissionNumber, className, sectionName, rollNumber);
        }).toList();
    }

    // ── Bulk generate ────────────────────────────────────────────────────────

    /**
     * Generate admit cards for all active students across the exam's participating classes
     * (or its legacy section/class scope). Idempotent — already-generated cards are skipped and
     * BLOCKED cards are re-evaluated. The exam's {@link FeePolicy} decides whether dues withhold.
     */
    @Transactional
    public int generateForExam(UUID tenantId, UUID examId) {
        Exam exam = requireExam(tenantId, examId);
        List<StudentEnrollment> enrollments = resolveEnrollments(exam);
        if (enrollments.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "No active students — select participating classes (or a class/section) first.");
        }

        int generated = 0;
        for (StudentEnrollment enrollment : enrollments) {
            try {
                AdmitCardStatus prev = generateOrUpdate(tenantId, exam, enrollment.getStudentId(), false);
                if (prev != AdmitCardStatus.GENERATED && prev != AdmitCardStatus.DOWNLOADED) generated++;
            } catch (Exception ex) {
                log.warn("Admit card generation failed for student {} exam {}: {}", enrollment.getStudentId(), examId, ex.getMessage());
            }
        }
        log.info("Admit card bulk-generate: exam={} generated={}", examId, generated);
        return generated;
    }

    /** Generate / regenerate a single student's admit card. {@code force} honours an OVERRIDE policy. */
    @Transactional
    public AdmitCardResponse generateForStudent(UUID tenantId, UUID examId, UUID studentId, boolean force) {
        Exam exam = requireExam(tenantId, examId);
        generateOrUpdate(tenantId, exam, studentId, force);
        AdmitCard card = admitCardRepository.findByExamIdAndStudentId(examId, studentId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Admit card not found after generation"));
        return buildResponse(card);
    }

    @Transactional
    public void recheckAfterFeePayment(UUID studentId) {
        List<AdmitCard> blocked = admitCardRepository.findBlockedByStudent(studentId);
        if (blocked.isEmpty()) return;
        long outstanding = feeInvoiceService.getOutstanding(studentId);
        if (outstanding > 0) return;
        for (AdmitCard card : blocked) {
            Exam exam = examRepository.findById(card.getExamId()).orElse(null);
            if (exam == null) continue;
            try {
                card.setFeeCleared(true);
                card.setOutstandingPaiseSnapshot(0);
                generatePdfAndUpdateCard(card.getSchoolId(), exam, card, studentId);
            } catch (Exception ex) {
                log.warn("Auto-regenerate after fee clearance failed: student={} exam={}: {}", studentId, exam.getId(), ex.getMessage());
            }
        }
    }

    // ── Download ───────────────────────────────────────────────────────────

    @Transactional
    public AdmitCardResponse markDownloaded(UUID tenantId, UUID admitCardId) {
        AdmitCard card = admitCardRepository.findById(admitCardId)
            .filter(c -> c.getSchoolId().equals(tenantId))
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Admit card not found"));
        if (card.getStatus() == AdmitCardStatus.GENERATED) {
            card.setStatus(AdmitCardStatus.DOWNLOADED);
            admitCardRepository.save(card);
        }
        return buildResponse(card);
    }

    // ── Enrollment resolution ────────────────────────────────────────────────

    /** Participating classes: exam_classes if set, else the legacy single class_id. */
    private List<UUID> participatingClassIds(Exam exam) {
        List<UUID> ids = examClassRepository.findByExamId(exam.getId()).stream()
            .map(ExamClass::getClassId).toList();
        if (!ids.isEmpty()) return ids;
        return exam.getClassId() != null ? List.of(exam.getClassId()) : List.of();
    }

    /** All active enrollments in scope: the exam's section, else every section of every participating class. */
    private List<StudentEnrollment> resolveEnrollments(Exam exam) {
        if (exam.getSectionId() != null) {
            return enrollmentRepository.findBySectionIdAndStatus(exam.getSectionId(), EnrollmentStatus.ACTIVE);
        }
        List<UUID> classIds = participatingClassIds(exam);
        List<StudentEnrollment> all = new ArrayList<>();
        for (UUID classId : classIds) {
            all.addAll(activeEnrollmentsForClass(classId, exam.getAcademicYearId()));
        }
        return all;
    }

    private List<StudentEnrollment> activeEnrollmentsForClass(UUID classId, UUID academicYearId) {
        return sectionRepository.findByClassIdAndAcademicYearIdOrderByName(classId, academicYearId).stream()
            .flatMap(s -> enrollmentRepository.findBySectionIdAndStatus(s.getId(), EnrollmentStatus.ACTIVE).stream())
            .toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** Returns the PREVIOUS status so caller can count net-new generations. */
    private AdmitCardStatus generateOrUpdate(UUID tenantId, Exam exam, UUID studentId, boolean force) {
        Optional<AdmitCard> existing = admitCardRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        AdmitCard card = existing.orElseGet(() -> {
            AdmitCard c = new AdmitCard();
            c.setSchoolId(tenantId);
            c.setExamId(exam.getId());
            c.setStudentId(studentId);
            return c;
        });

        AdmitCardStatus prev = card.getStatus();
        if (prev == AdmitCardStatus.GENERATED || prev == AdmitCardStatus.DOWNLOADED) return prev;

        long outstanding = feeInvoiceService.getOutstanding(studentId);
        card.setOutstandingPaiseSnapshot(outstanding);
        card.setFeeCleared(outstanding == 0);

        FeePolicy policy = exam.getFeePolicy() == null ? FeePolicy.BLOCK : exam.getFeePolicy();
        boolean withhold = outstanding > 0 && switch (policy) {
            case ALLOW -> false;
            case BLOCK -> true;
            case OVERRIDE -> !force; // bulk withholds; a per-student force-issue overrides
        };

        if (withhold) {
            card.setStatus(AdmitCardStatus.BLOCKED);
            admitCardRepository.save(card);
            return prev;
        }

        generatePdfAndUpdateCard(tenantId, exam, card, studentId);
        return prev;
    }

    private void generatePdfAndUpdateCard(UUID tenantId, Exam exam, AdmitCard card, UUID studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        StudentEnrollment enrollment = enrollmentRepository
            .findByStudentIdOrderByCreatedAtDesc(studentId)
            .stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).findFirst().orElse(null);

        Section section = enrollment != null ? sectionRepository.findById(enrollment.getSectionId()).orElse(null) : null;
        SchoolClass schoolClass = section != null ? schoolClassRepository.findById(section.getClassId()).orElse(null) : null;
        var school = schoolRepository.findById(tenantId).orElse(null);

        if (card.getAdmitCardNo() == null) {
            card.setAdmitCardNo("AC-" + exam.getId().toString().substring(0, 8).toUpperCase()
                + "-" + studentId.toString().substring(0, 6).toUpperCase());
        }
        if (card.getSeatNumber() == null && enrollment != null && enrollment.getRollNumber() != null) {
            card.setSeatNumber(String.valueOf(enrollment.getRollNumber()));
        }

        Map<String, Object> model = new java.util.HashMap<>();
        model.put("school", school);
        model.put("exam", exam);
        model.put("student", student);
        model.put("enrollment", buildEnrollmentView(enrollment, schoolClass, section));
        model.put("seatNumber", card.getSeatNumber());
        model.put("admitCardNo", card.getAdmitCardNo());
        model.put("schedule", buildScheduleView(exam.getId()));

        try {
            GeneratedDocument doc = documentService.generate(tenantId, DocumentType.HALL_TICKET, model);
            card.setPdfUrl(doc.url());
            card.setStatus(AdmitCardStatus.GENERATED);
            card.setGeneratedAt(OffsetDateTime.now());
        } catch (Exception ex) {
            log.error("PDF generation failed for admit card student={} exam={}: {}", studentId, exam.getId(), ex.getMessage());
            throw ex;
        }
        admitCardRepository.save(card);

        // Notify the parent their admit card is ready (carries the exam schedule on the PDF).
        events.publishEvent(new in.schoolapp.academics.event.AdmitCardReadyEvent(
            tenantId, studentId, exam.getName(), card.getPdfUrl()));
    }

    /** Rows for the hall-ticket schedule table: {date, subject, time, duration}. */
    private List<Map<String, String>> buildScheduleView(UUID examId) {
        List<ExamScheduleEntry> entries = scheduleRepository.findByExamIdOrderByExamDateAscStartTimeAsc(examId);
        if (entries.isEmpty()) return List.of();
        Map<UUID, String> names = subjectRepository.findAllById(
                entries.stream().map(ExamScheduleEntry::getSubjectId).toList()).stream()
            .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
        return entries.stream().map(e -> {
            String time = "—";
            String duration = "—";
            if (e.getStartTime() != null) {
                time = e.getEndTime() != null
                    ? e.getStartTime().format(T) + " – " + e.getEndTime().format(T)
                    : e.getStartTime().format(T);
                if (e.getEndTime() != null) {
                    long mins = Duration.between(e.getStartTime(), e.getEndTime()).toMinutes();
                    duration = (mins / 60) + "h" + (mins % 60 == 0 ? "" : " " + (mins % 60) + "m");
                }
            }
            return Map.of(
                "date", e.getExamDate() != null ? e.getExamDate().format(D) : "—",
                "subject", names.getOrDefault(e.getSubjectId(), "—"),
                "time", time,
                "duration", duration);
        }).toList();
    }

    private record EnrollmentView(String className, String sectionName, Integer rollNumber) {}

    private EnrollmentView buildEnrollmentView(StudentEnrollment enrollment, SchoolClass cls, Section sec) {
        if (enrollment == null) return new EnrollmentView(null, null, null);
        return new EnrollmentView(
            cls != null ? cls.getName() : null,
            sec != null ? sec.getName() : null,
            enrollment.getRollNumber());
    }

    private AdmitCardResponse buildResponse(AdmitCard card) {
        Student student = studentRepository.findById(card.getStudentId()).orElse(null);
        StudentEnrollment enrollment = enrollmentRepository
            .findByStudentIdOrderByCreatedAtDesc(card.getStudentId())
            .stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).findFirst().orElse(null);
        Section sec = enrollment != null ? sectionRepository.findById(enrollment.getSectionId()).orElse(null) : null;
        SchoolClass cls = sec != null ? schoolClassRepository.findById(sec.getClassId()).orElse(null) : null;
        return AdmitCardResponse.from(
            card,
            student != null ? student.displayName() : "—",
            student != null ? student.getAdmissionNumber() : null,
            cls != null ? cls.getName() : null,
            sec != null ? sec.getName() : null,
            enrollment != null ? enrollment.getRollNumber() : null);
    }

    private Exam requireExam(UUID tenantId, UUID examId) {
        return examRepository.findByIdAndSchoolId(examId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Exam not found"));
    }
}
