package in.schoolapp.migration;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.migration.dto.CommitMigrationRequest;
import in.schoolapp.migration.dto.MigrationJobResponse;
import in.schoolapp.migration.entity.MigrationJobType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/migration")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.PAPER_MIGRATION)
public class MigrationController {

    private final MigrationJobService jobService;

    /**
     * Multipart upload — {@code file} is the scanned image (JPEG/PNG/PDF), {@code type} is
     * one of {@link MigrationJobType}. Returns immediately with status=UPLOADED; the OCR + LLM
     * pipeline runs async and the job moves to REVIEW (or FAILED).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<MigrationJobResponse>> upload(
        @PathVariable UUID tenantId,
        @RequestParam("type") MigrationJobType type,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Upload file is empty");
        }
        try {
            MigrationJobResponse response = jobService.upload(
                tenantId, type, file.getBytes(), file.getContentType());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Could not read uploaded file");
        }
    }

    @GetMapping
    public ApiResponse<List<MigrationJobResponse>> listJobs(@PathVariable UUID tenantId) {
        return ApiResponse.success(jobService.listJobs(tenantId));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<MigrationJobResponse> getJob(
        @PathVariable UUID tenantId,
        @PathVariable UUID jobId
    ) {
        return ApiResponse.success(jobService.getJob(tenantId, jobId));
    }

    @PostMapping("/{jobId}/commit")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<MigrationJobResponse> commit(
        @PathVariable UUID tenantId,
        @PathVariable UUID jobId,
        @Valid @RequestBody CommitMigrationRequest request
    ) {
        return ApiResponse.success(jobService.commit(tenantId, jobId, request));
    }
}
