package in.schoolapp.fee.structure.dto;

/**
 * A single parsed CSV row in the matrix importer's preview, with the class + fee-head names
 * resolved to IDs (or {@code null} on error rows). The {@code error} field is populated when
 * the row failed validation — the row still appears in the result so the UI can render a
 * line-by-line table.
 */
public record MatrixImportRowResult(
    int rowNumber,
    String className,
    String feeHeadName,
    Integer termNumber,
    long amountPaise,
    boolean optional,
    String error
) {
    public boolean ok() { return error == null; }
}
