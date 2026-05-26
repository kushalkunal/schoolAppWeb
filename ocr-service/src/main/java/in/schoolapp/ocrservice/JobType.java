package in.schoolapp.ocrservice;

/**
 * Mirrors {@code in.schoolapp.migration.entity.MigrationJobType} in the main backend.
 * Duplicated rather than shared because this service must build without depending on the
 * backend's classpath.
 */
public enum JobType {
    FEE_RECEIPT,
    ATTENDANCE,
    MARKS,
    ADMISSION_FORM
}
