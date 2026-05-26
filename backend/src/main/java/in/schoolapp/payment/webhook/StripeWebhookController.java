package in.schoolapp.payment.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.payment.config.PaymentProperties;
import in.schoolapp.payment.dto.PaymentEvent;
import in.schoolapp.payment.dto.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;

/**
 * Receives Stripe events. Signature verification follows
 * <a href="https://docs.stripe.com/webhooks#verify-manually">Stripe's manual verification guide</a>:
 * <ul>
 *   <li>Header: {@code Stripe-Signature: t=<ts>,v1=<hex>,v1=<hex>,...}</li>
 *   <li>Signed payload: {@code "<ts>.<raw body>"}</li>
 *   <li>HMAC-SHA256 keyed by {@code app.payment.stripe.webhook-secret}</li>
 *   <li>Replay guard: reject if {@code ts} is older than 5 minutes</li>
 * </ul>
 * Only active when {@code app.payment.provider=STRIPE} — keeps the endpoint off when Stripe
 * isn't the configured gateway.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "STRIPE")
public class StripeWebhookController {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final long REPLAY_WINDOW_SECONDS = 300;

    private final PaymentProperties props;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public StripeWebhookController(PaymentProperties props, ObjectMapper objectMapper,
                                   ApplicationEventPublisher events) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.events = events;
        if (props.stripe() == null
            || props.stripe().webhookSecret() == null || props.stripe().webhookSecret().isBlank()) {
            throw new IllegalStateException(
                "app.payment.provider=STRIPE but app.payment.stripe.webhook-secret is not set");
        }
    }

    @PostMapping
    public ResponseEntity<String> receive(
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader,
        @RequestBody String rawBody
    ) {
        if (!verifySignature(rawBody, sigHeader)) {
            log.warn("Stripe webhook rejected — invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String type = text(root, "type");
            JsonNode dataObject = root.path("data").path("object");
            PaymentEvent evt = parse(type, dataObject);
            if (evt != null) {
                log.info("[STRIPE-WEBHOOK] type={} ref={} status={} amountPaise={} tenant={}",
                    type, evt.providerReference(), evt.status(), evt.amountPaise(),
                    evt.metadata().get("tenant_id"));
                events.publishEvent(evt);
            } else {
                log.debug("Stripe event ignored type={}", type);
            }
        } catch (Exception e) {
            log.error("Stripe webhook parse failed", e);
            // Still 200 — we've already verified the signature. Don't let BSP retry forever.
        }
        return ResponseEntity.ok("accepted");
    }

    private boolean verifySignature(String body, String sigHeader) {
        if (sigHeader == null || body == null) return false;
        Long ts = null;
        java.util.List<String> v1s = new java.util.ArrayList<>();
        for (String part : sigHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0].trim())) {
                try { ts = Long.parseLong(kv[1].trim()); } catch (NumberFormatException ignored) {}
            } else if ("v1".equals(kv[0].trim())) {
                v1s.add(kv[1].trim());
            }
        }
        if (ts == null || v1s.isEmpty()) return false;

        // Replay guard: reject if timestamp too old
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > REPLAY_WINDOW_SECONDS) {
            log.warn("Stripe webhook rejected — timestamp outside replay window");
            return false;
        }

        String payload = ts + "." + body;
        String expected = hmac(payload, props.stripe().webhookSecret());
        // Accept if any v1 scheme matches (Stripe may rotate secrets)
        for (String candidate : v1s) {
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps Stripe event types onto our provider-neutral {@link PaymentEvent}. Unhandled event
     * types (invoice.created, customer.updated, etc.) return null — we ack the webhook and
     * move on.
     */
    private PaymentEvent parse(String type, JsonNode obj) {
        if (type == null || obj == null || obj.isMissingNode()) return null;
        return switch (type) {
            case "checkout.session.completed" -> new PaymentEvent(
                text(obj, "id"),
                PaymentStatus.PAID,
                longOr(obj, "amount_total", 0),
                text(obj.path("payment_method_details"), "type"),
                metadata(obj)
            );
            case "checkout.session.expired" -> new PaymentEvent(
                text(obj, "id"), PaymentStatus.FAILED,
                longOr(obj, "amount_total", 0), null, metadata(obj)
            );
            case "charge.refunded" -> new PaymentEvent(
                text(obj, "id"), PaymentStatus.REFUNDED,
                longOr(obj, "amount_refunded", 0), null, metadata(obj)
            );
            default -> null;
        };
    }

    private Map<String, Object> metadata(JsonNode obj) {
        Map<String, Object> out = new HashMap<>();
        JsonNode md = obj.get("metadata");
        if (md != null && md.isObject()) {
            Iterator<String> fieldNames = md.fieldNames();
            while (fieldNames.hasNext()) {
                String f = fieldNames.next();
                out.put(f, md.get(f).asText());
            }
        }
        return out;
    }

    private static String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC error", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    private static long longOr(JsonNode node, String field, long fallback) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? fallback : f.asLong(fallback);
    }
}
