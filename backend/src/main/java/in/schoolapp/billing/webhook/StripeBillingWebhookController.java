package in.schoolapp.billing.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HexFormat;
import java.util.Optional;

/**
 * Platform-level Stripe Billing webhook (slice 8). Separate from the per-fee
 * {@code /webhooks/stripe} endpoint — that one handles checkout sessions for a school's own
 * fee collection (each school's own Stripe account). This one handles the SchoolApp
 * platform's billing for SaaS subscriptions (one Stripe account, all tenants).
 *
 * <p>Mounted only when {@code app.billing.stripe.webhook-secret} is configured — keeps the
 * endpoint off in dev / when billing isn't wired yet.
 *
 * <h2>Event handling</h2>
 * <ul>
 *   <li>{@code checkout.session.completed} (mode=subscription) — stamps
 *       {@code external_customer_id} + {@code external_subscription_id} on the matching
 *       Subscription, transitions TRIAL/PAST_DUE → ACTIVE.</li>
 *   <li>{@code invoice.paid} — TRIAL/PAST_DUE/ACTIVE → ACTIVE; updates current_period_end.</li>
 *   <li>{@code invoice.payment_failed} — ACTIVE/TRIAL → PAST_DUE.</li>
 *   <li>{@code customer.subscription.deleted} — terminal CANCELLED.</li>
 * </ul>
 *
 * <p>Tenant lookup: the event's {@code customer} (Stripe Customer ID) is matched against
 * {@code subscriptions.external_customer_id}. On {@code checkout.session.completed} the row
 * is identified via the session's {@code client_reference_id} (which the checkout-link
 * builder should set to the school's UUID).
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe-billing")
@ConditionalOnProperty(name = "app.billing.stripe.webhook-secret")
public class StripeBillingWebhookController {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final long REPLAY_WINDOW_SECONDS = 300;

    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;
    private final SubscriptionService subscriptionService;

    public StripeBillingWebhookController(
        @Value("${app.billing.stripe.webhook-secret:}") String webhookSecret,
        ObjectMapper objectMapper,
        SubscriptionRepository subscriptionRepository,
        SubscriptionEventRepository eventRepository,
        SubscriptionService subscriptionService
    ) {
        // Dev / local mode: the property is bound but empty. The webhook handler refuses any
        // incoming request when this is blank, so leaving the bean instantiated is harmless.
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("app.billing.stripe.webhook-secret unset — Stripe Billing webhook will reject all calls");
        }
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
        this.objectMapper = objectMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<String> receive(
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader,
        @RequestBody String rawBody
    ) {
        if (!verifySignature(rawBody, sigHeader)) {
            log.warn("Stripe-billing webhook rejected — invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String type = text(root, "type");
            JsonNode obj = root.path("data").path("object");
            log.info("[STRIPE-BILLING-WEBHOOK] type={} stripeObject={}", type, text(obj, "id"));
            handle(type, obj);
        } catch (Exception e) {
            // Signature already verified — log + 200 so Stripe doesn't retry forever.
            log.error("Stripe-billing webhook handler failed", e);
        }
        return ResponseEntity.ok("accepted");
    }

    @Transactional
    void handle(String type, JsonNode obj) {
        if (type == null || obj == null || obj.isMissingNode()) return;
        switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(obj);
            case "invoice.paid"               -> onInvoicePaid(obj);
            case "invoice.payment_failed"     -> onInvoiceFailed(obj);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(obj);
            default -> log.debug("Stripe-billing event ignored type={}", type);
        }
    }

    // ---- Handlers ----

    private void onCheckoutCompleted(JsonNode obj) {
        // mode=subscription means this was a SaaS subscription checkout, not a per-fee one.
        String mode = text(obj, "mode");
        if (!"subscription".equals(mode)) return;
        String schoolIdStr = text(obj, "client_reference_id");
        if (schoolIdStr == null) {
            log.warn("checkout.session.completed missing client_reference_id");
            return;
        }
        java.util.UUID schoolId;
        try {
            schoolId = java.util.UUID.fromString(schoolIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Bad client_reference_id: {}", schoolIdStr);
            return;
        }

        Subscription sub = subscriptionRepository.findBySchoolId(schoolId).orElse(null);
        if (sub == null) {
            log.warn("checkout.session.completed for unknown school={}", schoolId);
            return;
        }
        sub.setExternalCustomerId(text(obj, "customer"));
        sub.setExternalSubscriptionId(text(obj, "subscription"));
        // Active from now until next billing event sets current_period_end.
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            String from = sub.getStatus().name();
            sub.setStatus(SubscriptionStatus.ACTIVE);
            writeEvent(sub, "SUBSCRIPTION_STARTED", from, "ACTIVE",
                "Stripe checkout completed customer=" + sub.getExternalCustomerId());
        }
        subscriptionRepository.save(sub);
    }

    private void onInvoicePaid(JsonNode obj) {
        String customerId = text(obj, "customer");
        Optional<Subscription> opt = findByCustomer(customerId);
        if (opt.isEmpty()) return;
        Subscription sub = opt.get();

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            String from = sub.getStatus().name();
            sub.setStatus(SubscriptionStatus.ACTIVE);
            writeEvent(sub, "PAYMENT_SUCCEEDED", from, "ACTIVE",
                "invoice.paid id=" + text(obj, "id"));
        }
        // Push the period window — Stripe gives period_end on the invoice's lines.
        long periodEnd = longOr(obj, "period_end", 0);
        if (periodEnd > 0) {
            sub.setCurrentPeriodEnd(OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(periodEnd), ZoneOffset.UTC));
        }
        subscriptionRepository.save(sub);
    }

    private void onInvoiceFailed(JsonNode obj) {
        String customerId = text(obj, "customer");
        Optional<Subscription> opt = findByCustomer(customerId);
        if (opt.isEmpty()) return;
        Subscription sub = opt.get();
        if (sub.getStatus() == SubscriptionStatus.PAST_DUE) return;  // idempotent

        String from = sub.getStatus().name();
        sub.setStatus(SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(sub);
        writeEvent(sub, "PAYMENT_FAILED", from, "PAST_DUE",
            "invoice.payment_failed id=" + text(obj, "id"));
    }

    private void onSubscriptionDeleted(JsonNode obj) {
        String customerId = text(obj, "customer");
        Optional<Subscription> opt = findByCustomer(customerId);
        if (opt.isEmpty()) return;
        subscriptionService.cancel(opt.get().getSchoolId(),
            "Stripe customer.subscription.deleted id=" + text(obj, "id"));
    }

    // ---- helpers ----

    private Optional<Subscription> findByCustomer(String customerId) {
        if (customerId == null) return Optional.empty();
        // Single linear scan over a typically-small population is fine for slice 8; add an
        // index on subscriptions.external_customer_id if this grows past 10k tenants.
        return subscriptionRepository.findAll().stream()
            .filter(s -> customerId.equals(s.getExternalCustomerId()))
            .findFirst();
    }

    private void writeEvent(Subscription sub, String type, String from, String to, String note) {
        SubscriptionEvent ev = new SubscriptionEvent();
        ev.setSchoolId(sub.getSchoolId());
        ev.setSubscriptionId(sub.getId());
        ev.setEventType(type);
        ev.setFromStatus(from);
        ev.setToStatus(to);
        ev.setNote(note);
        eventRepository.save(ev);
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
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > REPLAY_WINDOW_SECONDS) return false;

        String payload = ts + "." + body;
        String expected = hmac(payload, webhookSecret);
        for (String candidate : v1s) {
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
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
        if (node == null) return null;
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    private static long longOr(JsonNode node, String field, long fallback) {
        if (node == null) return fallback;
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? fallback : f.asLong(fallback);
    }
}
