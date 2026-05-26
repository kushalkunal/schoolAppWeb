package in.schoolapp.migration.entity;

/**
 * Drives prompt-template selection (per type) + commit-target selection. Slice 9 implements
 * {@link #FEE_RECEIPT} end-to-end — the gap-analysis §7 example. The other types parse OCR +
 * extract via LLM (same pipeline) but defer commit-time persistence to Slice 9.5.
 */
public enum MigrationJobType {
    FEE_RECEIPT,
    ATTENDANCE,
    MARKS,
    ADMISSION_FORM
}
