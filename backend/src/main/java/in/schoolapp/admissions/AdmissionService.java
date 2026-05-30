package in.schoolapp.admissions;

import in.schoolapp.admissions.dto.AdmissionResponse;
import in.schoolapp.admissions.dto.AdmissionTestScoreDto;
import in.schoolapp.admissions.dto.EnquiryRequest;
import in.schoolapp.admissions.dto.OfferRequest;
import in.schoolapp.admissions.dto.ScheduleTestRequest;
import in.schoolapp.admissions.dto.SubmitApplicationRequest;
import in.schoolapp.admissions.dto.TestResultRequest;
import in.schoolapp.admissions.entity.Admission;
import in.schoolapp.admissions.entity.AdmissionStatus;
import in.schoolapp.admissions.entity.AdmissionTestScore;
import in.schoolapp.admissions.repository.AdmissionRepository;
import in.schoolapp.admissions.repository.AdmissionTestScoreRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.dto.CreateStudentRequest;
import in.schoolapp.student.dto.StudentResponse;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admissions lifecycle. Every state-changing method calls {@link #transition} so the
 * AdmissionStatus.canTransitionTo whitelist is the single source of truth for legal moves.
 *
 * <p>The {@link #enrollStudent} terminal step creates a real {@link in.schoolapp.student.entity.Student}
 * row using {@link StudentService#createStudent} — so the same dedup / parent-link logic
 * that runs for manual student creation applies here too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final AdmissionTestScoreRepository scoreRepository;
    private final SchoolRepository schoolRepository;
    private final StudentService studentService;
    private final ClassSectionService classSectionService;
    private final StudentEnrollmentRepository enrollmentRepository;

    /** Public entrypoint — no tenant context; the school is identified by phone/email/path. */
    @Transactional
    public AdmissionResponse createEnquiry(UUID schoolId, EnquiryRequest req) {
        // Confirm the tenant actually exists (otherwise we'd leak via 201s).
        School school = schoolRepository.findById(schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "School not found"));

        // Duplicate detection (#14): block a second live application for the same child + contact.
        if (req.parentPhone() != null && req.studentFirstName() != null
            && admissionRepository.countActiveDuplicates(school.getId(), req.parentPhone(),
                req.studentFirstName(), List.of(AdmissionStatus.REJECTED, AdmissionStatus.DECLINED)) > 0) {
            throw new AppException(ErrorCode.DUPLICATE_ADMISSION,
                "An application already exists for this child and contact. Continue with the existing one.");
        }

        Admission a = new Admission();
        a.setSchoolId(school.getId());
        a.setStatus(AdmissionStatus.ENQUIRY);
        a.setParentName(req.parentName());
        a.setParentPhone(req.parentPhone());
        a.setParentEmail(req.parentEmail());
        a.setStudentFirstName(req.studentFirstName());
        a.setStudentLastName(req.studentLastName());
        a.setStudentDateOfBirth(req.studentDateOfBirth());
        a.setStudentGender(req.studentGender());
        a.setIntendedClass(req.intendedClass());
        a.setIntendedSection(req.intendedSection());
        a.setIntendedAcademicYear(req.intendedAcademicYear());
        a.setSource(req.source());
        a.setReferrerName(req.referrerName());
        a.setNotes(req.notes());
        a = admissionRepository.save(a);
        log.info("Admission enquiry tenant={} student=\"{}\" class={}",
            schoolId, a.studentDisplayName(), req.intendedClass());
        return toResponse(a);
    }

    @Transactional
    public AdmissionResponse submitApplication(UUID tenantId, UUID admissionId, SubmitApplicationRequest req) {
        Admission a = getOrThrow(tenantId, admissionId);
        // Allow re-submitting an existing enquiry by accepting current status as no-op or transition.
        if (a.getStatus() == AdmissionStatus.ENQUIRY) {
            transition(a, AdmissionStatus.APPLICATION_SUBMITTED);
        } else if (a.getStatus() != AdmissionStatus.APPLICATION_SUBMITTED) {
            throw badTransition(a.getStatus(), AdmissionStatus.APPLICATION_SUBMITTED);
        }
        // Apply patch.
        if (req.parentName() != null)         a.setParentName(req.parentName());
        if (req.parentEmail() != null)        a.setParentEmail(req.parentEmail());
        if (req.studentLastName() != null)    a.setStudentLastName(req.studentLastName());
        if (req.studentDateOfBirth() != null) a.setStudentDateOfBirth(req.studentDateOfBirth());
        if (req.studentGender() != null)      a.setStudentGender(req.studentGender());
        if (req.intendedSection() != null)    a.setIntendedSection(req.intendedSection());
        if (req.notes() != null)              a.setNotes(req.notes());
        return toResponse(admissionRepository.save(a));
    }

    @Transactional
    public AdmissionResponse scheduleTest(UUID tenantId, UUID admissionId, ScheduleTestRequest req) {
        Admission a = getOrThrow(tenantId, admissionId);
        transition(a, AdmissionStatus.TEST_SCHEDULED);
        a.setTestScheduledAt(req.scheduledAt());
        a.setTestVenue(req.venue());
        return toResponse(admissionRepository.save(a));
    }

    @Transactional
    public AdmissionResponse recordTestResult(UUID tenantId, UUID admissionId, TestResultRequest req) {
        Admission a = getOrThrow(tenantId, admissionId);
        if (a.getStatus() != AdmissionStatus.TEST_SCHEDULED
            && a.getStatus() != AdmissionStatus.APPLICATION_SUBMITTED) {
            throw badTransition(a.getStatus(), AdmissionStatus.TEST_COMPLETED);
        }
        transition(a, AdmissionStatus.TEST_COMPLETED);
        a.setTestTotalMarks(req.totalMarks());
        a.setTestObtainedMarks(req.obtainedMarks());
        a.setTestRemarks(req.remarks());
        admissionRepository.save(a);

        // Persist optional per-subject scores.
        if (req.perSubject() != null) {
            for (AdmissionTestScoreDto s : req.perSubject()) {
                AdmissionTestScore row = new AdmissionTestScore();
                row.setAdmissionId(admissionId);
                row.setSchoolId(tenantId);
                row.setSubjectName(s.subjectName());
                row.setMaxMarks(s.maxMarks());
                row.setObtainedMarks(s.obtainedMarks());
                row.setRemarks(s.remarks());
                scoreRepository.save(row);
            }
        }
        return toResponse(a);
    }

    @Transactional
    public AdmissionResponse makeOffer(UUID tenantId, UUID admissionId, OfferRequest req) {
        Admission a = getOrThrow(tenantId, admissionId);
        transition(a, AdmissionStatus.OFFERED);
        a.setOfferLetterUrl(req.offerLetterUrl());
        a.setOfferIssuedAt(OffsetDateTime.now());
        return toResponse(admissionRepository.save(a));
    }

    @Transactional
    public AdmissionResponse acceptOffer(UUID tenantId, UUID admissionId) {
        Admission a = getOrThrow(tenantId, admissionId);
        transition(a, AdmissionStatus.ACCEPTED);
        a.setOfferAcceptedAt(OffsetDateTime.now());
        return toResponse(admissionRepository.save(a));
    }

    @Transactional
    public AdmissionResponse declineOffer(UUID tenantId, UUID admissionId, String reason) {
        Admission a = getOrThrow(tenantId, admissionId);
        transition(a, AdmissionStatus.DECLINED);
        a.setOfferDeclinedAt(OffsetDateTime.now());
        a.setDeclineReason(reason);
        return toResponse(admissionRepository.save(a));
    }

    @Transactional
    public AdmissionResponse reject(UUID tenantId, UUID admissionId, String reason) {
        Admission a = getOrThrow(tenantId, admissionId);
        transition(a, AdmissionStatus.REJECTED);
        a.setDeclineReason(reason);
        return toResponse(admissionRepository.save(a));
    }

    /**
     * Creates the real Student row + Enrollment, links it back to the admission, transitions
     * to ENROLLED. {@code sectionId} must belong to the same school and the intended academic
     * year; the admin picks the actual section since the application's "intended" hint is
     * just a preference.
     */
    @Transactional
    public AdmissionResponse enrollStudent(UUID tenantId, UUID admissionId, UUID sectionId) {
        Admission a = getOrThrow(tenantId, admissionId);

        // Capacity gate (#14): never enroll beyond the section's sanctioned strength.
        Section section = classSectionService.getSectionOrThrow(tenantId, sectionId);
        int max = section.getMaxStrength() != null ? section.getMaxStrength() : Integer.MAX_VALUE;
        long active = enrollmentRepository.countBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE);
        if (active >= max) {
            throw new AppException(ErrorCode.SECTION_FULL,
                "Section is at capacity (" + active + "/" + max + "). Pick another section or raise its limit.");
        }

        transition(a, AdmissionStatus.ENROLLED);

        CreateStudentRequest req = new CreateStudentRequest(
            a.getStudentFirstName(),
            a.getStudentLastName(),
            sectionId,
            a.getParentPhone(),
            a.getParentName(),
            a.getParentEmail(),
            null,                          // parent_relation can be filled in later
            null,                          // admission_number auto-generated
            a.getStudentGender(),
            a.getStudentDateOfBirth(),
            null,
            null
        );
        StudentResponse student = studentService.createStudent(tenantId, req);
        a.setEnrolledStudentId(student.id());
        a.setEnrolledAt(OffsetDateTime.now());
        admissionRepository.save(a);
        log.info("Admission enrolled tenant={} admission={} student={}",
            tenantId, admissionId, student.id());
        return toResponse(a);
    }

    // ---------------- list + read ----------------

    public Page<AdmissionResponse> list(UUID tenantId, AdmissionStatus status, Pageable pageable) {
        Page<Admission> rows = status != null
            ? admissionRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable)
            : admissionRepository.findBySchoolIdOrderByCreatedAtDesc(tenantId, pageable);
        return rows.map(this::toResponse);
    }

    public AdmissionResponse get(UUID tenantId, UUID admissionId) {
        return toResponse(getOrThrow(tenantId, admissionId));
    }

    public List<ClassResponse> intakeOptions(UUID tenantId) {
        return classSectionService.listClasses(tenantId);
    }

    // ---------------- helpers ----------------

    private Admission getOrThrow(UUID tenantId, UUID admissionId) {
        return admissionRepository.findByIdAndSchoolId(admissionId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Admission not found"));
    }

    private void transition(Admission a, AdmissionStatus next) {
        if (!a.getStatus().canTransitionTo(next)) throw badTransition(a.getStatus(), next);
        a.setStatus(next);
    }

    private static AppException badTransition(AdmissionStatus from, AdmissionStatus to) {
        return new AppException(ErrorCode.VALIDATION_ERROR,
            "Illegal admission transition " + from + " → " + to);
    }

    private AdmissionResponse toResponse(Admission a) {
        Optional<List<AdmissionTestScoreDto>> scores = Optional.empty();
        if (a.getStatus() != AdmissionStatus.ENQUIRY && a.getStatus() != AdmissionStatus.APPLICATION_SUBMITTED) {
            scores = Optional.of(scoreRepository.findByAdmissionIdOrderBySubjectName(a.getId())
                .stream().map(AdmissionTestScoreDto::from).toList());
        }
        return AdmissionResponse.from(a, scores.orElse(List.of()));
    }

    /** Sugar for the UI: given a tenant + class name, return the section ids in that class. */
    public List<SectionResponse> sectionsForClass(UUID tenantId, String className) {
        return classSectionService.listClasses(tenantId).stream()
            .filter(c -> c.name().equalsIgnoreCase(className))
            .findFirst()
            .map(ClassResponse::sections)
            .orElse(List.of());
    }
}
