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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;

/**
 * Receives Razorpay events. Signature verification per
 * <a href="https://razorpay.com/docs/webhooks/validate-test/">Razorpay's guide</a>:
 * <ul>
 *   <li>Header: {@code X-Razorpay-Signature: <hex>}</li>
 *   <li>HMAC-SHA256 of the raw body, keyed by {@code app.payment.razorpay.webhook-secret}</li>
 * </ul>
 * Only active when {@code app.payment.provider=RAZORPAY}.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/razorpay")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "RAZORPAY")
public class RazorpayWebhookController {

    private static final String HMAC_ALG = "HmacSHA256";

    private final PaymentProperties props;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public RazorpayWebhookController(PaymentProperties props, ObjectMapper objectMapper,
                                     ApplicationEventPublisher events) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.events = events;
        if (props.razorpay() == null
            || props.razorpay().webhookSecret() == null
            || props.razorpay().webhookSecret().isBlank()) {
            throw new IllegalStateException(
                "app.payment.provider=RAZORPAY but app.payment.razorpay.webhook-secret is not set");
        }
    }

    @PostMapping
    public ResponseEntity<String> receive(
        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
        @RequestBody String rawBody
    ) {
        if (!verifySignature(rawBody, signature)) {
            log.warn("Razorpay webhook rejected — invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = text(root, "event");
            PaymentEvent evt = parse(event, root);
            if (evt != null) {
                log.info("[RAZORPAY-WEBHOOK] event={} ref={} status={} amountPaise={} tenant={}",
                    event, evt.providerReference(), evt.status(), evt.amountPaise(),
                    evt.metadata().get("tenant_id"));
                events.publishEvent(evt);
            } else {
                log.debug("Razorpay event ignored event={}", event);
            }
        } catch (Exception e) {
            log.error("Razorpay webhook parse failed", e);
        }
        return ResponseEntity.ok("accepted");
    }

    private boolean verifySignature(String body, String signature) {
        if (signature == null || body == null) return false;
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                props.razorpay().webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expected);
            return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Razorpay signature verification error", e);
            return false;
        }
    }

    /**
     * Razorpay nests the domain object differently per event family. We pull {@code
     * payload.payment_link.entity} for payment_link.* events, {@code payload.payment.entity}
     * for payment.*, etc.
     */
    private PaymentEvent parse(String event, JsonNode root) {
        if (event == null) return null;
        JsonNode payload = root.path("payload");
        return switch (event) {
            case "payment_link.paid" -> {
                JsonNode link = payload.path("payment_link").path("entity");
                yield new PaymentEvent(
                    text(link, "id"),
                    PaymentStatus.PAID,
                    longOr(link, "amount_paid", 0),
                    text(payload.path("payment").path("entity"), "method"),
                    notes(link)
                );
            }
            case "payment_link.expired" -> {
                JsonNode link = payload.path("payment_link").path("entity");
                yield new PaymentEvent(text(link, "id"), PaymentStatus.FAILED,
                    longOr(link, "amount", 0), null, notes(link));
            }
            case "payment.refunded", "refund.created" -> {
                JsonNode pmt = payload.path("payment").path("entity");
                yield new PaymentEvent(text(pmt, "id"), PaymentStatus.REFUNDED,
                    longOr(pmt, "amount_refunded", 0), text(pmt, "method"), notes(pmt));
            }
            default -> null;
        };
    }

    private Map<String, Object> notes(JsonNode obj) {
        Map<String, Object> out = new HashMap<>();
        JsonNode md = obj.get("notes");
        if (md != null && md.isObject()) {
            Iterator<String> fieldNames = md.fieldNames();
            while (fieldNames.hasNext()) {
                String f = fieldNames.next();
                out.put(f, md.get(f).asText());
            }
        }
        return out;
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
