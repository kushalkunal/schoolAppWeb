package in.schoolapp.imports;

import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.dto.CreateStudentRequest;
import in.schoolapp.student.entity.ParentRelation;
import in.schoolapp.student.dto.StudentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk student import from CSV. Required columns:
 *
 * <pre>
 * first_name, last_name, class, section, parent_phone
 * </pre>
 *
 * Optional columns:
 *
 * <pre>
 * parent_name, parent_email, parent_relation, admission_number,
 * gender, date_of_birth, blood_group, address
 * </pre>
 *
 * Behaviour:
 * <ul>
 *   <li>Header names are case-insensitive — internally lower-cased.</li>
 *   <li>{@code dry_run=true} validates every row but doesn't persist anything. Lets the
 *       admin preview "10 rows look good, 2 have problems" before committing.</li>
 *   <li>Unknown class/section names → row error (no auto-creation; ambiguity risk).</li>
 *   <li>Duplicate admission numbers within the same upload → second occurrence rejected.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentImportService {

    private final ClassSectionService classSectionService;
    private final StudentService studentService;

    @Transactional
    public ImportResult<StudentResponse> importStudents(UUID tenantId, InputStream csv, boolean dryRun) {
        ImportResult<StudentResponse> result = new ImportResult<>(dryRun);

        // Build a (class lower-case, section lower-case) → sectionId index so we can look up by name.
        Map<String, UUID> sectionLookup = new HashMap<>();
        for (ClassResponse c : classSectionService.listClasses(tenantId)) {
            for (SectionResponse s : c.sections()) {
                sectionLookup.put(key(c.name(), s.name()), s.id());
            }
        }

        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            try {
                String firstName = CsvImporter.required(rec, "first_name");
                String lastName  = CsvImporter.optional(rec, "last_name");
                String className = CsvImporter.required(rec, "class");
                String sectName  = CsvImporter.required(rec, "section");
                String parentPh  = CsvImporter.required(rec, "parent_phone");

                UUID sectionId = sectionLookup.get(key(className, sectName));
                if (sectionId == null) {
                    result.recordError(rowNumber,
                        "Unknown class/section: \"" + className + " - " + sectName + "\". Create it under Settings → Classes first.");
                    return;
                }

                CreateStudentRequest req = new CreateStudentRequest(
                    firstName,
                    lastName,
                    sectionId,
                    parentPh,
                    CsvImporter.optional(rec, "parent_name"),
                    CsvImporter.optional(rec, "parent_email"),
                    parseRelation(CsvImporter.optional(rec, "parent_relation")),
                    CsvImporter.optional(rec, "admission_number"),
                    CsvImporter.optional(rec, "gender"),
                    parseDate(CsvImporter.optional(rec, "date_of_birth")),
                    CsvImporter.optional(rec, "blood_group"),
                    CsvImporter.optional(rec, "address")
                );

                if (dryRun) {
                    // Build a synthetic accepted item so the response carries the previewed value.
                    result.recordAccepted(new StudentResponse(
                        null, firstName, lastName, displayName(firstName, lastName),
                        req.admissionNumber(), req.gender(), req.dateOfBirth(),
                        req.bloodGroup(), null, true));
                } else {
                    result.recordAccepted(studentService.createStudent(tenantId, req));
                }
            } catch (Exception e) {
                result.recordError(rowNumber, e.getMessage());
            }
        });

        log.info("Student CSV import tenant={} dryRun={} read={} accepted={} errors={}",
            tenantId, dryRun, result.totalRowsRead(), result.acceptedCount(), result.errorCount());
        return result;
    }

    private static String key(String className, String sectionName) {
        return className.trim().toLowerCase() + "|" + sectionName.trim().toLowerCase();
    }

    private static ParentRelation parseRelation(String s) {
        if (s == null || s.isBlank()) return null;
        try { return ParentRelation.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid parent_relation \"" + s
                + "\" — must be FATHER, MOTHER, or GUARDIAN");
        }
    }

    private static java.time.LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        // Accept ISO yyyy-MM-dd or the common Indian dd/MM/yyyy.
        try { return java.time.LocalDate.parse(s); }
        catch (Exception ignored) {}
        try {
            return java.time.LocalDate.parse(s,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date_of_birth \"" + s
                + "\" — use yyyy-MM-dd or dd/MM/yyyy");
        }
    }

    private static String displayName(String first, String last) {
        return last != null && !last.isBlank() ? (first + " " + last).trim() : first;
    }
}
