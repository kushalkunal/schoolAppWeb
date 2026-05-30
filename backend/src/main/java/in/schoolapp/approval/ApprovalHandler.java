package in.schoolapp.approval;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalType;

/**
 * Performs the staged domain change when a request of its {@link #type()} is approved. One handler
 * per {@link ApprovalType}; {@link ApprovalService} looks it up and calls {@link #apply} inside the
 * approval transaction, so the mutation and the status flip commit together.
 * <p>
 * Implementations live in their own module (e.g. {@code fee}) so the approval engine stays free of
 * domain dependencies. {@code apply} runs as the <em>approver</em> — set {@code approvedById} to
 * {@code TenantContext.getStaffId()}.
 */
public interface ApprovalHandler {

    ApprovalType type();

    /** Apply the staged change. Throw to abort the approval (transaction rolls back). */
    void apply(ApprovalRequest request);
}
