package in.schoolapp.billing.entity;

/**
 * Lifecycle of a school's subscription. Persisted as VARCHAR — never rename.
 *
 * <ul>
 *   <li>{@code TRIAL}     — within the free trial window; all paid features work.</li>
 *   <li>{@code ACTIVE}    — paid + in good standing.</li>
 *   <li>{@code PAST_DUE}  — payment failed but still in grace; features keep working.</li>
 *   <li>{@code SUSPENDED} — soft-suspended by platform admin or hard payment failure;
 *                          mutating endpoints blocked by SubscriptionGuardInterceptor.</li>
 *   <li>{@code CANCELLED} — terminal; same gate as SUSPENDED. Re-activation creates a new
 *                          subscription row.</li>
 * </ul>
 */
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED;

    public boolean allowsMutations() {
        return this == TRIAL || this == ACTIVE || this == PAST_DUE;
    }
}
