package in.schoolapp.migration.entity;

/**
 * Migration job lifecycle. Transitions:
 * <pre>
 *   UPLOADED → PROCESSING → REVIEW → COMMITTED
 *   any state → FAILED   (terminal — error_message populated)
 * </pre>
 * Slice 9 surfaces all states; the human-review UI pulls jobs in {@code REVIEW} state.
 */
public enum MigrationJobStatus {
    /** Image uploaded; OCR + LLM not yet started. */
    UPLOADED,
    /** OCR + LLM running async. Roughly 5–60 seconds depending on provider. */
    PROCESSING,
    /** Extraction complete; awaiting human confirmation before commit. */
    REVIEW,
    /** Human-confirmed records have been written as historical entities. */
    COMMITTED,
    /** Terminal failure — see {@code error_message}. Re-upload to retry. */
    FAILED
}
