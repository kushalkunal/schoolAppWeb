# Third-party integrations

Every external service is abstracted behind an interface with a dev-default logging implementation.
Going from dev to prod is (a) create an account, (b) set two or three env vars, (c) restart — no
code changes. This file walks each provider: account setup → credentials → webhook URL → env vars
→ signature-verification contract → smoke test.

---

## Quick map

| Concern | Free-tier provider | Config prefix | Env switch |
|---|---|---|---|
| WhatsApp | WATI (~₹1,500/mo starter) | `app.whatsapp.*` | `WHATSAPP_PROVIDER=WATI` |
| Email | Gmail SMTP (500/day free) · SES · Mailgun · SendGrid | `app.email.*` + `spring.mail.*` | `EMAIL_PROVIDER=SMTP` |
| Payments | Razorpay (India) · Stripe (global) | `app.payment.*` | `PAYMENT_PROVIDER=STRIPE\|RAZORPAY` |
| File storage | Cloudflare R2 · Backblaze B2 · MinIO · AWS S3 | `app.storage.*` | `STORAGE_PROVIDER=S3` |
| OCR | Google Cloud Vision (1 k pages/mo free) | `app.migration.ocr.*` | `OCR_PROVIDER=GOOGLE_CLOUD_VISION` |
| LLM | Gemini (15 rpm / 1500 req/day free) · OpenAI · Anthropic | `app.migration.llm.*` | `LLM_PROVIDER=OPENAI\|ANTHROPIC\|GEMINI` |

All selections are independent. A fully free-tier prod stack is viable: Gemini + Google Vision +
Razorpay + R2 + Gmail + WATI.

---

## WhatsApp (WATI)

### Account

