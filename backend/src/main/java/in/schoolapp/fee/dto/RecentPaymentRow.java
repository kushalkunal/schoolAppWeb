package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.PaymentMode;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight row returned by the cashier-dashboard "recent payments" endpoint.
 * Includes student name (joined from the student table) to avoid a separate N+1 lookup.
 */
public record RecentPaymentRow(
    UUID paymentId,
    UUID studentId,
    String studentName,
    long amountPaise,
    PaymentMode paymentMode,
    String receiptNumber,
    String receiptPdfUrl,
    LocalDate paymentDate,
    /** Name of the staff member (accountant/cashier) who collected this payment. */
    String collectedByName
) {}
