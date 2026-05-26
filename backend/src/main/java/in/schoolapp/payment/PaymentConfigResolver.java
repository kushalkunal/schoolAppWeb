package in.schoolapp.payment;

import in.schoolapp.payment.config.PaymentProperties;
import in.schoolapp.tenantconfig.ProviderConcern;
import in.schoolapp.tenantconfig.ProviderType;
import in.schoolapp.tenantconfig.TenantProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective payment-gateway credentials for a tenant. Mirror of
 * {@code WhatsAppConfigResolver}: tenant DB row first, JVM-global env fallback.
 *
 * <p>Each school typically has its own Stripe / Razorpay account because:
 * <ul>
 *   <li>Settlement bank account is school-specific.</li>
 *   <li>KYC, GST registration, and PCI compliance ride on the school's legal entity.</li>
 *   <li>Refund + dispute liability stays with the school, not the platform.</li>
 * </ul>
 * So per-tenant config is the common case, not the exception. This resolver is the contract
 * the gateway impls use at request time.
 *
 * <p>Returned credentials are DECRYPTED — never log them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfigResolver {

    private final TenantProviderConfigService tenantConfigService;
    private final PaymentProperties globalProps;

    public Optional<StripeCreds> resolveStripe(UUID schoolId) {
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.PAYMENT);
            if (perTenant.isPresent() && ProviderType.STRIPE.equals(perTenant.get().provider())) {
                Object sk  = perTenant.get().config().get("secret_key");
                Object whs = perTenant.get().config().get("webhook_secret");
                if (sk instanceof String s && !s.isBlank()) {
                    return Optional.of(new StripeCreds(
                        s,
                        whs instanceof String w ? w : null,
                        perTenant.get().id()
                    ));
                }
            }
        }
        // JVM-global fallback.
        if (globalProps.stripe() != null
            && globalProps.stripe().secretKey() != null
            && !globalProps.stripe().secretKey().isBlank()) {
            return Optional.of(new StripeCreds(
                globalProps.stripe().secretKey(),
                globalProps.stripe().webhookSecret(),
                null
            ));
        }
        return Optional.empty();
    }

    public Optional<RazorpayCreds> resolveRazorpay(UUID schoolId) {
        if (schoolId != null) {
            var perTenant = tenantConfigService.getActive(schoolId, ProviderConcern.PAYMENT);
            if (perTenant.isPresent() && ProviderType.RAZORPAY.equals(perTenant.get().provider())) {
                Object id  = perTenant.get().config().get("key_id");
                Object sec = perTenant.get().config().get("key_secret");
                Object whs = perTenant.get().config().get("webhook_secret");
                if (id instanceof String i && sec instanceof String s
                    && !i.isBlank() && !s.isBlank()) {
                    return Optional.of(new RazorpayCreds(i, s,
                        whs instanceof String w ? w : null,
                        perTenant.get().id()));
                }
            }
        }
        if (globalProps.razorpay() != null
            && globalProps.razorpay().keyId() != null
            && globalProps.razorpay().keySecret() != null) {
            return Optional.of(new RazorpayCreds(
                globalProps.razorpay().keyId(),
                globalProps.razorpay().keySecret(),
                globalProps.razorpay().webhookSecret(),
                null
            ));
        }
        return Optional.empty();
    }

    public record StripeCreds(String secretKey, String webhookSecret, UUID configId) {}
    public record RazorpayCreds(String keyId, String keySecret, String webhookSecret, UUID configId) {}
}
