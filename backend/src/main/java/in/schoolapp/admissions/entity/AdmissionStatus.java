package in.schoolapp.admissions.entity;

import java.util.Set;

/**
 * Linear state machine of an admission. Allowed transitions are encoded in
 * {@link #canTransitionTo} so the service rejects invalid jumps centrally.
 *
 * <pre>
 *   ENQUIRY
 *      └─→ APPLICATION_SUBMITTED ┬─→ TEST_SCHEDULED ┬─→ TEST_COMPLETED ─→ OFFERED ┬─→ ACCEPTED ─→ ENROLLED
 *                                │                                                ├─→ DECLINED
 *                                │                                                └─→ REJECTED
 *                                └─→ REJECTED
 *
 *   Terminal: ENROLLED, DECLINED, REJECTED, WITHDRAWN
 * </pre>
 */
public enum AdmissionStatus {
    ENQUIRY,
    APPLICATION_SUBMITTED,
    TEST_SCHEDULED,
    TEST_COMPLETED,
    OFFERED,
    ACCEPTED,
    DECLINED,
    ENROLLED,
    WITHDRAWN,
    REJECTED;

    /** Whitelist of valid forward transitions. Returns true when {@code next} is reachable. */
    public boolean canTransitionTo(AdmissionStatus next) {
        return switch (this) {
            case ENQUIRY               -> Set.of(APPLICATION_SUBMITTED, REJECTED).contains(next);
            case APPLICATION_SUBMITTED -> Set.of(TEST_SCHEDULED, TEST_COMPLETED, OFFERED, REJECTED).contains(next);
            case TEST_SCHEDULED        -> Set.of(TEST_COMPLETED, REJECTED).contains(next);
            case TEST_COMPLETED        -> Set.of(OFFERED, REJECTED).contains(next);
            case OFFERED               -> Set.of(ACCEPTED, DECLINED, REJECTED).contains(next);
            case ACCEPTED              -> Set.of(ENROLLED, WITHDRAWN).contains(next);
            // Terminal states.
            case DECLINED, ENROLLED, WITHDRAWN, REJECTED -> false;
        };
    }
}
