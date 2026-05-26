package in.schoolapp.export_;

/**
 * Output format for data-export endpoints. CSV is simpler for diffing + importing into other
 * systems; XLSX is what principals actually open. Defaults to XLSX when the client does not
 * specify.
 */
public enum ExportFormat {
    CSV,
    XLSX
}
