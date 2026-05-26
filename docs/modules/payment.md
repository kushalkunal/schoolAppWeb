# `payment` module

Configurable payment providers — Stripe, Razorpay, or a LOGGING dev stub. Same
`@ConditionalOnProperty` pattern as every other external dispatcher. Closes the loop between a
parent tapping a link and a WhatsApp receipt landing on their phone, with zero admin action.

**Package:** `in.schoolapp.payment`

---

## Layout

```
in.schoolapp.payment/
├── PaymentGateway                  interface (createPaymentLink contract)
├── PaymentLinkService              facade — applies currency + expiry defaults
├── PaymentEventListener            @Async @EventListener — turns verified PAID events into FeePayment rows
├── dto/
│   ├── PaymentLinkRequest          provider-neutral input (amount, currency, payer, metadata)
│   ├── PaymentLink                 provider-neutral output (url, expiresAt, providerReference)
│   ├── PaymentEvent                provider-neutral webhook event
│   └── PaymentStatus               PAID | FAILED | REFUNDED | PENDING
├── config/
│   └── PaymentProperties           LOGGING | STRIPE | RAZORPAY + per-provider nested config
├── gateway/
│   ├── LoggingPaymentGateway       @ConditionalOnProperty: LOGGING (default, matchIfMissing)
│   ├── StripePaymentGateway        @ConditionalOnProperty: STRIPE
│   └── RazorpayPaymentGateway      @ConditionalOnProperty: RAZORPAY
└── webhook/
    ├── StripeWebhookController     @ConditionalOnProperty: STRIPE   (mounts /webhooks/stripe)
    └── RazorpayWebhookController   @ConditionalOnProperty: RAZORPAY (mounts /webhooks/razorpay)
```

Only the active provider's gateway + webhook controller load. Webhook endpoints for inactive
providers aren't mounted at all — reduces attack surface.

---

## Provider switching

```yaml
app:
  payment:
    provider: LOGGING              # LOGGING | STRIPE | RAZORPAY
    default-currency: INR
    return-url: https://app.schoolapp.in/payment/success
    stripe:
      secret-key: sk_live_...
      webhook-secret: whsec_...
    razorpay:
      key-id: rzp_live_...
      key-secret: ...
      webhook-secret: ...
```

---

## `PaymentGateway` interface

```java
public interface PaymentGateway {
    PaymentLink createPaymentLink(PaymentLinkRequest request);
}
```

Minimal — consumers don't care which provider they talk to. A `PaymentLinkRequest` carries
`tenantId`, `studentId`, `amountPaise`, `currency`, `purpose`, `payer`, `returnUrl`, `validFor`; the
returned `PaymentLink` exposes `{providerReference, url, amountPaise, currency, expiresAt}`.

[`PaymentLinkService`](../../backend/src/main/java/in/schoolapp/payment/PaymentLinkService.java) is
a thin facade that applies defaults (currency = INR, validity = 7 days) and is the single call
site used by [`FeeReminderService`](../../backend/src/main/java/in/schoolapp/fee/FeeReminderService.java).
Any future caller (canteen, library fines, etc.) should go through it so the provider switch stays
in one place.

---

## Stripe gateway ([`StripePaymentGateway`](../../backend/src/main/java/in/schoolapp/payment/gateway/StripePaymentGateway.java))

