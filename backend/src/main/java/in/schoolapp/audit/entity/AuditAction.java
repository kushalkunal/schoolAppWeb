package in.schoolapp.audit.entity;

/**
 * Verbs recorded in {@code audit_log.action}. Coarse-grained deliberately — the row's
 * {@code old_values}/{@code new_values} JSON captures the field-level delta; the action just
 * says what kind of change it was so reports can group by "deletes this quarter".
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    /** Non-CRUD business action (publish exam, dismiss alert, …). */
    ACTION
}
