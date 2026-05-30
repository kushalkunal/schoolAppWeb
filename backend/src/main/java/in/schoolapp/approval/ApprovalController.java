package in.schoolapp.approval;

import in.schoolapp.approval.dto.ApprovalRequestResponse;
import in.schoolapp.approval.entity.ApprovalStatus;
import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The approvals inbox. Listing is open to OWNER/PRINCIPAL/ADMIN (so requesters can track their
 * pending items), but deciding is restricted to OWNER/PRINCIPAL — and the service additionally
 * forbids approving your own request, so a maker can never also be the checker.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<List<ApprovalRequestResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) ApprovalStatus status
    ) {
        return ApiResponse.success(
            approvalService.list(tenantId, status).stream().map(ApprovalRequestResponse::from).toList());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(AppRoles.OWNER_OR_PRINCIPAL)
    public ApiResponse<ApprovalRequestResponse> approve(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.success(ApprovalRequestResponse.from(approvalService.approve(tenantId, id, note)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(AppRoles.OWNER_OR_PRINCIPAL)
    public ApiResponse<ApprovalRequestResponse> reject(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.success(ApprovalRequestResponse.from(approvalService.reject(tenantId, id, note)));
    }
}
