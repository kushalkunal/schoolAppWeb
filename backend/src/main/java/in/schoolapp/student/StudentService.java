package in.schoolapp.student;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.billing.usage.UsageMetric;
import in.schoolapp.billing.usage.UsageService;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.dto.CreateStudentRequest;
import in.schoolapp.student.dto.StudentResponse;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.ParentRelation;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.event.StudentEnrolledEvent;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentParentLinkRepository linkRepository;
    private final ParentService parentService;
    private final ClassSectionService classSectionService;
    private final AcademicYearService academicYearService;
    private final AuditLogger auditLogger;
    private final in.schoolapp.storage.FileStorageService fileStorageService;
    private final in.schoolapp.student.repository.StudentDocumentRepository documentRepository;
    /**
     * Slice 4 — usage metering. ObjectProvider so slice tests that exclude the billing
     * module aren't forced to bring it in.
     */
    private final ObjectProvider<UsageService> usageServiceProvider;
    private final ApplicationEventPublisher events;

    /**
     * 3-field create per LLD §5.1. Creates (or reuses) the Parent by phone — existing Parent
     * means the new student becomes a sibling of the existing parent's other children.
     */
    @Transactional
    public StudentResponse createStudent(UUID tenantId, CreateStudentRequest req) {
        // Pre-flight plan-limit check — blocks creation on FREE-plan over-the-cap schools.
        // No-op when UsageService isn't on the classpath (slice tests).
        UsageService usage = usageServiceProvider.getIfAvailable();
        if (usage != null) usage.enforceLimit(tenantId, UsageMetric.STUDENTS_COUNT);

        Section section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        if (!section.getAcademicYearId().equals(year.getId())) {
            throw new AppException(ErrorCode.SECTION_NOT_FOUND,
                "Section does not belong to the current academic year");
        }

        if (req.admissionNumber() != null && !req.admissionNumber().isBlank()
                && studentRepository.existsBySchoolIdAndAdmissionNumber(tenantId, req.admissionNumber().trim())) {
            throw new AppException(ErrorCode.DUPLICATE_ADMISSION_NUMBER,
                "Admission number already in use: " + req.admissionNumber());
        }

        // Parent (sibling detection happens inside findOrCreate by phone lookup)
        Parent parent = parentService.findOrCreate(
            tenantId, req.parentPhone(), req.parentName(), req.parentEmail());

        // Student
        Student student = new Student();
        student.setSchoolId(tenantId);
        student.setFirstName(req.firstName().trim());
        student.setLastName(blankToNull(req.lastName()));
        student.setAdmissionNumber(blankToNull(req.admissionNumber()));
        student.setGender(blankToNull(req.gender()));
        student.setDateOfBirth(req.dateOfBirth());
        student.setBloodGroup(blankToNull(req.bloodGroup()));
        student.setAddress(blankToNull(req.address()));
        student.setActive(true);
        student = studentRepository.save(student);

        // Link student → parent as primary
        StudentParentLink link = new StudentParentLink();
        link.setStudentId(student.getId());
        link.setParentId(parent.getId());
        link.setRelation(req.parentRelation() != null ? req.parentRelation() : ParentRelation.GUARDIAN);
        link.setPrimary(true);
        linkRepository.save(link);

        // Enrollment for current year
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setSchoolId(tenantId);
        enrollment.setAcademicYearId(year.getId());
        enrollment.setSectionId(section.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollmentRepository.save(enrollment);

        // Slice 31d — let the fee-structure auto-invoice listener pick this up. The listener
        // is a no-op if the tenant has no ACTIVE structure for the year.
        events.publishEvent(new StudentEnrolledEvent(
            tenantId, student.getId(), section.getId(), year.getId(), section.getClassId()));

        log.info("Created student id={} tenantId={} section={} parentId={}",
            student.getId(), tenantId, section.getId(), parent.getId());
        auditLogger.logCreate(tenantId, "Student", student.getId(), java.util.Map.of(
            "firstName", student.getFirstName(),
            "lastName", student.getLastName() == null ? "" : student.getLastName(),
            "sectionId", section.getId(),
            "parentId", parent.getId()
        ));
        // Slice 4 — bump the per-tenant STUDENTS_COUNT counter so the next enforceLimit call
        // sees the fresh value. Safe under concurrent creates because UsageService.increment
        // uses Postgres ON CONFLICT DO UPDATE.
        if (usage != null) usage.increment(tenantId, UsageMetric.STUDENTS_COUNT, 1);
        return StudentResponse.from(student);
    }

    /**
     * Creates a Student + Parent + enrollment from a migrated paper admission form. Differs
     * from {@link #createStudent} only in that it's called by the OCR migration commit flow
     * and tolerates more missing fields — many handwritten forms only have name + class.
     * Parent phone is still required (sibling detection needs it); if the form didn't contain
     * one, the reviewer must supply it before committing.
     */
    @Transactional
    public StudentResponse createHistoricalStudent(
        UUID tenantId, String firstName, String lastName, UUID sectionId,
        String parentPhone, String parentName, LocalDate dateOfBirth,
        String gender, String notes
    ) {
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);

        Parent parent = parentService.findOrCreate(tenantId, parentPhone, parentName, null);

        Student student = new Student();
        student.setSchoolId(tenantId);
        student.setFirstName(firstName.trim());
        student.setLastName(blankToNull(lastName));
        student.setGender(blankToNull(gender));
        student.setDateOfBirth(dateOfBirth);
        student.setActive(true);
        student = studentRepository.save(student);

        StudentParentLink link = new StudentParentLink();
        link.setStudentId(student.getId());
        link.setParentId(parent.getId());
        link.setRelation(ParentRelation.GUARDIAN);
        link.setPrimary(true);
        linkRepository.save(link);

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setSchoolId(tenantId);
        enrollment.setAcademicYearId(year.getId());
        enrollment.setSectionId(section.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollmentRepository.save(enrollment);

        events.publishEvent(new StudentEnrolledEvent(
            tenantId, student.getId(), section.getId(), year.getId(), section.getClassId()));

        log.info("Migrated historical student id={} tenantId={} section={} notes={}",
            student.getId(), tenantId, section.getId(), notes);
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> listStudents(UUID tenantId, String search, int page, int size) {
        int pageSize = clamp(size, 1, MAX_PAGE_SIZE);
        int pageNum = Math.max(page, 0);
        var sortedPageable = PageRequest.of(pageNum, pageSize, Sort.by("firstName"));
        // Native search query already orders by first_name in SQL; use unsorted Pageable to avoid
        // duplicate ORDER BY clause that would fail with the Java field name "firstName" (DB: first_name).
        var unsortedPageable = PageRequest.of(pageNum, pageSize);

        Page<Student> students = (search == null || search.isBlank())
            ? studentRepository.findBySchoolIdAndActiveTrue(tenantId, sortedPageable)
            : studentRepository.searchBySchoolId(tenantId, search.trim(), unsortedPageable);

        return students.map(StudentResponse::from);
    }

    /**
     * Slice 36 — active roster for a section. Joins student_enrollments → students and filters
     * by tenant for safety. Used by the attendance screen to seed a blank grid.
     */
    @Transactional(readOnly = true)
    public java.util.List<StudentResponse> listBySection(UUID tenantId, UUID sectionId) {
        var enrollments = enrollmentRepository.findBySectionIdAndStatus(
            sectionId, in.schoolapp.student.entity.EnrollmentStatus.ACTIVE);
        if (enrollments.isEmpty()) return java.util.List.of();
        var ids = enrollments.stream().map(e -> e.getStudentId()).toList();
        return studentRepository.findAllById(ids).stream()
            .filter(s -> tenantId.equals(s.getSchoolId()) && s.isActive())
            .sorted((a, b) -> a.getFirstName().compareToIgnoreCase(b.getFirstName()))
            .map(StudentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public Student getStudentEntity(UUID tenantId, UUID studentId) {
        return studentRepository.findByIdAndSchoolId(studentId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.STUDENT_NOT_FOUND, "Student", studentId));
    }

    @Transactional
    public void deactivateStudent(UUID tenantId, UUID studentId) {
        Student s = getStudentEntity(tenantId, studentId);
        s.setActive(false);
        studentRepository.save(s);
        auditLogger.logDelete(tenantId, "Student", studentId, java.util.Map.of(
            "displayName", s.displayName()
        ));
    }

    /**
     * Patch-style update — only non-null fields in the request are applied. Does NOT touch
     * enrollment or parent links; those have their own endpoints.
     */
    @Transactional
    public in.schoolapp.student.dto.StudentResponse updateStudent(
        UUID tenantId, UUID studentId, in.schoolapp.student.dto.UpdateStudentRequest req
    ) {
        Student s = getStudentEntity(tenantId, studentId);
        java.util.Map<String, Object> oldValues = new java.util.HashMap<>();
        java.util.Map<String, Object> newValues = new java.util.HashMap<>();

        applyIfChanged("firstName", s.getFirstName(), trimOrNull(req.firstName()),
            s::setFirstName, oldValues, newValues);
        applyIfChanged("lastName", s.getLastName(), trimOrNull(req.lastName()),
            s::setLastName, oldValues, newValues);
        applyIfChanged("gender", s.getGender(), trimOrNull(req.gender()),
            s::setGender, oldValues, newValues);
        applyIfChanged("dateOfBirth", s.getDateOfBirth(), req.dateOfBirth(),
            s::setDateOfBirth, oldValues, newValues);
        applyIfChanged("bloodGroup", s.getBloodGroup(), trimOrNull(req.bloodGroup()),
            s::setBloodGroup, oldValues, newValues);
        applyIfChanged("address", s.getAddress(), trimOrNull(req.address()),
            s::setAddress, oldValues, newValues);

        if (!newValues.isEmpty()) {
            studentRepository.save(s);
            auditLogger.logUpdate(tenantId, "Student", studentId, oldValues, newValues);
        }
        return in.schoolapp.student.dto.StudentResponse.from(s);
    }

    /** Replaces the student's photo. Stored at a deterministic key so later uploads overwrite. */
    @Transactional
    public in.schoolapp.student.dto.StudentResponse uploadPhoto(
        UUID tenantId, UUID studentId, byte[] bytes, String contentType
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Photo is empty");
        }
        Student s = getStudentEntity(tenantId, studentId);
        String ext = photoExtension(contentType);
        String key = "students/" + tenantId + "/" + studentId + "/photo" + ext;
        var stored = fileStorageService.store(key, bytes,
            contentType == null ? "application/octet-stream" : contentType);
        String oldUrl = s.getPhotoUrl();
        s.setPhotoUrl(stored.url());
        studentRepository.save(s);
        auditLogger.logUpdate(tenantId, "Student", studentId,
            java.util.Map.of("photoUrl", oldUrl == null ? "null" : oldUrl),
            java.util.Map.of("photoUrl", stored.url(), "sizeBytes", bytes.length));
        return in.schoolapp.student.dto.StudentResponse.from(s);
    }

    /** Uploads a typed document against a student. File is stored by the storage backend; a
     *  metadata row lands in {@code student_documents}. */
    @Transactional
    public in.schoolapp.student.dto.StudentDocumentResponse uploadDocument(
        UUID tenantId, UUID studentId,
        in.schoolapp.student.entity.StudentDocumentType docType,
        byte[] bytes, String contentType, String originalFilename
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Document is empty");
        }
        getStudentEntity(tenantId, studentId);  // tenant boundary check
        String docId = UUID.randomUUID().toString();
        String safeName = sanitizeFilename(originalFilename);
        String key = "student-docs/" + tenantId + "/" + studentId + "/"
            + docType.name().toLowerCase() + "/" + docId + "-" + safeName;
        var stored = fileStorageService.store(key, bytes,
            contentType == null ? "application/octet-stream" : contentType);

        in.schoolapp.student.entity.StudentDocument d = new in.schoolapp.student.entity.StudentDocument();
        d.setSchoolId(tenantId);
        d.setStudentId(studentId);
        d.setDocType(docType);
        d.setStorageKey(key);
        d.setFileUrl(stored.url());
        d.setFileName(safeName);
        d.setContentType(contentType);
        d.setSizeBytes((long) bytes.length);
        d.setUploadedById(in.schoolapp.common.TenantContext.getStaffId());
        d = documentRepository.save(d);
        auditLogger.logCreate(tenantId, "StudentDocument", d.getId(), java.util.Map.of(
            "studentId", studentId,
            "docType", docType.name(),
            "sizeBytes", bytes.length
        ));
        return in.schoolapp.student.dto.StudentDocumentResponse.from(d);
    }

    @Transactional(readOnly = true)
    public java.util.List<in.schoolapp.student.dto.StudentDocumentResponse> listDocuments(
        UUID tenantId, UUID studentId
    ) {
        getStudentEntity(tenantId, studentId);  // tenant boundary check
        return documentRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(in.schoolapp.student.dto.StudentDocumentResponse::from)
            .toList();
    }

    @Transactional
    public void deleteDocument(UUID tenantId, UUID documentId) {
        in.schoolapp.student.entity.StudentDocument d = documentRepository
            .findByIdAndSchoolId(documentId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "StudentDocument", documentId));
        fileStorageService.delete(d.getStorageKey());
        documentRepository.delete(d);
        auditLogger.logDelete(tenantId, "StudentDocument", documentId, java.util.Map.of(
            "studentId", d.getStudentId(),
            "docType", d.getDocType() == null ? "null" : d.getDocType().name()
        ));
    }

    private static <T> void applyIfChanged(String field, T current, T incoming,
                                           java.util.function.Consumer<T> setter,
                                           java.util.Map<String, Object> oldValues,
                                           java.util.Map<String, Object> newValues) {
        if (incoming == null) return;
        if (incoming.equals(current)) return;
        oldValues.put(field, current == null ? "null" : current);
        newValues.put(field, incoming);
        setter.accept(incoming);
    }

    private static String trimOrNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String photoExtension(String contentType) {
        if (contentType == null) return ".jpg";
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return ".png";
        if (ct.contains("webp")) return ".webp";
        return ".jpg";
    }

    /** Strips path separators + whitespace — protects the storage backend from malicious names. */
    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String cleaned = raw.replaceAll("[\\\\/\\s]", "_").replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.length() > 80) cleaned = cleaned.substring(cleaned.length() - 80);
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v <= 0 ? DEFAULT_PAGE_SIZE : v, max));
    }
}
