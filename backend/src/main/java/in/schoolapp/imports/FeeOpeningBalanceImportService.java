package in.schoolapp.imports;

import in.schoolapp.fee.FeeInvoiceService;
import in.schoolapp.fee.dto.OpeningBalanceRequest;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bulk fee opening-balance import — carries forward existing dues at go-live. Columns:
 * {@code admission_number} (required), {@code amount} (required, rupees), {@code note} (optional).
 * Each row resolves the student by admission number and creates a single opening-balance invoice
 * (tagged {@code openingBalance=true}). Common when a school joins mid-session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeOpeningBalanceImportService {

    private final StudentRepository studentRepository;
    private final FeeInvoiceService feeInvoiceService;

    @Transactional
    public ImportResult<String> importOpeningBalances(UUID tenantId, InputStream csv, boolean dryRun) {
        ImportResult<String> result = new ImportResult<>(dryRun);
        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            try {
                String admission = CsvImporter.required(rec, "admission_number");
                String amountStr = CsvImporter.required(rec, "amount");
                String note      = CsvImporter.optional(rec, "note");

                long amountPaise;
                try {
                    amountPaise = new BigDecimal(amountStr.replace(",", "").trim())
                        .movePointRight(2).longValueExact();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid amount \"" + amountStr + "\" — use rupees, e.g. 1500 or 1500.00");
                }
                if (amountPaise < 0) throw new IllegalArgumentException("amount cannot be negative");

                Student student = studentRepository.findBySchoolIdAndAdmissionNumber(tenantId, admission)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "No student with admission number \"" + admission + "\""));

                if (!dryRun) {
                    feeInvoiceService.recordOpeningBalances(tenantId, new OpeningBalanceRequest(
                        List.of(new OpeningBalanceRequest.Entry(student.getId(), amountPaise, note))));
                }
                result.recordAccepted(admission + " — ₹" + (amountPaise / 100.0));
            } catch (Exception e) {
                result.recordError(rowNumber, e.getMessage());
            }
        });
        log.info("Opening-balance CSV import tenant={} dryRun={} read={} accepted={} errors={}",
            tenantId, dryRun, result.totalRowsRead(), result.acceptedCount(), result.errorCount());
        return result;
    }
}
