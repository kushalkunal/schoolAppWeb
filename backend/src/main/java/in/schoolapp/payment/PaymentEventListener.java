package in.schoolapp.payment;

import in.schoolapp.fee.FeePaymentService;
import in.schoolapp.payment.dto.PaymentEvent;
import in.schoolapp.payment.dto.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Closes the loop between a successful online payment and our own books. A verified
 * {@link PaymentEvent} of status {@code PAID} is translated into a {@code FeePayment} row via
 * {@link FeePaymentService#createOnlinePayment}, which in turn triggers the standard
 * WhatsApp-receipt delivery flow.
 * <p>
 * Non-PAID events are logged and dropped — FAILED/REFUNDED/PENDING don't create rows in this
 * listener. Refund handling will land as part of the dispute/reconciliation Slice.
 * <p>
 * Runs async on the notification executor so the webhook controller's HTTP response to the BSP
 * stays fast — receipt PDF generation is ~100-500ms and shouldn't block the webhook ack.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final FeePaymentService feePaymentService;

    @Async("notificationExecutor")
    @EventListener
    public void onPaymentEvent(PaymentEvent event) {
        if (event.status() != PaymentStatus.PAID) {
            log.debug("Ignoring non-PAID payment event ref={} status={}",
                event.providerReference(), event.status());
            return;
        }

        Map<String, Object> md = event.metadata();
        UUID tenantId = parseUuid(md, "tenant_id");
        UUID studentId = parseUuid(md, "student_id");
        if (tenantId == null || studentId == null) {
            log.error("Dropping PAID event ref={} — metadata missing tenant_id/student_id. "
                    + "Operator action required: manually record payment.",
                event.providerReference());
            return;
        }
        if (event.amountPaise() <= 0) {
            log.error("Dropping PAID event ref={} — non-positive amount {}",
                event.providerReference(), event.amountPaise());
            return;
        }

        try {
            feePaymentService.createOnlinePayment(
                tenantId, studentId, event.amountPaise(),
                event.providerReference(), event.paymentMethod());
        } catch (Exception e) {
            // Never propagate — the webhook has already 200'd the BSP. The row may or may not
            // have been written; idempotency on providerReference protects a manual retry.
            log.error("Failed to record online payment ref={} tenant={} student={} — {}",
                event.providerReference(), tenantId, studentId, e.getMessage(), e);
        }
    }

    private static UUID parseUuid(Map<String, Object> md, String key) {
        if (md == null) return null;
        Object v = md.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