1. Sign up at [wati.io](https://wati.io/). Business verification needs a company PAN + domain.
2. Complete Meta Business verification through WATI's dashboard (3–7 days).
3. Buy a dedicated WhatsApp Business number through WATI (cannot be an existing personal number).
4. **API Access** → copy the **API Endpoint** + **Access Token**.

### Template registration

Pre-approve these in WATI **Templates**:

| Name | Category | Variables |
|---|---|---|
| `absence_notification` | UTILITY | `{parent}`, `{student}`, `{class}`, `{date}`, `{school}` |
| `fee_receipt` | UTILITY | `{school}`, `{student}`, `{class}`, `{receipt}`, `{amount}`, `{date}`, `{balance}`, `{url}` |
| `fee_reminder` | MARKETING | `{parent}`, `{amount}`, `{student}`, `{class}`, `{due}`, `{link}`, `{school}` |
| `report_card_ready` | UTILITY | `{student}`, `{exam}`, `{pct}`, `{rank}`, `{total}`, `{url}`, `{school}` |
| `otp` | UTILITY | `{otp}` |

Current dispatch goes through [`MessageTemplateService`](../backend/src/main/java/in/schoolapp/communication/template/MessageTemplateService.java)
(free-text); once Meta approves the templates above, switch to WATI's `/sendTemplateMessage`.

### Webhook

WATI **Webhooks**:

```
POST https://<your-app-domain>/webhooks/whatsapp
Header: X-WA-Signature: <hex HMAC-SHA256>
Secret: <generate a 64-char random string>
```

### Env vars

```bash
WHATSAPP_PROVIDER=WATI
WATI_BASE_URL=https://live-mt-server.wati.io/<account-id>
WATI_TOKEN=<access-token-from-wati-dashboard>
WHATSAPP_WEBHOOK_SECRET=<your-64-char-random>
```

Corresponding config keys: `app.whatsapp.provider`, `app.whatsapp.wati.base-url`,
`app.whatsapp.wati.token`, `app.whatsapp.webhook-secret`.

### Signature contract

[`WhatsAppWebhookController`](../backend/src/main/java/in/schoolapp/communication/webhook/WhatsAppWebhookController.java):

- **Header:** `X-WA-Signature`
- **Algorithm:** HMAC-SHA256 over the raw request body
- **Key:** `app.whatsapp.webhook-secret`
- **Compare:** constant-time (`MessageDigest.isEqual`) against hex-encoded expected

Payload shape follows Meta Cloud API's `whatsapp_business_account` entry. Delivery-status events
(`sent / delivered / read / failed`) transition `notification_log` rows; inbound messages route
into [`WhatsAppInboxRoutingService`](../backend/src/main/java/in/schoolapp/communication/WhatsAppInboxRoutingService.java).

### Smoke test

Set `WHATSAPP_PROVIDER=WATI`, request an OTP, verify a real WhatsApp message lands. Logs show
`[WATI-SENT type=OTP]`.

---

## Email (SMTP)

Any SMTP server works. Recommended: Gmail (500/day free) or SES.

### Gmail setup

1. Dedicated Gmail account.
2. Enable 2FA on that account.
3. Generate an **App Password** (Google Account → Security → App passwords). 16-char string.
4. Use the app password as `SMTP_PASSWORD` — **not** the account password.

### Env vars

```bash
EMAIL_PROVIDER=SMTP
EMAIL_FROM=noreply@yourschool.in
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-app-email@gmail.com
SMTP_PASSWORD=xxxx-xxxx-xxxx-xxxx   # the 16-char app password
SMTP_AUTH=true
SMTP_STARTTLS=true
```

These map to `app.email.provider`, `app.email.from`, and Spring's standard `spring.mail.host` /
`port` / `username` / `password` / `properties.mail.smtp.*`. The
[`SmtpEmailSender`](../backend/src/main/java/in/schoolapp/communication/dispatcher/SmtpEmailSender.java)
bean only loads when `app.email.provider=SMTP`; Spring's `MailSenderAutoConfiguration` only wires
`JavaMailSender` when `spring.mail.host` is non-empty, so leaving host blank in dev cleanly
disables email entirely.

### Smoke test

Set `SIGNUP_CHANNEL=EMAIL`, sign up with an email, request OTP — Gmail inbox should see the
delivery within seconds.

---

## Payments — Stripe (global)

### Account

1. [stripe.com](https://stripe.com/) → sign up. Test mode works anywhere; live mode needs KYC.
2. **Developers → API keys** → copy **Secret key** (`sk_test_...` or `sk_live_...`).

### Webhook

**Developers → Webhooks → Add endpoint:**

```
Endpoint URL: https://<your-app-domain>/webhooks/stripe
Events to listen for:
  checkout.session.completed
  checkout.session.expired
  charge.refunded
```

Copy the **Signing secret** (`whsec_...`).

### Env vars

```bash
PAYMENT_PROVIDER=STRIPE
STRIPE_SECRET_KEY=sk_test_51...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Maps to `app.payment.provider=STRIPE`, `app.payment.stripe.secret-key`,
`app.payment.stripe.webhook-secret`.

### Metadata propagation

[`StripePaymentGateway`](../backend/src/main/java/in/schoolapp/payment/gateway/StripePaymentGateway.java)
writes `metadata[tenant_id]` + `metadata[student_id]` on each Checkout Session. These survive
round-trip through the webhook so `PaymentEventListener` can route the resulting `FeePayment`.

### Signature contract

[`StripeWebhookController`](../backend/src/main/java/in/schoolapp/payment/webhook/StripeWebhookController.java):

- **Header:** `Stripe-Signature: t=<unix ts>,v1=<hex>,v1=<hex>,...`
- **Algorithm:** HMAC-SHA256 over `"<ts>.<raw body>"`
- **Key:** `app.payment.stripe.webhook-secret`
- **Replay guard:** reject if `|now - ts| > 300 s`
- **Rotation:** accept if any `v1=` candidate matches (Stripe rotates secrets with overlap)
- **Compare:** constant-time

Events → `PaymentEvent`: `checkout.session.completed → PAID`, `checkout.session.expired → FAILED`,
`charge.refunded → REFUNDED`. Unhandled types get a 200 and are ignored.

The webhook controller only mounts when `app.payment.provider=STRIPE`; its constructor fails fast
if the webhook secret isn't set.

### Test flow

1. Signup + login in your app.
2. Create an invoice + a fee reminder → logs show
   `[STRIPE-LINK-CREATED] ref=cs_test_... url=https://checkout.stripe.com/...`.
3. Open the URL, pay with test card `4242 4242 4242 4242` / any future expiry / any CVC.
4. Stripe calls your webhook → logs show
   `[STRIPE-WEBHOOK] type=checkout.session.completed status=PAID amountPaise=... tenant=...`.
5. `PaymentEventListener` creates a `FeePayment`; `ReceiptDeliveryListener` dispatches the receipt.

### Local webhook testing

```bash
stripe login
stripe listen --forward-to localhost:8080/webhooks/stripe
# Prints a whsec_... — use as STRIPE_WEBHOOK_SECRET locally.
```

---

## Payments — Razorpay (India)

### Why Razorpay over Stripe in India

- UPI support — ~95 % of consumer payments route via PhonePe / GPay / Paytm
- Lower fees (~2 % vs Stripe's 2.9 % + ₹2)
- India-resident settlement
- Domestic KYC flow already done

### Account

1. [razorpay.com](https://razorpay.com/). Business KYC 1–3 days.
2. **Account & Settings → API Keys → Generate Test Key** (or Live Key post-KYC).
3. Copy **Key Id** (`rzp_test_...`) + **Key Secret**.

### Webhook

**Settings → Webhooks → Add Webhook:**

```
URL: https://<your-app-domain>/webhooks/razorpay
Active Events:
  payment_link.paid
  payment_link.expired
  payment.refunded
  refund.created
Secret: <generate a 32-char random>
```

### Env vars

```bash
PAYMENT_PROVIDER=RAZORPAY
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=<the secret you set above>
```

Maps to `app.payment.razorpay.key-id`, `key-secret`, `webhook-secret`.

### Notes propagation

[`RazorpayPaymentGateway`](../backend/src/main/java/in/schoolapp/payment/gateway/RazorpayPaymentGateway.java)
writes `notes.tenant_id` + `notes.student_id` on each Payment Link — same round-trip discipline as
Stripe's `metadata`.

### Signature contract

[`RazorpayWebhookController`](../backend/src/main/java/in/schoolapp/payment/webhook/RazorpayWebhookController.java):

- **Header:** `X-Razorpay-Signature`
- **Algorithm:** HMAC-SHA256 over the raw request body (no timestamp prefix)
- **Key:** `app.payment.razorpay.webhook-secret`
- **Compare:** constant-time against hex-encoded expected
- **Replay window:** Razorpay does not timestamp signatures — we rely on TLS + HMAC alone. (Stripe's
  5-min replay window is a Stripe-specific protection.)

Events → `PaymentEvent`: `payment_link.paid → PAID`, `payment_link.expired → FAILED`,
`payment.refunded` / `refund.created → REFUNDED`.

Mounts only when `app.payment.provider=RAZORPAY`; constructor fails fast on missing secret.

### Test flow

1. Signup + login.
2. Create invoice + fee reminder → `[RAZORPAY-LINK-CREATED] ref=plink_... url=https://rzp.io/i/...`.
3. Open the link, pay with Razorpay test UPI `success@razorpay`.
4. `[RAZORPAY-WEBHOOK] event=payment_link.paid status=PAID amountPaise=... tenant=...`.

---

## File storage — S3-compatible

Any S3-compatible endpoint works — the
[`S3FileStorageService`](../backend/src/main/java/in/schoolapp/storage/S3FileStorageService.java)
uses the MinIO SDK under the hood, which speaks generic S3. Recommended hosts:

| Host | Free tier | Why |
|---|---|---|
| **Cloudflare R2** | 10 GB forever, **zero egress** | Huge win when serving PDFs to parents repeatedly |
| Backblaze B2 | 10 GB + 1 GB/day egress | Close second if R2 is unavailable |
| MinIO self-hosted | unlimited (your metal) | Air-gapped deployments |
| AWS S3 | 12-month free tier only | Standard fallback |

### Cloudflare R2 setup

1. [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up) — free.
2. **R2 → Purchase R2** (free tier auto-applied).
3. **R2 → Create bucket** (name e.g. `schoolapp-production`; location `auto`).
4. **R2 → Manage API Tokens → Create API Token** — permission "Object Read & Write" on the bucket.
5. Copy **Access Key ID**, **Secret Access Key**, **Endpoint** (ends `.r2.cloudflarestorage.com`).

### Env vars

```bash
STORAGE_PROVIDER=S3
S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
S3_REGION=auto                      # auto for R2; us-east-1 for AWS; us-west-000 for B2
S3_BUCKET=schoolapp-production
S3_ACCESS_KEY_ID=<from token>
S3_SECRET_ACCESS_KEY=<from token>
# Optional — set only if the bucket is public and you mapped a CDN subdomain:
# S3_PUBLIC_BASE_URL=https://cdn.yourschool.in
# Optional — set for MinIO / older S3 servers that need path-style URLs:
# S3_FORCE_PATH_STYLE=true
```

Maps to `app.storage.provider=S3`, `app.storage.s3.endpoint`, `region`, `bucket`, `access-key-id`,
`secret-access-key`, `public-base-url`, `force-path-style`.

### Public buckets

If you want receipts readable at stable URLs (no presigning):

1. **Bucket → Settings → Public Access → Allow**, map a CDN subdomain.
2. Set `S3_PUBLIC_BASE_URL=https://cdn.yourschool.in` — `S3FileStorageService` skips presigning.

### Smoke test

Trigger a quick-collect that generates a receipt → log shows
`receiptPdfUrl=https://<public or presigned R2 URL>`; the URL opens the PDF.

---

## OCR — Google Cloud Vision

### Free tier

1 000 pages / month on `DOCUMENT_TEXT_DETECTION`; ~₹1.50 / 1 000 pages after.

### Account

1. [console.cloud.google.com](https://console.cloud.google.com/) → create project (e.g. `schoolapp-ocr`).
2. **APIs & Services → Enable APIs** → enable **Cloud Vision API**.
3. **Billing** → link a billing account (required even for free-tier usage).
4. **APIs & Services → Credentials → Create credentials → API key**. Restrict it (HTTP referrers
   or IP).
5. Copy the key — 39-char string starting with `AIza`.

### Env vars

```bash
OCR_PROVIDER=GOOGLE_CLOUD_VISION
GOOGLE_VISION_API_KEY=AIza...
# Optional — default DOCUMENT_TEXT_DETECTION is better for handwritten registers:
# GOOGLE_VISION_FEATURE=DOCUMENT_TEXT_DETECTION
```

Maps to `app.migration.ocr.provider`, `app.migration.ocr.google-cloud-vision.api-key`,
`app.migration.ocr.google-cloud-vision.feature-type`. The
[`GoogleCloudVisionOcrProvider`](../backend/src/main/java/in/schoolapp/migration/ocr/GoogleCloudVisionOcrProvider.java)
bean only loads on `GOOGLE_CLOUD_VISION`.

### Smoke test

`POST /api/v1/tenants/{id}/migration` with `type=FEE_RECEIPT` + a scanned page. Logs show
`[OCR-VISION] extracted bytes=... chars=...`. `GET /migration/{jobId}` transitions to `REVIEW`
with real extracted text.

---

## LLM

Three interchangeable providers — pick ONE via `app.migration.llm.provider`. Exactly one of the
Spring beans loads at a time.

### OpenAI

1. [platform.openai.com](https://platform.openai.com/) → sign up → **Billing** (minimum $10 top-up).
2. **API Keys → Create new secret key**, copy `sk-...` (unviewable later).

```bash
LLM_PROVIDER=OPENAI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini            # ~$0.15 / 1M in, ~$0.60 / 1M out
```

### Anthropic (Claude)

Best structured-JSON output.

1. [console.anthropic.com](https://console.anthropic.com/) → sign up → **Billing**.
2. **API Keys → Create Key**, copy `sk-ant-...`.

```bash
LLM_PROVIDER=ANTHROPIC
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-haiku-4-5-20251001   # bump to claude-sonnet-4-6 for more accuracy
```

### Google Gemini (best free tier)

1. [aistudio.google.com/apikey](https://aistudio.google.com/apikey) — no billing account needed.
2. Copy `AIza...`.

```bash
LLM_PROVIDER=GEMINI
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-1.5-flash       # free tier eligible; gemini-1.5-pro paid for accuracy
```

15 requests/minute, 1 500 requests/day on `gemini-1.5-flash` is enough to run most small schools
free.

### Property names

All three share `app.migration.llm.provider` + a nested block:

- `app.migration.llm.openai.{api-key, model}`
- `app.migration.llm.anthropic.{api-key, model}`
- `app.migration.llm.gemini.{api-key, model}`

### Smoke test (any provider)

With `OCR_PROVIDER=LOGGING` (canned text) + `LLM_PROVIDER=GEMINI` (real), the pipeline hits the
real LLM with mock OCR text — faster + cheaper to iterate than a full OCR + LLM round-trip. Log
signatures:

- `[LLM-OPENAI type=FEE_RECEIPT model=gpt-4o-mini responseChars=...]`
- `[LLM-ANTHROPIC type=FEE_RECEIPT model=claude-haiku-... responseChars=...]`
- `[LLM-GEMINI type=FEE_RECEIPT model=gemini-1.5-flash responseChars=...]`

Then `GET /migration/{jobId}` returns real LLM-extracted records.

---

## Webhook URLs summary

Point dashboards at these — the server only mounts the endpoint when its provider is active:

| Provider | URL | Header | Algorithm | Secret env | Replay window |
|---|---|---|---|---|---|
| WATI | `https://<domain>/webhooks/whatsapp` | `X-WA-Signature` | HMAC-SHA256 on raw body | `WHATSAPP_WEBHOOK_SECRET` | — |
| Stripe | `https://<domain>/webhooks/stripe` | `Stripe-Signature: t=…,v1=…` | HMAC-SHA256 on `"<ts>.<body>"` | `STRIPE_WEBHOOK_SECRET` | 5 min |
| Razorpay | `https://<domain>/webhooks/razorpay` | `X-Razorpay-Signature` | HMAC-SHA256 on raw body | `RAZORPAY_WEBHOOK_SECRET` | — |

All three use `MessageDigest.isEqual` (constant-time) for the final compare.

Stripe's and Razorpay's controllers are `@ConditionalOnProperty` — only mounted when their
provider is active. When switching from Stripe to Razorpay, remove the Stripe webhook in the
dashboard first, or their retries will hit a 404 until DNS propagates.

---

## End-to-end "all prod" config

An example `.env.prod` for an Indian school running fully on paid providers:

```bash
# Core
DATABASE_URL=jdbc:postgresql://db.prod:5432/schoolapp
DATABASE_USER=schoolapp
DATABASE_PASSWORD=<strong>
REDIS_HOST=redis.prod
JWT_SECRET=<64-char random>
OTP_HMAC_SECRET=<32-char random>

# Dispatchers
WHATSAPP_PROVIDER=WATI
WATI_BASE_URL=https://live-mt-server.wati.io/123456
WATI_TOKEN=<redacted>
WHATSAPP_WEBHOOK_SECRET=<64-char random>

EMAIL_PROVIDER=SMTP
EMAIL_FROM=noreply@sunshinepublic.in
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=app@sunshinepublic.in
SMTP_PASSWORD=<gmail app password>

# Payments — Razorpay (India-first school)
PAYMENT_PROVIDER=RAZORPAY
RAZORPAY_KEY_ID=rzp_live_...
RAZORPAY_KEY_SECRET=<redacted>
RAZORPAY_WEBHOOK_SECRET=<redacted>

# Storage — Cloudflare R2
STORAGE_PROVIDER=S3
S3_ENDPOINT=https://abc123.r2.cloudflarestorage.com
S3_REGION=auto
S3_BUCKET=sunshine-schoolapp
S3_ACCESS_KEY_ID=<redacted>
S3_SECRET_ACCESS_KEY=<redacted>

# OCR + LLM
OCR_PROVIDER=GOOGLE_CLOUD_VISION
GOOGLE_VISION_API_KEY=AIza...
LLM_PROVIDER=ANTHROPIC
ANTHROPIC_API_KEY=sk-ant-...

# Logging
SPRING_PROFILES_ACTIVE=json
APP_LOG_LEVEL=INFO

# Signup
SIGNUP_CHANNEL=BOTH
```

---

## Credential security

- **Never commit env vars.** `.gitignore` already excludes `.env` files.
- **Store production secrets in a vault** — AWS Secrets Manager, Doppler, Railway secret manager, etc.
- **Rotation order on compromise** (fastest first):
  1. WhatsApp webhook secret (re-set in WATI + restart app — drops in-flight attacker events)
  2. `JWT_SECRET` (invalidates all access tokens; users re-login)
  3. Provider API keys (rotate in provider dashboard + restart)
