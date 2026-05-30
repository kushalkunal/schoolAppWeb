package in.schoolapp.academics;

import in.schoolapp.academics.dto.AdmitCardDashboardResponse;
import in.schoolapp.academics.dto.AdmitCardResponse;
import in.schoolapp.academics.entity.AdmitCard;
import in.schoolapp.academics.entity.AdmitCardStatus;
import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.repository.AdmitCardRepository;
import in.schoolapp.academics.repository.ExamRepository;
import in.schoolapp.branding.BrandingService;
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

import java.time.OffsetDateTime;
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
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SectionRepository sectionRepository;
    private final FeeInvoiceService feeInvoiceService;
    private final DocumentService documentService;

    // â”€â”€ Dashboard â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ List â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

        // Bulk-load students + enrollments for display
        List<UUID> studentIds = cards.stream().map(AdmitCard::getStudentId).toList();
        Map<UUID, Student> studentsById = studentRepository.findAllById(studentIds)
            .stream().collect(Collectors.toMap(Student::getId, Function.identity()));

        // Load enrollments per student (latest active)
        Map<UUID, StudentEnrollment> enrollments = studentIds.stream()
            .flatMap(sid -> enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .limit(1))
            .collect(Collectors.toMap(e -> e.getStudentId(), Function.identity(), (a, b) -> a));

        // Collect the unique sectionIds to load only those sections + their classes
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

            String studentName = student != null ? student.displayName() : "â€”";
            String admissionNumber = student != null ? student.getAdmissionNumber() : null;

            String className = null;
            String sectionName = null;
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

    // â”€â”€ Bulk generate â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Generate admit cards for all active students enrolled in the exam's section (or class,
     * if the exam is not section-scoped). Idempotent: already-generated cards are skipped,
     * BLOCKED cards are re-evaluated in case fees have been paid.
     */
    @Transactional
    public int generateForExam(UUID tenantId, UUID examId) {
        Exam exam = requireExam(tenantId, examId);

        // Find the section scope
        if (exam.getSectionId() == null && exam.getClassId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Exam has no class/section scope â€” cannot bulk-generate admit cards");
        }

        List<StudentEnrollment> enrollments;
        if (exam.getSectionId() != null) {
            enrollments = enrollmentRepository.findBySectionIdAndStatus(exam.getSectionId(), EnrollmentStatus.ACTIVE);
        } else {
            // Class-wide exam: collect all sections of this class
            List<Section> sections = sectionRepository.findByClassIdAndAcademicYearIdOrderByName(
                exam.getClassId(), exam.getAcademicYearId());
            enrollments = sections.stream()
                .flatMap(s -> enrollmentRepository.findBySectionIdAndStatus(s.getId(), EnrollmentStatus.ACTIVE).stream())
                .toList();
        }

        int generated = 0;
        for (StudentEnrollment enrollment : enrollments) {
            try {
                AdmitCardStatus prev = generateOrUpdate(tenantId, exam, enrollment.getStudentId());
                if (prev != AdmitCardStatus.GENERATED && prev != AdmitCardStatus.DOWNLOADED) {
                    generated++;
                }
            } catch (Exception ex) {
                log.warn("Admit card generation failed for student {} exam {}: {}", enrollment.getStudentId(), examId, ex.getMessage());
            }
        }
        log.info("Admit card bulk-generate: exam={} generated={}", examId, generated);
        return generated;
    }

    /**
     * Generate / regenerate a single student's admit card.
     */
    @Transactional
    public AdmitCardResponse generateForStudent(UUID tenantId, UUID examId, UUID studentId) {
        Exam exam = requireExam(tenantId, examId);
        generateOrUpdate(tenantId, exam, studentId);
        AdmitCard card = admitCardRepository.findByExamIdAndStudentId(examId, studentId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Admit card not found after generation"));
        return buildResponse(card);
    }

    /**
     * After a fee payment, re-check all BLOCKED cards for this student and regenerate them.
     */
    @Transactional
    public void recheckAfterFeePayment(UUID studentId) {
        List<AdmitCard> blocked = admitCardRepository.findBlockedByStudent(studentId);
        if (blocked.isEmpty()) return;

        long outstanding = feeInvoiceService.getOutstanding(studentId);
        if (outstanding > 0) return; // still has dues

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

    // â”€â”€ Download (marks as DOWNLOADED) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Internal helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Returns the PREVIOUS status so caller can count net-new generations. */
    private AdmitCardStatus generateOrUpdate(UUID tenantId, Exam exam, UUID studentId) {
        Optional<AdmitCard> existing = admitCardRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        AdmitCard card = existing.orElseGet(() -> {
            AdmitCard c = new AdmitCard();
            c.setSchoolId(tenantId);
            c.setExamId(exam.getId());
            c.setStudentId(studentId);
            return c;
        });

        AdmitCardStatus prev = card.getStatus();

        // Skip if already generated/downloaded (idempotent)
        if (prev == AdmitCardStatus.GENERATED || prev == AdmitCardStatus.DOWNLOADED) {
            return prev;
        }

        // Fee clearance check
        long outstanding = feeInvoiceService.getOutstanding(studentId);
        card.setOutstandingPaiseSnapshot(outstanding);
        card.setFeeCleared(outstanding == 0);

        if (outstanding > 0) {
            card.setStatus(AdmitCardStatus.BLOCKED);
            admitCardRepository.save(card);
            return prev;
        }

        generatePdfAndUpdateCard(tenantId, exam, card, studentId);
        return prev;
    }

    private void generatePdfAndUpdateCard(UUID tenantId, Exam exam, AdmitCard card, UUID studentId) {
        // Resolve student + enrollment for seat number assignment
        Student student = studentRepository.findById(studentId).orElse(null);
        StudentEnrollment enrollment = enrollmentRepository
            .findByStudentIdOrderByCreatedAtDesc(studentId)
            .stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).findFirst().orElse(null);

        Section section = enrollment != null ? sectionRepository.findById(enrollment.getSectionId()).orElse(null) : null;
        SchoolClass schoolClass = section != null ? schoolClassRepository.findById(section.getClassId()).orElse(null) : null;
        var school = schoolRepository.findById(tenantId).orElse(null);

        // Assign admit card number and seat
        if (card.getAdmitCardNo() == null) {
            String admitNo = "AC-" + exam.getId().toString().substring(0, 8).toUpperCase()
                + "-" + studentId.toString().substring(0, 6).toUpperCase();
            card.setAdmitCardNo(admitNo);
        }
        if (card.getSeatNumber() == null && enrollment != null && enrollment.getRollNumber() != null) {
            card.setSeatNumber(String.valueOf(enrollment.getRollNumber()));
        }

        // Build Thymeleaf model (mirrors hall_ticket.html variables)
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("school", school);
        model.put("exam", exam);
        model.put("student", student);
        model.put("enrollment", buildEnrollmentView(enrollment, schoolClass, section));
        model.put("seatNumber", card.getSeatNumber());
        model.put("admitCardNo", card.getAdmitCardNo());
        model.put("schedule", List.of()); // can be populated later

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
    }

    private record EnrollmentView(String className, String sectionName, Integer rollNumber) {}

    private EnrollmentView buildEnrollmentView(StudentEnrollment enrollment, SchoolClass cls, Section sec) {
        if (enrollment == null) return new EnrollmentView(null, null, null);
        return new EnrollmentView(
            cls != null ? cls.getName() : null,
            sec != null ? sec.getName() : null,
            enrollment.getRollNumber()
        );
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
            student != null ? student.displayName() : "â€”",
            student != null ? student.getAdmissionNumber() : null,
            cls != null ? cls.getName() : null,
            sec != null ? sec.getName() : null,
            enrollment != null ? enrollment.getRollNumber() : null
        );
    }

    private Exam requireExam(UUID tenantId, UUID examId) {
        return examRepository.findByIdAndSchoolId(examId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Exam not found"));
    }
}