- Endpoint: `POST https://api.stripe.com/v1/checkout/sessions`
- Auth: HTTP Basic (`sk_...:`)
- Body: form-encoded (Stripe's legacy convention — `line_items[0][price_data][currency]=inr`)
- Returns: `{id, url, …}` — a Checkout Session with a `url` for the browser
- **Metadata round-trip:** `metadata[tenant_id]=...` + `metadata[student_id]=...` ride through to
  the webhook so `PaymentEventListener` can route the resulting `FeePayment` correctly
- Expiry clamped to Stripe's 30 min–24 h window

---

## Razorpay gateway ([`RazorpayPaymentGateway`](../../backend/src/main/java/in/schoolapp/payment/gateway/RazorpayPaymentGateway.java))

- Endpoint: `POST https://api.razorpay.com/v1/payment_links`
- Auth: HTTP Basic (`keyId:keySecret`)
- Body: JSON with `{amount, currency, customer, notes, notify, expire_by}`
- Returns: `{id: "plink_...", short_url: "https://rzp.io/i/..."}` — UPI + card link
- **Notes round-trip:** `notes.tenant_id`, `notes.student_id` — same correlation pattern as Stripe
- India-first: native UPI (PhonePe, GPay, Paytm) + ~2% fees vs Stripe's 2.9%

---

## Webhooks

### Stripe ([`StripeWebhookController`](../../backend/src/main/java/in/schoolapp/payment/webhook/StripeWebhookController.java))

**URL:** `POST /webhooks/stripe`

**Header:** `Stripe-Signature: t=<ts>,v1=<hex>,v1=<hex>,...`

Verification follows [Stripe's manual guide](https://docs.stripe.com/webhooks#verify-manually):

1. Build the signed payload `"{timestamp}.{raw body}"`.
2. HMAC-SHA256 keyed by `app.payment.stripe.webhook-secret`.
3. **Replay guard** — reject if `|now - ts| > 300 s`.
4. Accept if ANY `v1=` candidate matches — Stripe may rotate secrets, so multiple signatures land during overlap.
5. Constant-time compare via `MessageDigest.isEqual`.

Parsed events → `PaymentEvent`:

| Stripe type | `PaymentStatus` |
|---|---|
| `checkout.session.completed` | `PAID` |
| `checkout.session.expired` | `FAILED` |
| `charge.refunded` | `REFUNDED` |

Unhandled types (invoice.created, customer.updated, …) return `null` — we 200 the webhook and move
on.

The controller's constructor refuses to start the app if `app.payment.provider=STRIPE` but the
webhook secret isn't set — misconfiguration fails at boot, not at the first inbound call.

### Razorpay ([`RazorpayWebhookController`](../../backend/src/main/java/in/schoolapp/payment/webhook/RazorpayWebhookController.java))

**URL:** `POST /webhooks/razorpay`

**Header:** `X-Razorpay-Signature: <hex>`

Simpler scheme — HMAC-SHA256 of the raw body keyed by `app.payment.razorpay.webhook-secret`,
constant-time compare.

Parsed events → `PaymentEvent`:

| Razorpay event | `PaymentStatus` | Source node |
|---|---|---|
| `payment_link.paid` | `PAID` | `payload.payment_link.entity` |
| `payment_link.expired` | `FAILED` | `payload.payment_link.entity` |
| `payment.refunded` / `refund.created` | `REFUNDED` | `payload.payment.entity` |

---

## Closing the loop — `PaymentEventListener`

Both webhook controllers translate their provider shape into a neutral
[`PaymentEvent`](../../backend/src/main/java/in/schoolapp/payment/dto/PaymentEvent.java) and publish
it via `ApplicationEventPublisher`.

[`PaymentEventListener`](../../backend/src/main/java/in/schoolapp/payment/PaymentEventListener.java)
runs `@Async("notificationExecutor") @EventListener`:

1. Drop non-PAID events (FAILED / REFUNDED / PENDING are logged and ignored — refund reconciliation
   is a future slice).
2. Parse `tenant_id` + `student_id` UUIDs out of `event.metadata()`. Missing or malformed → log an
   operator-action message and drop, never crash.
3. Reject non-positive amounts.
4. Call
   [`FeePaymentService.createOnlinePayment(tenantId, studentId, amountPaise, providerReference, paymentMethod)`](../../backend/src/main/java/in/schoolapp/fee/FeePaymentService.java).
5. Swallow and log any exception — the webhook has already 200'd the BSP, so throwing helps
   nothing.

`createOnlinePayment` is **idempotent on `provider_reference`**: a second webhook delivery for the
same session returns the existing `FeePayment` instead of inserting a duplicate. The idempotency
guarantee comes from a partial unique index added in
[V3__notification_webhook_correlation.sql](../../backend/src/main/resources/db/migration/V3__notification_webhook_correlation.sql):

```sql
ALTER TABLE fee_payments ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_payments_provider_reference
    ON fee_payments(provider_reference) WHERE provider_reference IS NOT NULL;
```

Creating the `FeePayment` fires `FeePaymentCreatedEvent`, which
[`ReceiptDeliveryListener`](../../backend/src/main/java/in/schoolapp/communication/event/ReceiptDeliveryListener.java)
consumes on `AFTER_COMMIT` to dispatch the WhatsApp receipt. End-to-end loop:

```
Parent taps payment link → pays at Stripe/Razorpay → BSP calls our webhook
 → signature verified → PaymentEvent published → PaymentEventListener
 → FeePaymentService.createOnlinePayment (idempotent) → FeePaymentCreatedEvent
 → ReceiptDeliveryListener → WhatsAppNotifier → parent's phone
```

No admin action anywhere.

---

## Endpoints

No business endpoints — `PaymentLinkService` is called internally by `FeeReminderService`. The two
webhook URLs above are the only HTTP surface this module exposes.

---

## Dependencies

- **Reads from `common`:** `AppException`, `ErrorCode`, `PhoneNormalizer` (E.164 normalisation for Razorpay customer payload)
- **Called by:** `fee.FeeReminderService` (link creation)
- **Calls:** `fee.FeePaymentService.createOnlinePayment` (via the listener) — which in turn publishes `FeePaymentCreatedEvent` consumed by `communication.ReceiptDeliveryListener`
- **Publishes:** `PaymentEvent` (on verified webhook); consumes it on the same bus
