package in.schoolapp.documents;

import in.schoolapp.academics.ExamService;
import in.schoolapp.academics.ReportCardService;
import in.schoolapp.academics.dto.ExamResponse;
import in.schoolapp.academics.dto.ReportCardResponse;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.documents.dto.HallTicketRequest;
import in.schoolapp.documents.dto.IssueBonafideRequest;
import in.schoolapp.documents.dto.IssueTransferCertificateRequest;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.dto.SchoolResponse;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.dto.StudentProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document-generation surface. Each endpoint:
 *
 * <ol>
 *   <li>Checks the relevant feature flag via {@code @RequiresFeature} (off by default).</li>
 *   <li>Gathers the model — school, student, exam, line items.</li>
 *   <li>Asks {@link DocumentService} to render + store the PDF.</li>
 *   <li>Returns either a JSON envelope with the URL, OR the raw PDF bytes if the
 *       caller passes {@code ?inline=true} (handy for "Preview" buttons).</li>
 * </ol>
 *
 * The model variables passed to Thymeleaf are documented next to each method so schools
 * writing custom templates know exactly what's available.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SchoolService schoolService;
    private final FamilyService familyService;
    private final ExamService examService;
    private final ReportCardService reportCardService;

    // ---------------- Transfer Certificate ----------------
    //
    // Model variables: school, student, parentName, enrollment, admissionDate, lastClass,
    // leavingDate, reasonForLeaving, conduct, promoted, feesDue, remarks, tcNumber, issueDate.

    @PostMapping("/students/{studentId}/transfer-certificate")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.TRANSFER_CERTIFICATE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueTransferCertificate(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @Valid @RequestBody IssueTransferCertificateRequest req
    ) {
        SchoolResponse school = schoolService.getSchool(tenantId);
        StudentProfileResponse profile = familyService.getProfile(tenantId, studentId);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("school", school);
        model.put("student", profile.student());
        model.put("enrollment", profile.currentEnrollment());
        model.put("parentName", firstParentName(profile));
        model.put("admissionDate", req.admissionDate());
        model.put("lastClass", profile.currentEnrollment() != null
            ? profile.currentEnrollment().className() + " " + profile.currentEnrollment().sectionName()
            : null);
        model.put("leavingDate", req.leavingDate() != null ? req.leavingDate() : LocalDate.now());
        model.put("reasonForLeaving", req.reasonForLeaving());
        model.put("conduct", req.conduct());
        model.put("promoted", req.promoted());
        model.put("feesDue", req.feesDue() != null ? req.feesDue() : "None");
        model.put("remarks", req.remarks());
        model.put("tcNumber", req.tcNumber() != null ? req.tcNumber() : autoTcNumber(tenantId));
        model.put("issueDate", LocalDate.now());

        var generated = documentService.generate(tenantId, DocumentType.TRANSFER_CERTIFICATE, model);

        Map<String, Object> body = new HashMap<>();
        body.put("url", generated.url());
        body.put("storageKey", generated.storageKey());
        body.put("documentType", DocumentType.TRANSFER_CERTIFICATE.name());
        body.put("issuedAt", OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    // ---------------- Bonafide Certificate ----------------
    //
    // Model variables: school, student, parentName, enrollment, academicYearName, purpose,
    // certificateNumber, issueDate.

    @PostMapping("/students/{studentId}/bonafide")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.BONAFIDE_CERTIFICATE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueBonafide(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @Valid @RequestBody IssueBonafideRequest req
    ) {
        SchoolResponse school = schoolService.getSchool(tenantId);
        StudentProfileResponse profile = familyService.getProfile(tenantId, studentId);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("school", school);
        model.put("student", profile.student());
        model.put("enrollment", profile.currentEnrollment());
        model.put("parentName", firstParentName(profile));
        model.put("academicYearName",
            profile.currentEnrollment() != null ? profile.currentEnrollment().academicYearName() : "—");
        model.put("purpose", req.purpose());
        model.put("certificateNumber", req.certificateNumber() != null ? req.certificateNumber() : autoBonafideNumber(tenantId));
        model.put("issueDate", LocalDate.now());

        var generated = documentService.generate(tenantId, DocumentType.BONAFIDE, model);

        Map<String, Object> body = new HashMap<>();
        body.put("url", generated.url());
        body.put("storageKey", generated.storageKey());
        body.put("documentType", DocumentType.BONAFIDE.name());
        body.put("issuedAt", OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    // ---------------- Hall ticket (single student) ----------------
    //
    // Model variables: school, exam, student, enrollment, seatNumber, schedule.

    @PostMapping("/exams/{examId}/hall-ticket/{studentId}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    @RequiresFeature(FeatureKey.HALL_TICKETS)
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueHallTicket(
        @PathVariable UUID tenantId,
        @PathVariable UUID examId,
        @PathVariable UUID studentId,
        @Valid @RequestBody HallTicketRequest req
    ) {
        SchoolResponse school = schoolService.getSchool(tenantId);
        StudentProfileResponse profile = familyService.getProfile(tenantId, studentId);
        List<ExamResponse> exams = examService.listCurrentYearExams(tenantId);
        ExamResponse exam = exams.stream().filter(e -> e.id().equals(examId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Exam not found in current year"));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("school", school);
        model.put("exam", exam);
        model.put("student", profile.student());
        model.put("enrollment", profile.currentEnrollment());
        model.put("seatNumber", req.seatNumber());
        model.put("schedule", req.schedule() != null ? req.schedule() : List.of());

        var generated = documentService.generate(tenantId, DocumentType.HALL_TICKET, model);

        Map<String, Object> body = new HashMap<>();
        body.put("url", generated.url());
        body.put("storageKey", generated.storageKey());
        body.put("documentType", DocumentType.HALL_TICKET.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    // ---------------- Report card PDF (renders for a single student) ----------------
    //
    // Model variables: school, exam, student, enrollment, academicYearName, subjectRows,
    // totalMaxMarks, totalObtainedMarks, overallPercentage, overallGrade, rankInClass,
    // classTeacherRemarks, issueDate.

    @GetMapping(value = "/students/{studentId}/report-card/{examId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequiresFeature(FeatureKey.PDF_GENERATION)
    public ResponseEntity<byte[]> downloadReportCardPdf(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @PathVariable UUID examId
    ) {
        ReportCardResponse rc = reportCardService.getReportCard(tenantId, studentId, examId);
        SchoolResponse school = schoolService.getSchool(tenantId);
        StudentProfileResponse profile = familyService.getProfile(tenantId, studentId);
        ExamResponse exam = examService.listCurrentYearExams(tenantId).stream()
            .filter(e -> e.id().equals(examId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Exam not found"));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("school", school);
        model.put("exam", exam);
        model.put("student", profile.student());
        model.put("enrollment", profile.currentEnrollment());
        model.put("academicYearName",
            profile.currentEnrollment() != null ? profile.currentEnrollment().academicYearName() : "—");
        // Per-subject rows are deliberately empty by default — Slice 12 lays the pipeline;
        // Slice 21 will surface ReportCardSubjectRow when the entity is extended. For now the
        // aggregate is shown in the summary block of the template.
        model.put("subjectRows", List.of());
        model.put("totalMaxMarks", rc.totalMarks());
        model.put("totalObtainedMarks", rc.obtainedMarks());
        model.put("overallPercentage", rc.percentage());
        model.put("overallGrade", rc.grade());
        model.put("rankInClass", rc.rankInClass());
        model.put("classTeacherRemarks", null);
        model.put("issueDate", LocalDate.now());

        var generated = documentService.generate(tenantId, DocumentType.REPORT_CARD, model);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
            "report-card-" + studentId + "-" + examId + ".pdf");
        return new ResponseEntity<>(generated.bytes(), headers, HttpStatus.OK);
    }

    // ---------------- helpers ----------------

    private static String firstParentName(StudentProfileResponse profile) {
        if (profile.parents() == null || profile.parents().isEmpty()) return null;
        return profile.parents().get(0).name();
    }

    /** Cheap auto-numbering — production should consult a per-tenant sequence column. */
    private static String autoTcNumber(UUID tenantId) {
        return "TC/" + LocalDate.now().getYear() + "/" + (tenantId.hashCode() & 0x7fffffff) % 10000;
    }

    private static String autoBonafideNumber(UUID tenantId) {
        return "BC/" + LocalDate.now().getYear() + "/" + (tenantId.hashCode() & 0x7fffffff) % 10000;
    }
}
