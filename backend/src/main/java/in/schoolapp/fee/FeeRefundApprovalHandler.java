package in.schoolapp.fee;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.approval.ApprovalHandler;
import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalType;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Executes a refund once approved, from the parameters staged in the request's payload. The actual
 * money movement (walking back the invoice + recording a {@code FeeAdjustment}) happens here, in the
 * approval transaction — never at request time — and the adjustment is stamped with the approver.
 */
@Component
@RequiredArgsConstructor
public class FeeRefundApprovalHandler implements ApprovalHandler {

    private final FeeRefundService refundService;
    private final ObjectMapper objectMapper;

    /** Staged refund parameters serialized into {@link ApprovalRequest#getPayloadJson()}. */
    public record RefundPayload(UUID paymentId, Long amountPaise, String reason) {}

    @Override
    public ApprovalType type() {
        return ApprovalType.FEE_REFUND;
    }

    @Override
    public void apply(ApprovalRequest request) {
        RefundPayload p = parse(request.getPayloadJson());
        refundService.executeRefund(
            request.getSchoolId(), p.paymentId(), p.amountPaise(), p.reason(), TenantContext.getStaffId());
    }

    private RefundPayload parse(String json) {
        try {
            return objectMapper.readValue(json, RefundPayload.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Malformed refund approval payload");
        }
    }
}
