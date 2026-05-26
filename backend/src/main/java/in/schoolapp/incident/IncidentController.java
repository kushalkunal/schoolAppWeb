package in.schoolapp.incident;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import in.schoolapp.incident.dto.CreateIncidentRequest;
import in.schoolapp.incident.dto.IncidentResponse;
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
@RequestMapping("/api/v1/tenants/{tenantId}/incidents")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.INCIDENT_LOG)
public class IncidentController {

    private final IncidentService service;

    @PostMapping
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ResponseEntity<ApiResponse<IncidentResponse>> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CreateIncidentRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(service.create(tenantId, req)));
    }

    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Page<IncidentResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.success(service.list(tenantId, page, size));
    }

    @GetMapping("/by-student/{studentId}")
    @PreAuthorize(AppRoles.ANY_TEACHER)
    public ApiResponse<List<IncidentResponse>> byStudent(
        @PathVariable UUID tenantId, @PathVariable UUID studentId
    ) {
        return ApiResponse.success(service.forStudent(studentId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ApiResponse.ok();
    }
}
