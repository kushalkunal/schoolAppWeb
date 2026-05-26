package in.schoolapp.payment;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.payment.config.PaymentProperties;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Thin facade around {@link PaymentGateway} — applies defaults (currency, expiry), validates
 * inputs, and provides a single call site for the rest of the app. When more than one caller
 * needs payment links (canteen, library fines, etc.), they all go through this service so the
 * provider switch stays at one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private static final Duration DEFAULT_VALID_FOR = Duration.ofDays(7);

    private final PaymentGateway gateway;
    private final PaymentProperties props;

    public PaymentLink createLink(UUID tenantId, UUID studentId, long amountPaise,
                                  String purpose, PaymentLinkRequest.PayerInfo payer) {
        if (amountPaise <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Amount must be positive");
        }
        PaymentLinkRequest req = new PaymentLinkRequest(
            tenantId,
            studentId,
            amountPaise,
            props.defaultCurrency() != null ? props.defaultCurrency() : "INR",
            purpose,
            payer,
            props.returnUrl(),
            DEFAULT_VALID_FOR
        );
        return gateway.createPaymentLink(req);
    }
}
