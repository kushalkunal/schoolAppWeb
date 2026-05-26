package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.InvoiceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundResponse(
    UUID adjustmentId,
    UUID paymentId,
    UUID invoiceId,
    long refundedPaise,
    InvoiceStatus newInvoiceStatus,
    OffsetDateTime refundedAt
) {}
