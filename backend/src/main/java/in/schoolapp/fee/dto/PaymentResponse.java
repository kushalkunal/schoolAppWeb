package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.fee.entity.PaymentMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID studentId,
    UUID invoiceId,
    long amountPaise,
    PaymentMode paymentMode,
    String receiptNumber,
    String receiptPdfUrl,
    LocalDate paymentDate,
    long outstandingBalancePaise,
    OffsetDateTime createdAt,
    UUID collectedById,
    /** Name of the staff member (accountant/cashier) who collected this payment. */
    String collectedByName
) {
    public static PaymentResponse from(FeePayment p, long studentOutstandingPaise) {
        return from(p, studentOutstandingPaise, null);
    }

    public static PaymentResponse from(FeePayment p, long studentOutstandingPaise, String collectedByName) {
        return new PaymentResponse(
            p.getId(),
            p.getStudentId(),
            p.getInvoiceId(),
            p.getAmountPaise(),
            p.getPaymentMode(),
            p.getReceiptNumber(),
            p.getReceiptPdfUrl(),
            p.getPaymentDate(),
            studentOutstandingPaise,
            p.getCreatedAt(),
            p.getCollectedById(),
            collectedByName
        );
    }
}
