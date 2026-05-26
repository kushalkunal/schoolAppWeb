package in.schoolapp.communication.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.communication.NotificationLogger;
import in.schoolapp.communication.WhatsAppInboxRoutingService;
import in.schoolapp.communication.dispatcher.config.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/**
 * Delivery-status + inbound-message webhook endpoint for the WhatsApp BSP (WATI / Interakt /
 * Meta Cloud API). Signature verification uses HMAC-SHA256 with {@code app.whatsapp.webhook-secret}.
 * <p>
 * Slice 6.5 wires two things on top of the raw parse that already existed:
 * <ul>
 *   <li>Delivery-status events flow into {@link NotificationLogger} — the original dispatch's
 *       log row transitions QUEUED → SENT → DELIVERED → READ (or → FAILED).</li>
 *   <li>Inbound messages flow into {@link WhatsAppInboxRoutingService} — routed to the class
 *       teacher's inbox for the parent who sent them.</li>
 * </ul>
 * Payload shape follows Meta Cloud API's {@code whatsapp_business_account} entry →
 * {@code changes} → {@code value.statuses[]} / {@code value.messages[]}. WATI and Interakt
 * forward largely-compatible shapes.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private static final String HMAC_ALG = "HmacSHA256";

    private final WhatsAppProperties props;
    private final ObjectMapper objectMapper;
    private final NotificationLogger notificationLogger;
    private final WhatsAppInboxRoutingService inboxRoutingService;

    @PostMapping
    public ResponseEntity<String> receive(
        @RequestHeader(value = "X-WA-Signature", required = false) String signature,
        @RequestBody String rawBody
    ) {
        if (!verifySignature(rawBody, signature)) {
            log.warn("WhatsApp webhook rejected — invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            for (DeliveryStatusEvent e : parseStatuses(rawBody)) {
                applyStatus(e);
            }
            for (InboundMessage m : parseInbound(rawBody)) {
                log.info("[WA-WEBHOOK-INBOUND] msgId={} from={} bytes={}",
                    m.messageId(), m.fromPhone(), m.body() == null ? 0 : m.body().length());
                inboxRoutingService.route(m.messageId(), m.fromPhone(), m.body());
            }
        } catch (Exception e) {
            // Webhooks must always 200 on successful verification — otherwise the BSP retries
            // indefinitely. Log and swallow body-parse errors.
            log.error("WhatsApp webhook parse failed", e);
        }
        return ResponseEntity.ok("accepted");
    }

    /**
     * Meta Cloud API reports status values as {@code sent | delivered | read | failed}. We
     * translate those into the log-row transitions; unknown values are ignored (a future BSP
     * might introduce new states and we don't want to break on them).
     */
    private void applyStatus(DeliveryStatusEvent e) {
        log.info("[WA-WEBHOOK-STATUS] msgId={} status={} recipient={} ts={}",
            e.messageId(), e.status(), e.recipient(), e.timestamp());
        if (e.messageId() == null || e.status() == null) return;
        OffsetDateTime at = parseEpochSeconds(e.timestamp());
        switch (e.status().toLowerCase()) {
            case "sent" -> {
                // Most dispatches are marked SENT inline by the auditing decorator; this is a
                // safety net for providers that only emit the "sent" ack via webhook.
            }
            case "delivered" -> notificationLogger.markDelivered(e.messageId(), at);
            case "read" -> notificationLogger.markRead(e.messageId(), at);
            case "failed" -> notificationLogger.markFailedByWaMessageId(
                e.messageId(), "BSP reported failed at " + (at == null ? "unknown" : at));
            default -> log.debug("Ignoring unknown WA status: {}", e.status());
        }
    }

    private static OffsetDateTime parseEpochSeconds(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(Long.parseLong(ts.trim())), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean verifySignature(String body, String signature) {
        if (signature == null || signature.isBlank() || body == null) return false;
        String secret = props.webhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("app.whatsapp.webhook-secret is not configured — rejecting webhook");
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expected);
            // Constant-time compare — guards against timing-based signature forgery.
            return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    private List<DeliveryStatusEvent> parseStatuses(String rawBody) throws Exception {
        List<DeliveryStatusEvent> out = new ArrayList<>();
        JsonNode root = objectMapper.readTree(rawBody);
        for (JsonNode entry : safeArray(root.get("entry"))) {
            for (JsonNode change : safeArray(entry.get("changes"))) {
                JsonNode value = change.get("value");
                if (value == null) continue;
                for (JsonNode st : safeArray(value.get("statuses"))) {
                    out.add(new DeliveryStatusEvent(
                        textField(st, "id"),
                        textField(st, "status"),
                        textField(st, "recipient_id"),
                        textField(st, "timestamp")
                    ));
                }
            }
        }
        return out;
    }

    private List<InboundMessage> parseInbound(String rawBody) throws Exception {
        List<InboundMessage> out = new ArrayList<>();
        JsonNode root = objectMapper.readTree(rawBody);
        for (JsonNode entry : safeArray(root.get("entry"))) {
            for (JsonNode change : safeArray(entry.get("changes"))) {
                JsonNode value = change.get("value");
                if (value == null) continue;
                for (JsonNode msg : safeArray(value.get("messages"))) {
                    JsonNode text = msg.get("text");
                    out.add(new InboundMessage(
                        textField(msg, "id"),
                        textField(msg, "from"),
                        text == null ? null : textField(text, "body")
                    ));
                }
            }
        }
        return out;
    }

    private static Iterable<JsonNode> safeArray(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        return () -> {
            Iterator<JsonNode> it = node.elements();
            return it;
        };
    }

    private static String textField(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    public record DeliveryStatusEvent(String messageId, String status, String recipient, String timestamp) {}
    public record InboundMessage(String messageId, String fromPhone, String body) {}
}
