package in.schoolapp.academics.entity;

/**
 * Lifecycle state of an exam's results.
 *
 * <ul>
 *   <li>{@code DRAFT} — marks are still being entered; result not yet computed.</li>
 *   <li>{@code READY} — all component marks submitted; result auto-computed and ranked.</li>
 *   <li>{@code VERIFIED} — the section's class teacher has reviewed and verified the computed
 *       results; required before a principal/admin may publish (audit #7).</li>
 *   <li>{@code PUBLISHED} — admin published; PDF report cards sent to parents.</li>
 * </ul>
 */
public enum ResultStatus {
    DRAFT,
    READY,
    VERIFIED,
    PUBLISHED
}
