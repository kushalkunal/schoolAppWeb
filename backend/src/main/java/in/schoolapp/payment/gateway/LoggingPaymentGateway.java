package in.schoolapp.payment.gateway;

import in.schoolapp.payment.PaymentGateway;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dev default. Returns a deterministic fake URL and logs the request so the fee-reminder flow
 * is end-to-end-testable without Stripe/Razorpay credentials. Active when
 * {@code app.payment.provider} is unset or set to {@code LOGGING}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingPaymentGateway implements PaymentGateway {

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest req) {
        String ref = "dev-link-" + UUID.randomUUID();
        String url = String.format("https://pay.schoolapp.in/dev/%s/%s?amount=%d&ref=%s",
            req.tenantId(), req.studentId(), req.amountPaise(), ref);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(
            req.validFor() == null ? java.time.Duration.ofDays(7) : req.validFor());

        log.info("[PAYMENT-LINK-DEV] ref={} tenant={} student={} amountPaise={} url={}",
            ref, req.tenantId(), req.studentId(), req.amountPaise(), url);

        return new PaymentLink(ref, url, req.amountPaise(),
            req.currency() == null ? "INR" : req.currency(), expiresAt);
    }
}
