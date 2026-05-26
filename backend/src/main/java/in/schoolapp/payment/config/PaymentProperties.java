package in.schoolapp.payment.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Payment gateway configuration. Same pattern as WhatsApp/Email dispatchers — switch
 * providers via config without touching consumer code.
 * <ul>
 *   <li>{@code LOGGING}  — dev default; returns a deterministic fake URL.</li>
 *   <li>{@code STRIPE}   — Stripe Checkout Sessions via {@code api.stripe.com}.</li>
 *   <li>{@code RAZORPAY} — Razorpay Payment Links via {@code api.razorpay.com}.</li>
 * </ul>
 * Each real provider has its own nested config block; fields are only required when the
 * matching provider is active (validated inside the gateway's constructor).
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    @NotNull Provider provider,
    String defaultCurrency,
    String returnUrl,
    StripeConfig stripe,
    RazorpayConfig razorpay
) {
    public enum Provider { LOGGING, STRIPE, RAZORPAY }

    /**
     * @param secretKey         {@code sk_live_...} or {@code sk_test_...}
     * @param webhookSecret     {@code whsec_...} from the Stripe dashboard
     */
    public record StripeConfig(String secretKey, String webhookSecret) {}

    /**
     * @param keyId             {@code rzp_live_...} or {@code rzp_test_...}
     * @param keySecret         the secret paired with {@code keyId}
     * @param webhookSecret     shared secret configured on the Razorpay dashboard webhook
     */
    public record RazorpayConfig(String keyId, String keySecret, String webhookSecret) {}
}
