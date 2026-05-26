package in.schoolapp.cashrecon.dto;

import java.time.LocalDate;

/**
 * What the accountant sees in the "expected" column before they punch in the counted amounts.
 * Computed by {@link in.schoolapp.cashrecon.CashReconciliationService#expectedForDay} from
 * {@code fee_payments} for the given date.
 */
public record DayTotalsResponse(
    LocalDate date,
    long expectedCashPaise,
    long expectedUpiPaise,
    long expectedChequePaise,
    long expectedOtherPaise,
    long totalPaise,
    int  paymentCount
) {}
