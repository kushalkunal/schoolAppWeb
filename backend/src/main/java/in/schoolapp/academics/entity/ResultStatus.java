package in.schoolapp.academics.entity;

/**
 * Lifecycle state of an exam's results.
 *
 * <ul>
 *   <li>{@code DRAFT} — marks are still being entered; result not yet computed.</li>
 *   <li>{@code READY} — all component marks submitted; result auto-computed and ranked.</li>
 *   <li>{@code PUBLISHED} — admin published; PDF report cards sent to parents.</li>
 * </ul>
 */
public enum ResultStatus {
    DRAFT,
    READY,
    PUBLISHED
}
