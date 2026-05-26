package in.schoolapp.fee.entity;

/**
 * Lifecycle of an invoice. Stored as VARCHAR — never rename a value.
 *
 * <p>Slice 15 adds two values:
 * <ul>
 *   <li>{@code SUPERSEDED}: an installment plan replaced this invoice with N children.</li>
 *   <li>{@code REFUNDED}: every payment against this invoice has been reversed.</li>
 * </ul>
 */
public enum InvoiceStatus {
    PENDING,
    PARTIAL,
    PAID,
    WAIVED,
    SUPERSEDED,
    REFUNDED
}
