package in.schoolapp.export_;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.fee.repository.FeePaymentRepository;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tenant-scoped streaming exports. XLSX output uses SXSSFWorkbook — only a rolling window of
 * rows is in memory, so a 10k-student school still exports without OOM. CSV output is a naive
 * quoted writer (RFC 4180-compatible for the common cases).
 * <p>
 * The service never materialises the full dataset in a list; it paginates through the
 * repository in {@link #PAGE_SIZE} chunks and hands each row to the writer. The caller
 * (controller) streams the response body directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Kept small enough that a single page comfortably fits in heap but big enough to avoid
     *  round-trip churn. Tuned for a 10k-row export completing in ~5 batches. */
    private static final int PAGE_SIZE = 500;

    /** SXSSF rolling window — rows older than this are flushed to a tmp file. 100 is POI's
     *  recommended default; raising it increases memory for no correctness benefit. */
    private static final int XLSX_ROWS_IN_MEMORY = 100;

    private final StudentRepository studentRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AuditLogger auditLogger;

    // ============================================================
    // Students
    // ============================================================

    @Transactional(readOnly = true)
    public void exportStudents(UUID tenantId, ExportFormat format, OutputStream out) throws IOException {
        List<String> headers = List.of(
            "Student ID", "First Name", "Last Name", "Admission Number",
            "Gender", "Date of Birth", "Is Active", "Created At");

        writeDataset(format, out, "Students", headers, rowSink -> {
            int page = 0;
            while (true) {
                Page<Student> p = studentRepository.findBySchoolIdAndActiveTrue(
                    tenantId, PageRequest.of(page, PAGE_SIZE));
                if (p.isEmpty()) break;
                for (Student s : p.getContent()) {
                    rowSink.accept(List.of(
                        nullSafe(s.getId()),
                        nullSafe(s.getFirstName()),
                        nullSafe(s.getLastName()),
                        nullSafe(s.getAdmissionNumber()),
                        nullSafe(s.getGender()),
                        nullSafe(s.getDateOfBirth()),
                        String.valueOf(s.isActive()),
                        nullSafe(s.getCreatedAt())
                    ));
                }
                if (p.isLast()) break;
                page++;
            }
        });
        auditExport(tenantId, "Student", null);
    }

    // ============================================================
    // Fee payments
    // ============================================================

    @Transactional(readOnly = true)
    public void exportFeePayments(UUID tenantId, ExportFormat format, OutputStream out) throws IOException {
        List<String> headers = List.of(
            "Payment ID", "Student ID", "Invoice ID", "Amount (Paise)", "Payment Mode",
            "Receipt Number", "Payment Date", "Provider Reference", "Is Historical", "Created At");

        writeDataset(format, out, "Fee Payments", headers, rowSink -> {
            int page = 0;
            while (true) {
                Page<FeePayment> p = feePaymentRepository.findBySchoolIdOrderByPaymentDateDesc(
                    tenantId, PageRequest.of(page, PAGE_SIZE));
                if (p.isEmpty()) break;
                for (FeePayment pmt : p.getContent()) {
                    rowSink.accept(List.of(
                        nullSafe(pmt.getId()),
                        nullSafe(pmt.getStudentId()),
                        nullSafe(pmt.getInvoiceId()),
                        String.valueOf(pmt.getAmountPaise()),
                        nullSafe(pmt.getPaymentMode()),
                        nullSafe(pmt.getReceiptNumber()),
                        nullSafe(pmt.getPaymentDate()),
                        nullSafe(pmt.getProviderReference()),
                        String.valueOf(pmt.isHistorical()),
                        nullSafe(pmt.getCreatedAt())
                    ));
                }
                if (p.isLast()) break;
                page++;
            }
        });
        auditExport(tenantId, "FeePayment", null);
    }

    // ============================================================
    // Attendance
    // ============================================================

    @Transactional(readOnly = true)
    public void exportAttendance(UUID tenantId, LocalDate from, LocalDate to,
                                 ExportFormat format, OutputStream out) throws IOException {
        List<String> headers = List.of(
            "Record ID", "Date", "Student ID", "Section ID", "Status",
            "Arrival Time", "Note", "Is Historical", "Synced From Mobile");

        writeDataset(format, out, "Attendance", headers, rowSink -> {
            int page = 0;
            while (true) {
                Page<AttendanceRecord> p = attendanceRepository
                    .findBySchoolIdAndDateBetweenOrderByDateAscStudentIdAsc(
                        tenantId, from, to, PageRequest.of(page, PAGE_SIZE));
                if (p.isEmpty()) break;
                for (AttendanceRecord r : p.getContent()) {
                    rowSink.accept(List.of(
                        nullSafe(r.getId()),
                        nullSafe(r.getDate()),
                        nullSafe(r.getStudentId()),
                        nullSafe(r.getSectionId()),
                        nullSafe(r.getStatus()),
                        nullSafe(r.getArrivalTime()),
                        nullSafe(r.getNote()),
                        String.valueOf(r.isHistorical()),
                        String.valueOf(r.isSyncedFromMobile())
                    ));
                }
                if (p.isLast()) break;
                page++;
            }
        });
        auditExport(tenantId, "AttendanceRecord", Map.of("from", from.toString(), "to", to.toString()));
    }

    // ============================================================
    // Format writers
    // ============================================================

    private void writeDataset(ExportFormat format, OutputStream out, String sheetName,
                              List<String> headers, Consumer<Consumer<List<String>>> producer) throws IOException {
        if (format == ExportFormat.CSV) {
            writeCsv(out, headers, producer);
        } else {
            writeXlsx(out, sheetName, headers, producer);
        }
    }

    private void writeCsv(OutputStream out, List<String> headers,
                          Consumer<Consumer<List<String>>> producer) throws IOException {
        var writer = new java.io.BufferedWriter(
            new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8));
        writer.write(toCsvLine(headers));
        writer.write("\r\n");
        producer.accept(row -> {
            try {
                writer.write(toCsvLine(row));
                writer.write("\r\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        writer.flush();
    }

    private void writeXlsx(OutputStream out, String sheetName, List<String> headers,
                           Consumer<Consumer<List<String>>> producer) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(XLSX_ROWS_IN_MEMORY)) {
            wb.setCompressTempFiles(true);  // keep the tmp footprint small on big exports
            Sheet sheet = wb.createSheet(sheetName);
            int[] rowNum = {0};

            Row header = sheet.createRow(rowNum[0]++);
            for (int c = 0; c < headers.size(); c++) {
                header.createCell(c).setCellValue(headers.get(c));
            }

            producer.accept(values -> {
                Row row = sheet.createRow(rowNum[0]++);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            });

            wb.write(out);
            // try-with-resources close() cleans the rolling-window temp files.
        }
    }

    private static String toCsvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quoteCsv(cells.get(i)));
        }
        return sb.toString();
    }

    /** RFC 4180-ish — wrap in quotes if value contains comma/quote/newline; double embedded quotes. */
    private static String quoteCsv(String v) {
        if (v == null) return "";
        boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
            || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!needsQuotes) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : v.toString();
    }

    private void auditExport(UUID tenantId, String entityType, Map<String, Object> params) {
        Map<String, Object> details = params == null ? Map.of() : params;
        // The export itself is the audit event — no row id to diff against, so log as an action.
        auditLogger.logAction(tenantId, entityType, UUID.randomUUID(), "DATA_EXPORT", details);
    }

    /** RuntimeException wrapper around checked IOException so we can throw from a lambda. */
    private static final class UncheckedIOException extends RuntimeException {
        UncheckedIOException(IOException cause) { super(cause); }
    }
}
