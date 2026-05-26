# Configuration reference

How to configure the SchoolApp backend + ocr-service. Every env var, every per-tenant knob.

The system runs as **two services**:
- `backend/` — the main monolith (auth, students, fees, etc.) on port `8080`.
- `ocr-service/` — stateless OCR + LLM extraction (slice 5) on port `8090`.

---

## 1. Backend — required env vars

These have dev defaults but **must be set in production**:

| Env var | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/schoolapp` | RDS / Railway / Neon in prod |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `schoolapp` / `schoolapp_dev_password` | **Change** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty | Managed Redis in prod |
| `JWT_SECRET` | dev fallback (insecure) | **Required**, ≥64 random chars |
| `OTP_HMAC_SECRET` | dev fallback (insecure) | **Required**, ≥32 random chars |
| `TENANT_CONFIG_KEY` | dev fallback (insecure) | **Required**, ≥16 chars — AES key for encrypting per-tenant credentials in `tenant_provider_configs.config` |

---

## 2. Backend — feature toggles

The single global switch matters less now that slices 1–3 added per-tenant config. The
global env still controls **which dispatcher bean loads at JVM boot** (the `@ConditionalOnProperty`
gate); the per-tenant config overrides the credentials at send time.

| Env var | Allowed values | Effect |
|---|---|---|
| `WHATSAPP_PROVIDER` | `LOGGING` (default) · `WATI` | Loads the `WatiWhatsAppNotifier` bean; per-tenant `tenant_provider_configs(WHATSAPP, WATI)` overrides creds |
| `EMAIL_PROVIDER` | `LOGGING` · `SMTP` | Loads the `SmtpEmailSender` |
| `PAYMENT_PROVIDER` | `LOGGING` · `STRIPE` · `RAZORPAY` | Loads the matching gateway; per-tenant config overrides creds |
| `STORAGE_PROVIDER` | `LOCAL` (default) · `S3` | Loads file storage backend |
| `SIGNUP_CHANNEL` | `PHONE` (default) · `EMAIL` · `BOTH` | Which identifier(s) accept signup |
| `OCR_SERVICE_URL` | `http://localhost:8090` | Where the OCR microservice lives |

---

## 3. SaaS plans + features (per-tenant)

Slice 1 added a feature-flag + subscription system. Configuration is **data**, not env vars —
managed through the platform-admin endpoints (`/api/v1/platform/*`, SUPER_ADMIN only).

### Plans (seeded by Flyway V6)

| Code | Monthly (₹) | Students | Staff | Messages/mo | Storage |
|---|---|---|---|---|---|
| `FREE` | 0 | 50 | 10 | 500 | 100 MB |
| `STARTER` | 999 | 300 | 30 | 5,000 | 2 GB |
| `GROWTH` | 2,499 | 800 | 80 | 20,000 | 10 GB |
| `ENTERPRISE` | 5,999 | unlimited | unlimited | unlimited | unlimited |

### Feature keys

Stable strings in `in.schoolapp.feature.FeatureKey`. Gated controllers carry
`@RequiresFeature(KEY)`. A school's effective enabled-state = override (if present) > plan
inclusion. Categories: Core (FREE+), Standard (STARTER+), Premium (GROWTH+), Add-on (ENTERPRISE).

```
STUDENTS · ATTENDANCE · SCHOOL_PROFILE                         (Core)
FEE · ACADEMICS · COMMUNICATION_CORE · CIRCULARS · WHATSAPP_INBOX  (Standard)
ANALYTICS · SUBSTITUTE_TEACHERS · TEACHER_ASSIGNMENTS ·
EXAM_ELIGIBILITY · AUDIT_LOG                                   (Premium)
PAPER_MIGRATION · DATA_EXPORT · MOBILE_SYNC · DPDP_REQUESTS ·
PAYMENT_GATEWAY · STUDENT_DOCUMENTS                            (Add-on)
```

