package in.schoolapp.approval.entity;

/**
 * Lifecycle of an {@link ApprovalRequest}. Terminal states (APPROVED/REJECTED/CANCELLED) are
 * immutable; only a PENDING request can be decided.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
