package in.schoolapp.fee.dto;

import java.util.List;
import java.util.UUID;

public record StudentFeeSummaryResponse(
    UUID studentId,
    long totalOutstandingPaise,
    long totalPaidPaise,
    List<InvoiceResponse> invoices,
    List<PaymentResponse> recentPayments
) {}
