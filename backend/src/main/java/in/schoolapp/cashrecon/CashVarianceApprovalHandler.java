package in.schoolapp.cashrecon;

import in.schoolapp.approval.ApprovalHandler;
import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.cashrecon.entity.CashReconciliation;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Signs off a cash-drawer over/short once the escalation is approved, stamping the reviewer. The
 * person who closed the drawer cannot approve their own variance (enforced by the engine's
 * self-approval ban). Depends only on the repository, so no cycle through the approval engine.
 */
@Component
@RequiredArgsConstructor
public class CashVarianceApprovalHandler implements ApprovalHandler {

    private final CashReconciliationRepository repository;
    private final AuditLogger audit;

    @Override
    public ApprovalType type() {
        return ApprovalType.CASH_VARIANCE;
    }

    @Override
    public void apply(ApprovalRequest request) {
        CashReconciliation r = repository.findByIdAndSchoolId(request.getSubjectId(), request.getSchoolId())
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "CashReconciliation", request.getSubjectId()));
        r.setVarianceReviewed(true);
        r.setReviewedById(TenantContext.getStaffId());
        repository.save(r);
        audit.logAction(request.getSchoolId(), "CashReconciliation", r.getId(), "CASH_VARIANCE_REVIEWED",
            Map.of("variancePaise", r.getVariancePaise(), "date", String.valueOf(r.getClosedOnDate())));
    }
}
