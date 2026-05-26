package in.schoolapp.fee.entity;

/**
 * When a reminder schedule fires relative to an invoice's due date:
 * <ul>
 *   <li>{@link #BEFORE_DUE} — fires {@code daysOffset} days <i>before</i> the due date
 *       (e.g. "remind 5 days before")</li>
 *   <li>{@link #AFTER_DUE} — fires {@code daysOffset} days <i>after</i> the due date
 *       (escalation schedule: "3 days late", "7 days late", "14 days late")</li>
 *   <li>{@link #ON_DUE} — fires on the due date itself (daysOffset must be 0)</li>
 * </ul>
 */
public enum ReminderTriggerType {
    BEFORE_DUE,
    ON_DUE,
    AFTER_DUE
}
