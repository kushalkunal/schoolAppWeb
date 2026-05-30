package in.schoolapp.fee;

import in.schoolapp.approval.ApprovalHandler;
import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.fee.entity.FeeDiscount;
import in.schoolapp.fee.repository.FeeDiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Activates a fee discount once its approval is granted: the discount was created
 * {@code active=false} (so {@code findApplicable} ignored it), and approval flips it live and
 * stamps the approver. Depends only on the repository — never on {@link FeeDiscountService} — so
 * there is no cycle through {@link in.schoolapp.approval.ApprovalService}.
 */
@Component
@RequiredArgsConstructor
public class FeeDiscountApprovalHandler implements ApprovalHandler {

    private final FeeDiscountRepository discountRepository;
    private final AuditLogger audit;

    @Override
    public ApprovalType type() {
        return ApprovalType.FEE_DISCOUNT;
    }

    @Override
    public void apply(ApprovalRequest request) {
        FeeDiscount d = discountRepository.findById(request.getSubjectId())
            .filter(x -> x.getSchoolId().equals(request.getSchoolId()))
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Discount not found"));
        d.setActive(true);
        d.setApprovedById(TenantContext.getStaffId());
        discountRepository.save(d);
        audit.logAction(request.getSchoolId(), "FeeDiscount", d.getId(), "DISCOUNT_APPROVED",
            Map.of("studentId", String.valueOf(d.getStudentId()),
                "discountType", d.getDiscountType().name()));
    }
}
