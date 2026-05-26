package in.schoolapp.fee.structure;

import in.schoolapp.fee.entity.FeeHead;
import in.schoolapp.fee.repository.FeeHeadRepository;
import in.schoolapp.fee.structure.dto.MatrixImportRowResult;
import in.schoolapp.fee.structure.dto.MatrixRowDto;
import in.schoolapp.imports.CsvImporter;
import in.schoolapp.imports.ImportResult;
import in.schoolapp.school.entity.SchoolClass;
import in.schoolapp.school.repository.SchoolClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Imports the (class × fee-head × term) matrix from a CSV. The on-the-wire format is
 * intentionally narrow so admins can paste it from any spreadsheet:
 *
 * <pre>
 * class_name, fee_head_name, term_number, amount_rupees, is_optional
 * Class 1,    Tuition,       1,           5000,           false
 * Class 1,    Transport,     ,            12000,          true     # annual (blank term)
 * </pre>
 *
 * Required columns: {@code class_name}, {@code fee_head_name}, {@code amount_rupees}.
 * Optional: {@code term_number} (blank = annual), {@code is_optional} (default false).
 *
 * <p>Behavior:
 * <ul>
 *   <li><b>dryRun=true</b>: parse + resolve names; return per-row results; do not write.</li>
 *   <li><b>dryRun=false</b>: parse + resolve. If <em>any</em> row errored, fail the whole
 *       import (no partial commits — easier to reason about than partial state). Otherwise
 *       replace the version's row set via {@link FeeStructureService#replaceRows}.</li>
 * </ul>
 * Term <em>schedules</em> (start/end/due dates) are not part of the CSV — set those in the
 * editor before importing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeStructureMatrixImportService {

    private final FeeStructureService structureService;
    private final SchoolClassRepository classRepo;
    private final FeeHeadRepository headRepo;

    @Transactional
    public ImportResult<MatrixImportRowResult> importMatrix(
            UUID tenantId, UUID versionId, InputStream csv, boolean dryRun) {

        // Make sure the version exists + is DRAFT before we read a single byte of CSV.
        structureService.require(tenantId, versionId);

        // Pre-load name → entity maps so we don't issue N queries per row.
        Map<String, SchoolClass> classes = new HashMap<>();
        for (SchoolClass c : classRepo.findBySchoolIdOrderBySortOrderAscNameAsc(tenantId)) {
            classes.put(c.getName().toLowerCase(Locale.ROOT), c);
        }
        Map<String, FeeHead> heads = new HashMap<>();
        for (FeeHead h : headRepo.findBySchoolIdAndActiveTrueOrderByName(tenantId)) {
            heads.put(h.getName().toLowerCase(Locale.ROOT), h);
        }

        ImportResult<MatrixImportRowResult> result = new ImportResult<>(dryRun);
        List<MatrixRowDto> resolved = new ArrayList<>();

        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            String className   = CsvImporter.optional(rec, "class_name");
            String headName    = CsvImporter.optional(rec, "fee_head_name");
            String termStr     = CsvImporter.optional(rec, "term_number");
            String amountStr   = CsvImporter.optional(rec, "amount_rupees");
            String optionalStr = CsvImporter.optional(rec, "is_optional");

            try {
                if (className == null) throw new IllegalArgumentException("class_name is required");
                if (headName  == null) throw new IllegalArgumentException("fee_head_name is required");
                if (amountStr == null) throw new IllegalArgumentException("amount_rupees is required");

                SchoolClass c = classes.get(className.toLowerCase(Locale.ROOT));
                if (c == null) throw new IllegalArgumentException("Unknown class \"" + className + "\"");
                FeeHead h = heads.get(headName.toLowerCase(Locale.ROOT));
                if (h == null) throw new IllegalArgumentException(
                    "Unknown (or inactive) fee head \"" + headName + "\"");

                Integer term = null;
                if (termStr != null && !termStr.isBlank()) {
                    try { term = Integer.parseInt(termStr); }
                    catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("term_number must be an integer or blank");
                    }
                }

                double rupees;
                try { rupees = Double.parseDouble(amountStr); }
                catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("amount_rupees must be a number");
                }
                if (rupees < 0) throw new IllegalArgumentException("amount_rupees must be non-negative");
                long paise = Math.round(rupees * 100);

                boolean isOptional = parseBool(optionalStr);

                resolved.add(new MatrixRowDto(c.getId(), h.getId(), term, paise, isOptional));
                result.recordAccepted(new MatrixImportRowResult(
                    rowNumber, c.getName(), h.getName(), term, paise, isOptional, null));
            } catch (Exception ex) {
                result.recordError(rowNumber, ex.getMessage());
            }
        });

        if (!dryRun) {
            if (result.errorCount() > 0) {
                // Fail the whole commit — partial matrices are confusing.
                log.info("Matrix CSV commit refused: {} errors", result.errorCount());
            } else {
                structureService.replaceRows(tenantId, versionId, resolved);
            }
        }
        log.info("Matrix CSV import tenant={} version={} dryRun={} read={} ok={} errors={}",
            tenantId, versionId, dryRun, result.totalRowsRead(),
            resolved.size(), result.errorCount());
        return result;
    }

    private static boolean parseBool(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("yes") || t.equals("y") || t.equals("1");
    }
}
