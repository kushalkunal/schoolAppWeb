package in.schoolapp.imports;

import in.schoolapp.academics.SubjectService;
import in.schoolapp.academics.dto.CreateSubjectsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Bulk subject import. Columns: {@code name} (required), {@code code} (optional).
 * One subject per row so each row gets independent error reporting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectImportService {

    private final SubjectService subjectService;

    @Transactional
    public ImportResult<String> importSubjects(UUID tenantId, InputStream csv, boolean dryRun) {
        ImportResult<String> result = new ImportResult<>(dryRun);
        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            try {
                String name = CsvImporter.required(rec, "name");
                String code = CsvImporter.optional(rec, "code");
                if (!dryRun) {
                    subjectService.bulkCreate(tenantId, new CreateSubjectsRequest(
                        List.of(new CreateSubjectsRequest.SubjectSpec(name, code))));
                }
                result.recordAccepted(code != null ? name + " (" + code + ")" : name);
            } catch (Exception e) {
                result.recordError(rowNumber, e.getMessage());
            }
        });
        log.info("Subject CSV import tenant={} dryRun={} read={} accepted={} errors={}",
            tenantId, dryRun, result.totalRowsRead(), result.acceptedCount(), result.errorCount());
        return result;
    }
}
