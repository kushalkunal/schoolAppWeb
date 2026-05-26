package in.schoolapp.fee;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.fee.dto.CreateFeeHeadRequest;
import in.schoolapp.fee.dto.FeeHeadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fee-heads")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.FEE)
public class FeeHeadController {

    private final FeeHeadService service;

    @PostMapping
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ResponseEntity<ApiResponse<FeeHeadResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateFeeHeadRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(tenantId, req)));
    }

    @GetMapping
    public ApiResponse<List<FeeHeadResponse>> list(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.list(tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<FeeHeadResponse> update(
        @PathVariable UUID tenantId, @PathVariable UUID id,
        @Valid @RequestBody CreateFeeHeadRequest req
    ) {
        return ApiResponse.success(service.update(tenantId, id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.FEE_WRITER)
    public ApiResponse<Void> deactivate(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.deactivate(tenantId, id);
        return ApiResponse.ok();
    }
}
