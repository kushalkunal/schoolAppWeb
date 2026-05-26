package in.schoolapp.fee;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.dto.RefundRequest;
import in.schoolapp.fee.dto.RefundResponse;
import in.schoolapp.fee.entity.FeeAdjustment;
import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.FeePayment;
import in.schoolapp.fee.entity.InvoiceStatus;
import in.schoolapp.fee.repository.FeeAdjustmentRepository;
import in.schoolapp.fee.repository.FeeInvoiceRepository;
import in.schoolapp.fee.repository.FeePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reverses a fee payment. The flow:
 *
 * <ol>
 *   <li>Validate that the payment belongs to this tenant and the refund amount ≤ original.</li>
 *   <li>Decrease {@code FeeInvoice.amount_paid_paise} by the refund amount; reset status
 *       from PAID/PARTIAL back to PARTIAL/PENDING (or to REFUNDED if a full reversal of a
 *       previously fully-paid invoice).</li>
 *   <li>Record a {@code FeeAdjustment} of type REFUND with the payment id and reason.</li>
 *   <li>Annotate the payment with a {@code refunded_*} timestamp / amount (Slice 15 leaves
 *       these on FeeAdjustment + service-derived for now; the entity stays untouched).</li>
 * </ol>
 *
 * Partial refunds are supported — caller specifies {@code amountPaise} ≤ original payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeRefundService {

    private final FeePaymentRepository paymentRepository;
    private final FeeInvoiceRepository invoiceRepository;
    private final FeeAdjustmentRepository adjustmentRepository;

    @Transactional
    public RefundResponse refund(UUID tenantId, UUID paymentId, RefundRequest req) {
        FeePayment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found"));
        if (!payment.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found");
        }

        long refundAmount = req.amountPaise() != null && req.amountPaise() > 0
            ? req.amountPaise()
            : payment.getAmountPaise();  // full refund if amount not supplied

        long alreadyRefunded = adjustmentRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
            .filter(a -> a.getAdjustmentType() == FeeAdjustment.AdjustmentType.REFUND)
            .mapToLong(a -> -a.getAmountPaise())   // negative-sign convention
            .sum();
        long refundable = payment.getAmountPaise() - alreadyRefunded;
        if (refundAmount > refundable) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_EXCEEDS_DUE,
                "Refund amount " + refundAmount + " exceeds refundable " + refundable);
        }

        // 1. Walk back the invoice's amount_paid_paise.
        FeeInvoice inv = invoiceRepository.findById(payment.getInvoiceId())
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Invoice not found"));
        inv.setAmountPaidPaise(inv.getAmountPaidPaise() - refundAmount);
        if (inv.getAmountPaidPaise() <= 0) {
            inv.setAmountPaidPaise(0);
            inv.setStatus(alreadyRefunded + refundAmount >= payment.getAmountPaise()
                ? InvoiceStatus.REFUNDED
                : InvoiceStatus.PENDING);
        } else {
            inv.setStatus(InvoiceStatus.PARTIAL);
        }
        invoiceRepository.save(inv);

        // 2. Audit row (amount_paise is negative — reduces invoice balance).
        FeeAdjustment adj = new FeeAdjustment();
        adj.setSchoolId(tenantId);
        adj.setInvoiceId(inv.getId());
        adj.setPaymentId(payment.getId());
        adj.setAdjustmentType(FeeAdjustment.AdjustmentType.REFUND);
        adj.setAmountPaise(-refundAmount);
        adj.setReason(req.reason() != null ? req.reason() : "Refund");
        adj = adjustmentRepository.save(adj);

        log.info("Refund tenant={} payment={} amount={} reason=\"{}\"",
            tenantId, paymentId, refundAmount, adj.getReason());

        return new RefundResponse(
            adj.getId(), payment.getId(), inv.getId(),
            refundAmount, inv.getStatus(), adj.getCreatedAt());
    }
}
