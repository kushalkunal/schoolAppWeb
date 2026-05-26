package in.schoolapp.fee.structure.dto;

/**
 * Trigger the bulk invoice generator. {@code termNumber} narrows generation to one term;
 * null = generate for all terms (or the annual rows if there are no terms).
 * <p>
 * The generator is idempotent: re-running with the same (version, term) for the same school
 * produces no duplicate invoices (enforced by the partial unique index in Flyway V20).
 */
public record GenerateInvoicesRequest(
    Integer termNumber
) {}
