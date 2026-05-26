package in.schoolapp.fee.structure;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.fee.structure.dto.CreateVersionRequest;
import in.schoolapp.fee.structure.dto.FeeStructureVersionResponse;
import in.schoolapp.fee.structure.dto.GenerateInvoicesRequest;
import in.schoolapp.fee.structure.dto.GenerateInvoicesResponse;
import in.schoolapp.fee.structure.dto.MatrixImportRowResult;
import in.schoolapp.fee.structure.dto.MatrixResponse;
import in.schoolapp.fee.structure.dto.UpdateMatrixRequest;
import in.schoolapp.imports.ImportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Per-class fee-structure endpoints.
 * <pre>
 *   POST   /api/v1/tenants/{t}/fee-structure/versions               — create DRAFT
 *   GET    /api/v1/tenants/{t}/fee-structure/versions               — list
 *   GET    /api/v1/tenants/{t}/fee-structure/versions/{v}           — get matrix
 *   PUT    /api/v1/tenants/{t}/fee-structure/versions/{v}/matrix    — replace matrix (DRAFT only)
 *   POST   /api/v1/tenants/{t}/fee-structure/versions/{v}/activate
 *   POST   /api/v1/tenants/{t}/fee-structure/versions/{v}/archive
 *   POST   /api/v1/tenants/{t}/fee-structure/versions/{v}/generate  — bulk-create invoices
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fee-structure")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.FEE_STRUCTURE)
public class FeeStructureController {

    private final FeeStructureService service;
    private final GenerateInvoicesService generator;
    private final FeeStructureMatrixImportService matrixImporter;

    @PostMapping("/versions")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<FeeStructureVersionResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateVersionRequest req
    ) {
return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.create(tenantId, req, TenantContext.getStaffId())));
    }

    @GetMapping("/versions")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<List<FeeStructureVersionResponse>> list(@PathVariable UUID tenantId) {
return ApiResponse.success(service.list(tenantId));
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<MatrixResponse> getMatrix(
        @PathVariable UUID tenantId, @PathVariable UUID versionId
    ) {
return ApiResponse.success(service.getMatrix(tenantId, versionId));
    }

    @PutMapping("/versions/{versionId}/matrix")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<MatrixResponse> updateMatrix(
        @PathVariable UUID tenantId, @PathVariable UUID versionId,
        @Valid @RequestBody UpdateMatrixRequest req
    ) {
return ApiResponse.success(service.replaceMatrix(tenantId, versionId, req));
    }

    @PostMapping("/versions/{versionId}/activate")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<FeeStructureVersionResponse> activate(
        @PathVariable UUID tenantId, @PathVariable UUID versionId
    ) {
return ApiResponse.success(service.activate(tenantId, versionId));
    }

    @PostMapping("/versions/{versionId}/archive")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<FeeStructureVersionResponse> archive(
        @PathVariable UUID tenantId, @PathVariable UUID versionId
    ) {
return ApiResponse.success(service.archive(tenantId, versionId));
    }

    @PostMapping(value = "/versions/{versionId}/import-matrix",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<ImportResult<MatrixImportRowResult>>> importMatrix(
        @PathVariable UUID tenantId, @PathVariable UUID versionId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean dryRun
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty CSV file");
        }
        var result = matrixImporter.importMatrix(tenantId, versionId, file.getInputStream(), dryRun);
        return ResponseEntity.status(dryRun ? HttpStatus.OK : HttpStatus.CREATED)
            .body(ApiResponse.success(result));
    }

    @PostMapping("/versions/{versionId}/generate")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<GenerateInvoicesResponse> generate(
        @PathVariable UUID tenantId, @PathVariable UUID versionId,
        @RequestBody(required = false) GenerateInvoicesRequest req
    ) {
return ApiResponse.success(generator.generate(tenantId, versionId, req));
    }
}
