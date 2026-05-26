package in.schoolapp.audit;

import in.schoolapp.audit.dto.AuditLogResponse;
import in.schoolapp.audit.repository.AuditLogRepository;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only audit-log surface for compliance + admin review screens. Writes flow through
 * {@link AuditLogger} — this controller is read-only.
 * <ul>
 *   <li>{@code GET /audit?entityType=&entityId=} — per-entity history, ordered newest first</li>
 *   <li>{@code GET /audit?page=&size=} — tenant-wide paginated stream</li>
 * </ul>
 * Restricted to owner/admin: audit data reveals actor ids + timestamps across modules.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/audit")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.AUDIT_LOG)
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SCHOOL_OWNER','PRINCIPAL')")
    public ApiResponse<?> audit(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        if (entityType != null && entityId != null) {
            List<AuditLogResponse> rows = repository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream()
                .filter(r -> r.getSchoolId() == null || r.getSchoolId().equals(tenantId))
                .map(AuditLogResponse::from)
                .toList();
            return ApiResponse.success(rows);
        }
        int pageSize = clamp(size);
        Page<AuditLogResponse> p = repository
            .findBySchoolIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(Math.max(0, page), pageSize))
            .map(AuditLogResponse::from);
        return ApiResponse.success(p.getContent(),
            new ApiResponse.Meta(p.getTotalElements(), p.getNumber(), p.getSize(), null));
    }

    private static int clamp(int size) {
        if (size <= 0) return 50;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
