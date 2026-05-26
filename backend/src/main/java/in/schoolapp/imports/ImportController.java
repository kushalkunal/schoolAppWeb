package in.schoolapp.imports;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.student.dto.StudentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Bulk-import surface. Each endpoint is multipart, accepts a CSV under the {@code file}
 * part, and supports {@code ?dryRun=true} so the admin can preview the impact before
 * committing.
 *
 * <p>All endpoints are role-gated to OWNER_OR_ADMIN and guarded by the
 * {@code BULK_IMPORT} feature flag — plans below STARTER will get
 * {@code FEATURE_DISABLED}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/imports")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.BULK_IMPORT)
public class ImportController {

    private final StudentImportService studentImportService;
    private final StaffImportService staffImportService;

    @PostMapping(value = "/students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<ImportResult<StudentResponse>>> importStudents(
        @PathVariable UUID tenantId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean dryRun
    ) throws IOException {
        rejectEmpty(file);
        var result = studentImportService.importStudents(tenantId, file.getInputStream(), dryRun);
        return ResponseEntity.status(dryRun ? HttpStatus.OK : HttpStatus.CREATED)
            .body(ApiResponse.success(result));
    }

    @PostMapping(value = "/staff", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<ImportResult<StaffResponse>>> importStaff(
        @PathVariable UUID tenantId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean dryRun
    ) throws IOException {
        rejectEmpty(file);
        var result = staffImportService.importStaff(tenantId, file.getInputStream(), dryRun);
        return ResponseEntity.status(dryRun ? HttpStatus.OK : HttpStatus.CREATED)
            .body(ApiResponse.success(result));
    }

    private static void rejectEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty CSV file");
        }
    }
}
