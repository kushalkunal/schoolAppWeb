package in.schoolapp.documents;

/**
 * Catalogue of document types the platform can render to PDF. Values are persisted on
 * {@code document_templates.document_type} — never rename or your per-school overrides
 * stop resolving.
 *
 * <p>Each value maps 1:1 to a default Thymeleaf template under
 * {@code classpath:/templates/documents/<lower-case>.html}.
 */
public enum DocumentType {
    /** Fee collection receipt with school header, student info, line items, amount in words. */
    RECEIPT,
    /** Transfer Certificate issued on student exit. */
    TRANSFER_CERTIFICATE,
    /** Bonafide / studying certificate for current students. */
    BONAFIDE,
    /** Exam admit card with seat number, dates, instructions. */
    HALL_TICKET,
    /** Full report card with subject-wise marks, grade, rank, signatures. */
    REPORT_CARD;

    /** Default classpath template path. */
    public String defaultTemplatePath() {
        return "documents/" + name().toLowerCase();
    }
}
