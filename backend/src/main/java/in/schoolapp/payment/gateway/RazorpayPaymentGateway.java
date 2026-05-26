package in.schoolapp.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.payment.PaymentConfigResolver;
import in.schoolapp.payment.PaymentGateway;
import in.schoolapp.payment.dto.PaymentLink;
import in.schoolapp.payment.dto.PaymentLinkRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Razorpay Payment Links — <a href="https://razorpay.com/docs/api/payments/payment-links/">API docs</a>.
 *
 * <p><strong>Slice 4 change:</strong> credentials resolved per request via
 * {@link PaymentConfigResolver}; per-tenant Razorpay accounts are the production norm in
 * India (each school does their own KYC + settlement).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "RAZORPAY")
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com";
    private static final Duration MIN_EXPIRY = Duration.ofMinutes(15);

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final PaymentConfigResolver configResolver;

    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public RazorpayPaymentGateway(RestClient.Builder builder, ObjectMapper objectMapper,
                                  PaymentConfigResolver configResolver) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.configResolver = configResolver;
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest req) {
        PaymentConfigResolver.RazorpayCreds creds = configResolver.resolveRazorpay(req.tenantId())
            .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "No Razorpay credentials available for tenant " + req.tenantId()
                    + " — set per-tenant config or app.payment.razorpay.key-id/key-secret"));
        RestClient client = clientFor(creds.keyId(), creds.keySecret());

        Duration expiry = req.validFor() == null || req.validFor().compareTo(MIN_EXPIRY) < 0
            ? Duration.ofDays(7) : req.validFor();
        long expireByEpoch = Instant.now().plus(expiry).getEpochSecond();
        String currency = (req.currency() == null ? "INR" : req.currency()).toUpperCase();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", req.amountPaise());
        body.put("currency", currency);
        body.put("accept_partial", false);
        body.put("description", req.purpose() != null ? req.purpose() : "School Fees");
        body.put("expire_by", expireByEpoch);
        body.put("reminder_enable", true);

        if (req.payer() != null) {
            Map<String, Object> customer = new HashMap<>();
            if (req.payer().name()  != null) customer.put("name", req.payer().name());
            if (req.payer().phone() != null) customer.put("contact", PhoneNormalizer.toE164(req.payer().phone()));
            if (req.payer().email() != null) customer.put("email", req.payer().email());
            if (!customer.isEmpty()) body.put("customer", customer);
        }

        Map<String, Object> notes = new HashMap<>();
        notes.put("tenant_id", req.tenantId().toString());
        notes.put("student_id", req.studentId().toString());
        body.put("notes", notes);

        try {
            String response = client.post()
                .uri("/v1/payment_links")
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            String id = text(node, "id");
            String url = text(node, "short_url");
            if (id == null || url == null) {
                throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Razorpay response missing id/short_url");
            }
            log.info("[RAZORPAY-LINK-CREATED] ref={} tenant={} student={} amountPaise={}",
                id, req.tenantId(), req.studentId(), req.amountPaise());
            return new PaymentLink(id, url, req.amountPaise(), currency,
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(expireByEpoch), ZoneOffset.UTC));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Razorpay createPaymentLink failed tenant={} student={} — {}",
                req.tenantId(), req.studentId(), e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "Payment link creation failed", e);
        }
    }

    private RestClient clientFor(String keyId, String keySecret) {
        String cacheKey = Integer.toHexString((keyId + ":" + keySecret).hashCode());
        return clientCache.computeIfAbsent(cacheKey, k -> {
            String basic = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            return builder
                .baseUrl(RAZORPAY_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }
}
