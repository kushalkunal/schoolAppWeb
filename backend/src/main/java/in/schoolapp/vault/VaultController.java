package in.schoolapp.vault;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.vault.entity.VaultDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/vault")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.STUDENT_DOCUMENT_VAULT)
public class VaultController {

    private final VaultService service;

    @PostMapping(value = "/students/{studentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
        @PathVariable UUID tenantId, @PathVariable UUID studentId,
        @RequestPart("file") MultipartFile file,
        @RequestParam String docType,
        @RequestParam(required = false) String notes
    ) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Empty file");
        }
        try {
            VaultDocument d = service.upload(tenantId, studentId, docType,
                file.getOriginalFilename(), file.getContentType(), file.getBytes(), notes);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "id", d.getId(),
                "docType", d.getDocType(),
                "fileName", d.getFileName(),
                "fileUrl", d.getFileUrl()
            )));
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Could not read upload");
        }
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<VaultDocument>> list(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(service.listForStudent(studentId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ApiResponse.ok();
    }
}
