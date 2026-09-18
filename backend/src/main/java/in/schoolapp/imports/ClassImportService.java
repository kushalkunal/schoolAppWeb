package in.schoolapp.imports;

import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.dto.CreateClassesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Bulk class+section import. Columns: {@code class_name} (or {@code name}) required;
 * {@code sections} optional (separated by {@code ,} or {@code |}, e.g. "A|B|C"). When sections is
 * blank, a single section "A" is created. One class per row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassImportService {

    private final ClassSectionService classSectionService;

    @Transactional
    public ImportResult<String> importClasses(UUID tenantId, InputStream csv, boolean dryRun) {
        ImportResult<String> result = new ImportResult<>(dryRun);
        int[] order = {0};
        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            try {
                String name = CsvImporter.optional(rec, "class_name");
                if (name == null) name = CsvImporter.required(rec, "name");
                String sectionsRaw = CsvImporter.optional(rec, "sections");
                List<String> sections = sectionsRaw == null ? List.of("A")
                    : Arrays.stream(sectionsRaw.split("[,|]")).map(String::trim).filter(s -> !s.isBlank()).toList();
                if (sections.isEmpty()) sections = List.of("A");

                if (!dryRun) {
                    classSectionService.bulkCreate(tenantId, new CreateClassesRequest(
                        List.of(new CreateClassesRequest.ClassSpec(name, sections, ++order[0]))));
                }
                result.recordAccepted(name + " (" + String.join("/", sections) + ")");
            } catch (Exception e) {
                result.recordError(rowNumber, e.getMessage());
            }
        });
        log.info("Class CSV import tenant={} dryRun={} read={} accepted={} errors={}",
            tenantId, dryRun, result.totalRowsRead(), result.acceptedCount(), result.errorCount());
        return result;
    }
}
