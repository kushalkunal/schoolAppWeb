package in.schoolapp.fee.dto;

/**
 * One row in the class-wise collection report.
 * Returned by {@code GET /fees/reports/class-wise?from=&to=}
 */
public record ClassCollectionRow(
    String classId,
    String className,
    long collectedPaise,
    long paymentCount,
    long outstandingPaise,
    long studentCount
) {}
