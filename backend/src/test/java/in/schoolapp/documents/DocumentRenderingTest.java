package in.schoolapp.documents;

import in.schoolapp.branding.BrandingService;
import in.schoolapp.documents.repository.DocumentTemplateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the document-polish slice renders end-to-end through the real Thymeleaf templates:
 * every document carries a scannable QR (data-URI), a signed verification URL, the school's
 * branding (logo/colour) and the configured signature block — and the report card surfaces
 * the attendance + per-subject + remarks data. Also round-trips the verification token.
 */
class DocumentRenderingTest {

    private final UUID school = UUID.randomUUID();

    private DocumentService service() {
        DocumentTemplateRepository repo = mock(DocumentTemplateRepository.class);
        when(repo.findBySchoolIdAndDocumentType(any(), any())).thenReturn(Optional.empty());

        // Mirror production brandingModel(), which always emits every key (blank when unset) so
        // template guards like `branding.websiteUrl != null` never hit an absent-key SpEL error.
        Map<String, Object> brandingModel = new java.util.HashMap<>();
        brandingModel.put("schoolName", "Vidya Mandir School");
        brandingModel.put("shortName", "VMS");
        brandingModel.put("tagline", "Knowledge is power");
        brandingModel.put("affiliation", "Affiliated to CBSE");
        brandingModel.put("logoUrl", "https://cdn.example.com/logo.png");
        brandingModel.put("primaryColor", "#0a7c4a");
        brandingModel.put("accentColor", "#f59e0b");
        brandingModel.put("contactPhone", "020-12345678");
        brandingModel.put("contactEmail", "office@vms.school");
        brandingModel.put("address", "MG Road, Pune");
        brandingModel.put("websiteUrl", "https://vms.school");
        brandingModel.put("gstin", "29ABCDE1234F1Z5");
        brandingModel.put("signatureUrl", "https://cdn.example.com/principal-sign.png");
        brandingModel.put("signatoryName", "Dr. A. Mehta");
        brandingModel.put("signatoryTitle", "Principal");

        BrandingService branding = mock(BrandingService.class);
        when(branding.brandingModel(any())).thenReturn(brandingModel);

        return new DocumentService(repo, null, null, null, branding,
            new QrCodeGenerator(),
            new DocumentVerificationService("test-secret-test-secret-test-secret-123", "http://localhost:8081"));
    }

    private Map<String, Object> student() {
        return Map.of("displayName", "Riya Sharma", "admissionNumber", "ADM-2025-0042");
    }

    private Map<String, Object> school(String extraKey, Object extraVal) {
        return Map.of("name", "Vidya Mandir School", "city", "Pune", "state", "MH",
            "phone", "020-12345678", "email", "office@vms.school", extraKey, extraVal);
    }

    @Test
    void receiptCarriesQrSignatureAndBranding() {
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("school", school("board", "CBSE"));
        model.put("student", student());
        model.put("receiptNumber", "RC-001");
        model.put("receiptDate", LocalDate.now());
        model.put("lineItems", List.of(Map.of("label", "Tuition", "amount", new BigDecimal("12000"))));
        model.put("discountAmount", BigDecimal.ZERO);
        model.put("totalPaid", new BigDecimal("12000"));
        model.put("amountInWords", "Twelve Thousand Rupees Only");
        model.put("paymentMode", "UPI");

        String html = service().renderHtml(school, DocumentType.RECEIPT, model);

        assertThat(html).contains("data:image/png;base64,");                       // QR rendered
        assertThat(html).contains("Scan to verify");
        assertThat(html).contains("Dr. A. Mehta");                                  // signature name
        assertThat(html).contains("https://cdn.example.com/principal-sign.png");    // signature image
        assertThat(html).contains("Vidya Mandir School");                           // branding
        assertThat(html).contains("#0a7c4a");                                       // brand colour
    }

    @Test
    void hallTicketCarriesQrAndSignature() {
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("school", school("board", "CBSE"));
        model.put("student", student());
        model.put("exam", Map.of("name", "Term 1", "examType", "TERM",
            "startDate", LocalDate.now(), "endDate", LocalDate.now().plusDays(7)));
        model.put("seatNumber", "A-12");
        model.put("admitCardNo", "HT-7788");

        String html = service().renderHtml(school, DocumentType.HALL_TICKET, model);

        assertThat(html).contains("data:image/png;base64,");
        assertThat(html).contains("Dr. A. Mehta");
        assertThat(html).contains("Vidya Mandir School");
    }

    @Test
    void reportCardCarriesAttendanceSubjectsRemarksAndQr() {
        Map<String, Object> subjectRow = new java.util.HashMap<>();
        subjectRow.put("subjectName", "Mathematics");
        subjectRow.put("subjectCode", "MATH");
        subjectRow.put("maxMarks", new BigDecimal("100"));
        subjectRow.put("obtainedMarks", new BigDecimal("88"));
        subjectRow.put("absent", false);
        subjectRow.put("percentage", new BigDecimal("88.0"));
        subjectRow.put("grade", "A1");
        subjectRow.put("remarks", "Excellent");

        Map<String, Object> attendance = new java.util.LinkedHashMap<>();
        attendance.put("present", 180L);
        attendance.put("absent", 12L);
        attendance.put("marked", 192L);
        attendance.put("percentage", new BigDecimal("93.8"));

        Map<String, Object> model = new java.util.HashMap<>();
        model.put("school", school("board", "CBSE"));
        model.put("student", student());
        model.put("exam", Map.of("name", "Annual Exam", "examType", "TERM"));
        model.put("academicYearName", "2025-2026");
        model.put("subjectRows", List.of(subjectRow));
        model.put("attendance", attendance);
        model.put("totalMaxMarks", new BigDecimal("100"));
        model.put("totalObtainedMarks", new BigDecimal("88"));
        model.put("overallPercentage", new BigDecimal("88.0"));
        model.put("overallGrade", "A1");
        model.put("rankInClass", 3);
        model.put("classTeacherRemarks", "A diligent and curious student.");
        model.put("documentRef", "ADM-2025-0042");
        model.put("issueDate", LocalDate.now());

        String html = service().renderHtml(school, DocumentType.REPORT_CARD, model);

        assertThat(html).contains("Mathematics");                 // subject row populated
        assertThat(html).contains("Attendance %");                // attendance block
        assertThat(html).contains("93.8%");
        assertThat(html).contains("A diligent and curious student."); // remarks populated
        assertThat(html).contains("data:image/png;base64,");       // QR
        assertThat(html).contains("Dr. A. Mehta");                 // signature
    }

    @Test
    void verificationTokenRoundTrips() {
        DocumentVerificationService v =
            new DocumentVerificationService("test-secret-test-secret-test-secret-123", "http://localhost:8081");
        String token = v.token(school, DocumentType.RECEIPT, "RC-001");

        var ok = v.verify(token);
        assertThat(ok.valid()).isTrue();
        assertThat(ok.schoolId()).isEqualTo(school);
        assertThat(ok.type()).isEqualTo(DocumentType.RECEIPT);
        assertThat(ok.reference()).isEqualTo("RC-001");

        // Tampered token must fail closed.
        assertThat(v.verify(token.substring(0, token.length() - 2) + "xy").valid()).isFalse();
        assertThat(v.verify("garbage").valid()).isFalse();
    }
}
