package in.schoolapp.audit;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.feature.RequiresFeature;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * DPDP Act 2023 data-deletion intake. A principal files a request to have a
 * subject's data removed; the request is logged as an audit action and
 * surfaces in the compliance workflow — actual purging happens out-of-band
 * (requires cross-table orchestration + grace periods for financial records).
 * <p>
 * This endpoint deliberately does NOT delete data inline. Building that
 * workflow is a larger slice (retention policy, financial-record exemptions,
 * DPO review queue); for now we just guarantee that every request lands in
 * {@code audit_log} with the principal's staff id and the target's type+id.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/data-deletion-requests")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.DPDP_REQUESTS)
public class DataDeletionController {

    private final AuditLogger auditLogger;

    public record DataDeletionRequest(
        @NotBlank String subjectType,   // STUDENT | PARENT | STAFF
        @NotBlank String subjectId,
        String reason
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('SCHOOL_OWNER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> request(
        @PathVariable UUID tenantId,
        @RequestBody DataDeletionRequest req
    ) {
        UUID subjectId;
        try {
            subjectId = UUID.fromString(req.subjectId());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "subjectId must be a UUID");
        }
        String type = req.subjectType() == null ? "" : req.subjectType().toUpperCase();
        if (!("STUDENT".equals(type) || "PARENT".equals(type) || "STAFF".equals(type))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "subjectType must be one of STUDENT, PARENT, STAFF");
        }

        UUID requestId = UUID.randomUUID();
        auditLogger.logAction(tenantId, type, subjectId, "DPDP_DELETION_REQUESTED", Map.of(
            "requestId", requestId.toString(),
            "reason", req.reason() == null ? "" : req.reason(),
            "requestedByStaffId", String.valueOf(TenantContext.getStaffId())
        ));
        log.info("DPDP deletion request recorded tenant={} type={} subject={} requestId={}",
            tenantId, type, subjectId, requestId);

        Map<String, Object> body = Map.of(
            "requestId", requestId,
            "status", "RECORDED",
            "message", "Request logged. Compliance review will follow per retention policy."
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(body));
    }
}
