package in.schoolapp.export_;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.fee.entity.PaymentMode;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock FeePaymentRepository feePaymentRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock AuditLogger auditLogger;

    @InjectMocks ExportService service;

    // ----------------------------------------------------------------
    // Students
    // ----------------------------------------------------------------

    @Test
    void exportStudents_csv_writesHeaderAndRows() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(studentRepository.findBySchoolIdAndActiveTrue(eq(tenant), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(student("Aarav", "Kumar"), student("Meera, the Pro", "Iyer"))))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportStudents(tenant, ExportFormat.CSV, out);

        String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("Student ID,First Name,Last Name,Admission Number,"
            + "Gender,Date of Birth,Is Active,Created At\r\n");
        // Row with a comma in the first-name should be quoted
        assertThat(csv).contains("\"Meera, the Pro\"");
        assertThat(csv).contains("Aarav,Kumar");
        verify(auditLogger).logAction(eq(tenant), eq("Student"), any(UUID.class),
            eq("DATA_EXPORT"), any());
    }

    @Test
    void exportStudents_xlsx_writesReadableWorkbook() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(studentRepository.findBySchoolIdAndActiveTrue(eq(tenant), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(student("Aarav", "Kumar"))))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportStudents(tenant, ExportFormat.XLSX, out);

        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(200);  // XLSX has ZIP overhead

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Students");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("First Name");
            Row firstData = sheet.getRow(1);
            assertThat(firstData.getCell(1).getStringCellValue()).isEqualTo("Aarav");
            assertThat(firstData.getCell(2).getStringCellValue()).isEqualTo("Kumar");
        }
    }

    @Test
    void exportStudents_paginatesUntilLast() throws Exception {
        UUID tenant = UUID.randomUUID();
        // Two non-empty pages, then empty → 3 calls total
        when(studentRepository.findBySchoolIdAndActiveTrue(eq(tenant), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(student("A", "X")), Pageable.ofSize(500), 1001))
            .thenReturn(new PageImpl<>(List.of(student("B", "Y")), Pageable.ofSize(500), 1001))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportStudents(tenant, ExportFormat.CSV, out);

        String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("A,X").contains("B,Y");
    }

    // ----------------------------------------------------------------
    // Fee payments
    // ----------------------------------------------------------------

    @Test
    void exportFeePayments_csv_includesProviderReferenceColumn() throws Exception {
        UUID tenant = UUID.randomUUID();
        FeePayment p = new FeePayment();
        p.setId(UUID.randomUUID());
        p.setSchoolId(tenant);
        p.setStudentId(UUID.randomUUID());
        p.setAmountPaise(450000L);
        p.setPaymentMode(PaymentMode.ONLINE);
        p.setReceiptNumber("RCPT-2026-00042");
        p.setPaymentDate(LocalDate.of(2026, 4, 22));
        p.setProviderReference("cs_test_xyz");
        when(feePaymentRepository.findBySchoolIdOrderByPaymentDateDesc(eq(tenant), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(p)))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportFeePayments(tenant, ExportFormat.CSV, out);

        String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Provider Reference");
        assertThat(csv).contains("cs_test_xyz");
        assertThat(csv).contains("450000");
        verify(auditLogger).logAction(eq(tenant), eq("FeePayment"), any(UUID.class),
            eq("DATA_EXPORT"), any());
    }

    // ----------------------------------------------------------------
    // Attendance
    // ----------------------------------------------------------------

    @Test
    void exportAttendance_csv_includesDateRangeInAuditMetadata() throws Exception {
        UUID tenant = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 20);

        AttendanceRecord r = new AttendanceRecord();
        r.setId(UUID.randomUUID());
        r.setSchoolId(tenant);
        r.setStudentId(UUID.randomUUID());
        r.setSectionId(UUID.randomUUID());
        r.setDate(LocalDate.of(2026, 4, 15));
        r.setStatus(AttendanceStatus.ABSENT);
        when(attendanceRepository.findBySchoolIdAndDateBetweenOrderByDateAscStudentIdAsc(
            eq(tenant), eq(from), eq(to), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(r)))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportAttendance(tenant, from, to, ExportFormat.CSV, out);

        String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Status").contains("ABSENT").contains("2026-04-15");

        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> captor =
            org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditLogger).logAction(eq(tenant), eq("AttendanceRecord"), any(UUID.class),
            anyString(), captor.capture());
        assertThat(captor.getValue()).containsEntry("from", "2026-04-01")
            .containsEntry("to", "2026-04-20");
    }

    // ----------------------------------------------------------------
    // CSV-specific edge cases
    // ----------------------------------------------------------------

    @Test
    void csv_embeddedQuoteIsDoubledAndWrappedInQuotes() throws Exception {
        UUID tenant = UUID.randomUUID();
        Student s = student("He said \"hi\"", "Quoted");
        when(studentRepository.findBySchoolIdAndActiveTrue(eq(tenant), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(s)))
            .thenReturn(Page.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportStudents(tenant, ExportFormat.CSV, out);

        String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"He said \"\"hi\"\"\"");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static Student student(String first, String last) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setFirstName(first);
        s.setLastName(last);
        s.setActive(true);
        return s;
    }
}