Configure: see [OPERATIONS.md](OPERATIONS.md#feature-overrides).

### Subscription lifecycle

Every new tenant starts on `FREE` in `TRIAL` for 14 days (configurable via
`BILLING_TRIAL_DAYS`). Transitions:

```
   TRIAL  ─── trial_ends_at passes ──▶  PAST_DUE  (auto, daily 02:00 cron)
                                             │
              platform admin / Stripe webhook │
                                             ▼
   PAST_DUE ◀──────────────────  ACTIVE ──────▶  CANCELLED
       ▲                                                ▲
       │     platform admin                             │
       └────────────────────────────────────────────────┘
                          SUSPENDED  (mutating endpoints blocked,
                                      reads still allowed)
```

The `SubscriptionGuardInterceptor` blocks `POST/PUT/PATCH/DELETE` on tenant-scoped paths when
the status is SUSPENDED or CANCELLED. Reads, auth, and platform-admin endpoints stay
reachable so a suspended tenant can resolve their billing.

---

## 4. Per-tenant provider configurations (slice 2)

Stored in the `tenant_provider_configs` table. Sensitive fields are AES-GCM-256 encrypted
with the key from `TENANT_CONFIG_KEY`. Six concerns, each with allowed provider types
(enforced by `ProviderType.allowedFor`):

| Concern | Allowed providers | Sensitive fields |
|---|---|---|
| `WHATSAPP` | `LOGGING` · `WATI` · `TWILIO_WA` · `INTERAKT` | `token`, `webhook_secret` |
| `EMAIL` | `LOGGING` · `SMTP` · `SES` · `MAILGUN` · `SENDGRID` | `password`, `api_key` |
| `PAYMENT` | `LOGGING` · `STRIPE` · `RAZORPAY` | `secret_key`, `key_secret`, `webhook_secret` |
| `STORAGE` | `LOCAL` · `S3` | `secret_access_key` |
| `OCR` | `LOGGING` · `GOOGLE_CLOUD_VISION` | `api_key` |
| `LLM` | `LOGGING` · `OPENAI` · `ANTHROPIC` · `GEMINI` | `api_key` |

Currently consumed by:
- **WhatsApp (WATI)** — `WatiWhatsAppNotifier` reads `tenant_provider_configs(WHATSAPP, WATI)` per send (slice 3).
- **Payment (Stripe + Razorpay)** — `StripePaymentGateway` and `RazorpayPaymentGateway` read per request (slice 4).

Each call falls back to JVM-global env vars if the tenant has no row. So a deployment can:
- Set `WHATSAPP_PROVIDER=WATI` + globals → all schools share one BSP.
- Or leave the globals blank + set per-tenant DB rows → each school has its own.

OCR/LLM/Email/Storage are not yet wired to consume per-tenant config (infrastructure
exists, dispatcher refactor deferred — see [OPERATIONS.md](OPERATIONS.md#known-gaps)).

---

## 5. OCR service env vars

The ocr-service is **stateless** — no DB, no Redis. Configure via env only.

| Env var | Default | Notes |
|---|---|---|
| `OCR_SERVICE_PORT` | `8090` | |
| `OCR_PROVIDER` | `LOGGING` | `LOGGING` · `GOOGLE_CLOUD_VISION` |
| `GOOGLE_VISION_API_KEY` | empty | Required when `OCR_PROVIDER=GOOGLE_CLOUD_VISION` |
| `GOOGLE_VISION_FEATURE` | `DOCUMENT_TEXT_DETECTION` | Better for handwritten registers |
| `LLM_PROVIDER` | `LOGGING` | `LOGGING` · `OPENAI` · `ANTHROPIC` · `GEMINI` |
| `OPENAI_API_KEY` / `OPENAI_MODEL` | empty / `gpt-4o-mini` | Required when `LLM_PROVIDER=OPENAI` |
| `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL` | empty / `claude-haiku-4-5-20251001` | |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | empty / `gemini-1.5-flash` | Best free tier |

---

## 6. Idempotency (slice 4c)

Clients send `Idempotency-Key: <opaque-up-to-120-chars>` on `POST` requests. The backend
captures the response and replays it on retry (same key + same body hash).

| Env var | Default | Notes |
|---|---|---|
| `app.idempotency.enabled` | `true` | Filter is on by default |
| `app.idempotency.max-body-bytes` | `65536` | Responses larger than this are not cached |

24-hour TTL hardcoded. Cleanup of expired rows is a future task.

---

## 7. Library + Transport (slices 6c/6d)

| Env var | Default | Notes |
|---|---|---|
| `app.library.fine-paise-per-day` | `200` | ₹2/day overdue fine |

---

## 8. Production checklist

Before going live:

- [ ] Set `JWT_SECRET` (64+ random chars)
- [ ] Set `OTP_HMAC_SECRET` (32+ random chars)
- [ ] Set `TENANT_CONFIG_KEY` (32+ random chars)
- [ ] Set `DATABASE_URL` + creds, run Flyway (auto on boot)
- [ ] Provision Redis, set `REDIS_HOST`
- [ ] Deploy `ocr-service` (or use the shared one), set `OCR_SERVICE_URL`
- [ ] Set at least one global provider env var per concern OR seed `tenant_provider_configs` per school
- [ ] For each school: `POST /api/v1/platform/tenants/{id}/providers/{concern}/verify` to confirm creds work
- [ ] Set `SPRING_PROFILES_ACTIVE=json` for structured logs
