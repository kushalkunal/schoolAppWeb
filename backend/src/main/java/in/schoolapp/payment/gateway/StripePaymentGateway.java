package in.schoolapp.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.payment.PaymentConfigResolver;
import in.schoolapp.payment.PaymentGateway;
import in.schoolapp.payment.config.PaymentProperties;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stripe Checkout Sessions — <a href="https://docs.stripe.com/api/checkout/sessions/create">Stripe API</a>.
 *
 * <p><strong>Slice 4 change:</strong> credentials are resolved per request via
 * {@link PaymentConfigResolver} — tenant DB row first, JVM-global env fallback. The
 * constructor-time validation moved to runtime so this bean can load with no global creds and
 * serve schools that have per-tenant Stripe accounts. Each school's settlement account is
 * Stripe-side, so per-tenant credentials are the common case in production.
 *
 * <p>Per-{@code secretKey} {@link RestClient}s are cached so steady creds reuse the same client;
 * rotating creds create a fresh one.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "STRIPE")
public class StripePaymentGateway implements PaymentGateway {

    private static final String STRIPE_API_BASE = "https://api.stripe.com";
    private static final Duration MIN_EXPIRY = Duration.ofMinutes(30);
    private static final Duration MAX_EXPIRY = Duration.ofHours(24);

    private final RestClient.Builder builder;
    private final PaymentProperties props;
    private final ObjectMapper objectMapper;
    private final PaymentConfigResolver configResolver;

    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public StripePaymentGateway(RestClient.Builder builder, PaymentProperties props,
                                ObjectMapper objectMapper, PaymentConfigResolver configResolver) {
        this.builder = builder;
        this.props = props;
        this.objectMapper = objectMapper;
        this.configResolver = configResolver;
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest req) {
        PaymentConfigResolver.StripeCreds creds = configResolver.resolveStripe(req.tenantId())
            .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "No Stripe credentials available for tenant " + req.tenantId()
                    + " — set per-tenant config or app.payment.stripe.secret-key"));
        RestClient client = clientFor(creds.secretKey());

        Duration expiry = clamp(req.validFor() == null ? MAX_EXPIRY : req.validFor(),
            MIN_EXPIRY, MAX_EXPIRY);
        long expiresAtEpoch = Instant.now().plus(expiry).getEpochSecond();
        String currency = (req.currency() == null ? "INR" : req.currency()).toLowerCase();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", req.returnUrl() != null ? req.returnUrl() : defaultReturnUrl());
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][product_data][name]",
            req.purpose() != null ? req.purpose() : "School Fees");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(req.amountPaise()));
        form.add("line_items[0][quantity]", "1");
        form.add("expires_at", String.valueOf(expiresAtEpoch));
        form.add("metadata[tenant_id]", req.tenantId().toString());
        form.add("metadata[student_id]", req.studentId().toString());
        if (req.payer() != null && req.payer().email() != null && !req.payer().email().isBlank()) {
            form.add("customer_email", req.payer().email());
        }

        try {
            String response = client.post()
                .uri("/v1/checkout/sessions")
                .body(form)
                .retrieve()
                .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            String id = text(node, "id");
            String url = text(node, "url");
            if (url == null || id == null) {
                throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Stripe response missing id/url");
            }
            log.info("[STRIPE-LINK-CREATED] ref={} tenant={} student={} amountPaise={}",
                id, req.tenantId(), req.studentId(), req.amountPaise());
            return new PaymentLink(id, url, req.amountPaise(), currency.toUpperCase(),
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), ZoneOffset.UTC));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe createPaymentLink failed tenant={} student={} — {}",
                req.tenantId(), req.studentId(), e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Payment link creation failed", e);
        }
    }

    private RestClient clientFor(String secretKey) {
        String key = Integer.toHexString(secretKey.hashCode());
        return clientCache.computeIfAbsent(key, k -> {
            String basic = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return builder
                .baseUrl(STRIPE_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
        });
    }

    private String defaultReturnUrl() {
        return props.returnUrl() != null && !props.returnUrl().isBlank()
            ? props.returnUrl() : "https://app.schoolapp.in/payment/success";
    }

    private static Duration clamp(Duration v, Duration min, Duration max) {
        if (v.compareTo(min) < 0) return min;
        if (v.compareTo(max) > 0) return max;
        return v;
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }
}
