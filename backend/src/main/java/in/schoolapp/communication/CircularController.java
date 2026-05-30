package in.schoolapp.communication;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.communication.dto.CircularResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.communication.dto.CreateCircularRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/circulars")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.CIRCULARS)
public class CircularController {

    private final CircularService service;

    @PostMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ResponseEntity<ApiResponse<CircularResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateCircularRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.createAndDispatch(tenantId, req)));
    }

    @GetMapping
    public ApiResponse<java.util.List<CircularResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CircularResponse> p = service.list(tenantId, page, size);
        return ApiResponse.success(p.getContent(),
            new ApiResponse.Meta(p.getTotalElements(), p.getNumber(), p.getSize(), null));
    }

    @GetMapping("/{id}")
    public ApiResponse<CircularResponse> get(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return ApiResponse.success(service.get(tenantId, id));
    }
}
