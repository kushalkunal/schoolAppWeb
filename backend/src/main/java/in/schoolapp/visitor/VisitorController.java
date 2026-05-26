package in.schoolapp.visitor;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.visitor.dto.CreateVisitorRequest;
import in.schoolapp.visitor.dto.VisitorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/visitors")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.VISITOR_MANAGEMENT)
public class VisitorController {

    private final VisitorService service;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<VisitorResponse>> checkIn(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateVisitorRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.checkIn(tenantId, req)));
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<VisitorResponse> checkOut(
        @PathVariable UUID tenantId, @PathVariable UUID id
    ) {
        return ApiResponse.success(service.checkOut(tenantId, id));
    }

    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<VisitorResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success(service.list(tenantId, page, size));
    }

    @GetMapping("/open")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<VisitorResponse>> open(@PathVariable UUID tenantId) {
        return ApiResponse.success(service.listOpen(tenantId));
    }
}
