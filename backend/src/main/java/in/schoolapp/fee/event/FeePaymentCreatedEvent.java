package in.schoolapp.fee.event;

import java.util.UUID;

/**
 * Fired after a fee payment is committed. Receipt dispatch, analytics aggregation, and
 * anti-fraud listeners subscribe to this rather than inline the logic on the write path —
 * keeps quick-collect fast and testable.
 */
public record FeePaymentCreatedEvent(
    UUID tenantId,
    UUID paymentId,
    UUID studentId,
    long amountPaise,
    String receiptNumber,
    String receiptPdfUrl
) {}
