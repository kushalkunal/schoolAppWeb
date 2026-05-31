package in.schoolapp.imports;

import in.schoolapp.school.StaffService;
import in.schoolapp.school.dto.CreateStaffRequest;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.entity.StaffRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

/**
 * Bulk staff import. Required columns:
 *
 * <pre>
 * first_name, phone, role
 * </pre>
 *
 * Optional: {@code last_name}, {@code email}.
 *
 * <p>{@code role} accepts the same string values as the {@link StaffRole} enum
 * (PRINCIPAL, ADMIN, CLASS_TEACHER, SUBJECT_TEACHER, ACCOUNTANT, VIEWER).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffImportService {

    private final StaffService staffService;

    @Transactional
    public ImportResult<StaffResponse> importStaff(UUID tenantId, InputStream csv, boolean dryRun) {
        ImportResult<StaffResponse> result = new ImportResult<>(dryRun);

        CsvImporter.parse(csv, (rowNumber, rec) -> {
            result.recordRowRead();
            try {
                String firstName = CsvImporter.required(rec, "first_name");
                String lastName  = CsvImporter.optional(rec, "last_name");
                String phone     = CsvImporter.required(rec, "phone");
                String email     = CsvImporter.optional(rec, "email");
                String roleStr   = CsvImporter.required(rec, "role");

                StaffRole role;
                try { role = StaffRole.valueOf(roleStr.trim().toUpperCase()); }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid role \"" + roleStr
                        + "\" — must be PRINCIPAL, ADMIN, CLASS_TEACHER, SUBJECT_TEACHER, ACCOUNTANT, or VIEWER");
                }

                CreateStaffRequest req = new CreateStaffRequest(firstName, lastName, phone, email, role);

                if (dryRun) {
                    result.recordAccepted(new StaffResponse(
                        null, tenantId, firstName, lastName,
                        displayName(firstName, lastName),
                        phone, email, null, null, role, true, false, java.util.Map.of()));
                } else {
                    result.recordAccepted(staffService.createStaff(tenantId, req));
                }
            } catch (Exception e) {
                result.recordError(rowNumber, e.getMessage());
            }
        });

        log.info("Staff CSV import tenant={} dryRun={} read={} accepted={} errors={}",
            tenantId, dryRun, result.totalRowsRead(), result.acceptedCount(), result.errorCount());
        return result;
    }

    private static String displayName(String first, String last) {
        return last != null && !last.isBlank() ? (first + " " + last).trim() : first;
    }
}
