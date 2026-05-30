package in.schoolapp.fee.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily/range fee-collection register (audit #21): what was collected over a period, broken down
 * by payment mode, with the grand total and count. This is the accountant's reconciliation report
 * — the same mode totals the cash-drawer close reconciles against.
 * Returned by {@code GET /fees/reports/collection-register?from=&to=} (defaults to today).
 */
public record CollectionRegisterResponse(
    LocalDate from,
    LocalDate to,
    long totalPaise,
    long paymentCount,
    List<ModeBreakdownRow> byMode
) {
    public record ModeBreakdownRow(String mode, long amountPaise, long paymentCount) {}
}
