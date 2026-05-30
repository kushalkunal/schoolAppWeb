package in.schoolapp.approval.entity;

/**
 * The kinds of maker-checker requests the engine routes. Each value has exactly one
 * {@link in.schoolapp.approval.ApprovalHandler} that performs the staged domain change on approval.
 * Add a value here + a handler to bring a new action under maker-checker control.
 */
public enum ApprovalType {
    FEE_DISCOUNT,
    FEE_REFUND
}
