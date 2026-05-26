# Operations guide

Day-to-day platform-admin tasks. Every action below requires a `SUPER_ADMIN` JWT — these
endpoints are at `/api/v1/platform/*` and skip both `TenantInterceptor` and the subscription
guard so an operator can administer suspended tenants.

---

## 1. Inspect a tenant

```bash
# All tenants, paginated
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants?page=0&size=50

# One tenant — full detail: summary + effective feature flags + usage + plan limits
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID

# Subscription event log
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/subscription/events
```

---

## 2. Change a plan

```bash
curl -X PUT \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"planCode": "GROWTH", "note": "Upgraded after pilot"}' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/subscription
```

Side effects:
- The school's effective feature set changes immediately (`FeatureFlagService` cache is
  evicted next time it's read).
- A `SubscriptionEvent` row is written: `event_type=PLAN_CHANGED, from_plan_code=..., to_plan_code=GROWTH`.
- If the school was `SUSPENDED` or `CANCELLED`, the status flips to `ACTIVE`.

---

## 3. Suspend / resume / cancel

```bash
# Suspend (e.g. non-payment, abuse) — reads still work; writes are blocked
curl -X POST \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Outstanding invoice 45 days overdue"}' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/suspend

# Resume — goes to TRIAL if trial_ends_at still in future, else ACTIVE
curl -X POST \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/resume

# Cancel — terminal state; future activity requires a new subscription record
curl -X POST \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer requested closure"}' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/cancel
```

While a tenant is `SUSPENDED`/`CANCELLED`, every `POST/PUT/PATCH/DELETE` on
`/api/v1/tenants/{id}/*` returns `403 TENANT_SUSPENDED`. Tenant can still GET (read-only
access for data export, dashboard inspection) and hit `/api/v1/auth/*` (so the principal can
log in to see the situation). Platform-admin endpoints are exempted so you can resume them.

---

## 4. Feature overrides

Each school's effective enabled-state = override (if present) > plan inclusion.

```bash
# Enable PAPER_MIGRATION for a tenant whose plan doesn't include it
curl -X PUT \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true, "note": "Pilot customer — granted by sales"}' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/features/PAPER_MIGRATION

# Disable a feature their plan would otherwise include (e.g. forcing CIRCULARS off)
curl -X PUT ... -d '{"enabled": false, "note": "Trial ended; downgrading"}' \
  .../features/CIRCULARS

# Clear the override — fall back to plan default
curl -X DELETE \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/features/PAPER_MIGRATION

# List the entire feature catalog
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/features
```

A feature-gated controller throws `403 FEATURE_DISABLED` when a tenant calls it without the
feature being enabled. Tenants see this via `GET /api/v1/tenants/{tenantId}/feature-flags`.

---

## 5. Per-tenant provider credentials

The dispatchers (WhatsApp, Stripe, Razorpay — slices 3+4) check the tenant's row first, then
fall back to JVM-global env vars. So a school can have its own WATI account while another
uses the platform's default.

```bash
# Set WhatsApp creds for a tenant
curl -X PUT \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "WATI",
    "config": {
      "base_url": "https://live-mt-server.wati.io/12345",
      "token": "live-bearer-token-here",
      "webhook_secret": "32-char-random"
    },
    "note": "Sunshine Public School BSP account"
  }' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/providers/WHATSAPP

# Set Stripe creds (sensitive values auto-encrypted with TENANT_CONFIG_KEY)
curl -X PUT ... -d '{
  "provider": "STRIPE",
  "config": {
    "secret_key": "sk_live_...",
    "webhook_secret": "whsec_..."
  }
}' .../providers/PAYMENT

# Verify creds round-trip — does a real authenticated GET to WATI (other concerns
# return "not yet verifiable" stub for now)
curl -X POST \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/providers/WHATSAPP/verify
# On success: { "success": true, "data": { "success": true, "verifiedAt": "..." } }
# On bad creds: { "success": true, "data": { "success": false, "message": "WATI returned 401: Unauthorized" } }

# Clear a tenant's per-provider config (falls back to JVM-global)
curl -X DELETE \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/providers/WHATSAPP

# List a tenant's configs (decrypted — for the platform team)
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/providers
```

School owners can see their own config (with secrets masked):

```bash
curl -H "Authorization: Bearer $SCHOOL_OWNER_TOKEN" \
  http://localhost:8080/api/v1/tenants/$SCHOOL_ID/providers
# Response: secrets shown as "********"
```

---

## 6. Run the OCR service

The OCR service is a separate Spring Boot app (slice 5) at `ocr-service/`. Build and run it
alongside the main backend:

```bash
# Build
cd ocr-service
/c/Program\ Files/apache-maven-3.9.9/bin/mvn package -DskipTests
java -jar target/ocr-service-0.1.0-SNAPSHOT.jar

# Or Docker
docker build -t schoolapp/ocr-service .
docker run -p 8090:8090 \
  -e OCR_PROVIDER=GOOGLE_CLOUD_VISION \
  -e GOOGLE_VISION_API_KEY=AIza... \
  -e LLM_PROVIDER=ANTHROPIC \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  schoolapp/ocr-service

# Then point the backend at it
OCR_SERVICE_URL=http://ocr-service:8090 java -jar backend.jar
```

Stateless POST endpoint:

```bash
curl -X POST http://localhost:8090/extract \
  -H "Content-Type: application/json" \
  -d "{
    \"jobType\": \"FEE_RECEIPT\",
    \"imageBase64\": \"$(base64 -w 0 < receipt.jpg)\"
  }"
# Response: { "records": [...], "rawText": "...", "ocrConfidence": 0.95, "recordCount": 5 }
```

The backend's `MigrationProcessor` calls this endpoint internally; you don't normally hit it
directly. Use the migration controller on the backend:

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F type=FEE_RECEIPT \
  -F file=@receipt.jpg \
  http://localhost:8080/api/v1/tenants/$SCHOOL_ID/migration
```

---

## 7. Usage counters

The `UsageService` increments counters as side effects of business events. As of slice 4:

| Event | Counter | Metric type |
|---|---|---|
| `StudentService.createStudent` | `STUDENTS_COUNT` | lifetime |
| `NotificationLogger.recordQueued` | `MESSAGES_SENT_MONTHLY` | per-month |

Plan limits are pre-flight enforced on `createStudent` (FREE-plan schools cannot create their
51st student until they upgrade). Other counters (`STORAGE_BYTES`, `STAFF_COUNT`,
`OCR_PAGES_MONTHLY`) — the table + API exist; the increment-listeners aren't wired yet.
See the GET endpoint:

```bash
curl -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID
# returns "usage": {"STUDENTS_COUNT": 42, "MESSAGES_SENT_MONTHLY": 1280, ...}
```

---

## 8. Idempotency for safe retries

Clients (mobile, BSP webhooks, scripts) can attach an `Idempotency-Key` header on `POST`s to
make retries safe:

```bash
KEY=$(uuidgen)
for attempt in 1 2 3; do
  curl -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $KEY" \
    -H "Content-Type: application/json" \
    -d '{"firstName":"Rohan","sectionId":"...","parentPhone":"9876543210"}' \
    http://localhost:8080/api/v1/tenants/$SCHOOL_ID/students
done
# Only the first attempt actually creates; the others return the cached response with
# header X-Idempotency-Replay: true
```

Same key + different body returns `409 VALIDATION_ERROR` ("key reuse with different payload").

24-hour TTL. Webhooks under `/webhooks/**` are excluded — providers do their own
idempotency on event IDs.

---

## 9. Trial expiry

Every new school lands on `FREE` in `TRIAL`. A daily cron at **02:00 IST**
(`TrialExpiryScheduler`) transitions expired trials to `PAST_DUE` (NOT `SUSPENDED` —
that's a separate, more aggressive flip done by an admin or a future Stripe webhook).

`PAST_DUE` still allows mutations — it's a soft grace state. The intent is to let billing
sort itself out without immediately breaking the school's daily operations.

---

## 10. Stripe Billing — SaaS subscription webhook (slice 8a)

Distinct from `/webhooks/stripe` (which handles per-school fee-checkout sessions). The
billing webhook handles the **platform's own** Stripe account where SchoolApp charges schools
for the SaaS subscription itself.

### Setup

1. In the **platform's** Stripe dashboard (one account, all tenants), create a webhook
   endpoint:
   - URL: `https://api.schoolapp.in/webhooks/stripe-billing`
   - Events: `checkout.session.completed`, `invoice.paid`, `invoice.payment_failed`,
     `customer.subscription.deleted`.
   - Copy the signing secret (`whsec_...`).
2. Set the env var on the backend:
   ```bash
   STRIPE_BILLING_WEBHOOK_SECRET=whsec_...
   ```
   The endpoint only mounts when this is set.
3. When sending a school to the Stripe-hosted checkout page for subscription, pass
   `client_reference_id={school_id}` on the Checkout Session — the webhook uses that to bind
   the resulting Stripe Customer ID to the local `subscriptions.external_customer_id`.

### Behaviour

| Stripe event | Subscription transition |
|---|---|
| `checkout.session.completed` (mode=subscription) | stores `external_customer_id` + `external_subscription_id`; status → ACTIVE |
| `invoice.paid` | → ACTIVE; pushes `current_period_end` |
| `invoice.payment_failed` | → PAST_DUE (starts grace clock — see §11) |
| `customer.subscription.deleted` | → CANCELLED (terminal) |

Every transition writes a `subscription_events` row for audit.

## 11. Grace period scheduler (slice 8b)

Daily 03:00 IST cron transitions `PAST_DUE` subscriptions to `SUSPENDED` once they've been
PAST_DUE for `app.billing.grace-days` days (default `7`). Set `BILLING_GRACE_DAYS=0` to
disable auto-suspension.

The clock starts on the latest `SubscriptionEvent` with `toStatus=PAST_DUE`. If Stripe
retries within the grace window and the next invoice succeeds, the status flips back to
ACTIVE and the clock resets.

## 12. Per-tenant Email (slice 8c)

`SmtpEmailSender` now reads creds via `EmailConfigResolver`:
- Tenant DB row → use those (allows per-school sender domain like
  `noreply@sunshinepublic.in`).
- Else JVM-global `spring.mail.*` env vars.
- Else log + skip (no email sent).

Per-tenant config:

```bash
curl -X PUT \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "SMTP",
    "config": {
      "host": "smtp.gmail.com",
      "port": 587,
      "username": "school@sunshinepublic.in",
      "password": "<16-char Gmail app password>",
      "from": "Sunshine Public <school@sunshinepublic.in>"
    }
  }' \
  http://localhost:8080/api/v1/platform/tenants/$SCHOOL_ID/providers/EMAIL
```

Per-(host, port, username) `JavaMailSenderImpl` instances are cached so steady creds reuse
the same client. OTP dispatches before login (no `TenantContext`) automatically fall back to
the JVM-global SMTP config.

## 13. Per-tenant Storage (slice 8d — partial)

`StorageConfigResolver` infrastructure exists for per-tenant S3 buckets, but the
`S3FileStorageService` refactor that consumes it is deferred. Today every tenant shares one
bucket and namespaces by key path (`receipts/{tenantId}/{paymentId}.pdf`). Enterprise
tenants requiring their own bucket / KMS key need the dispatcher refactor before that ships.

---

## 14. Transactional outbox (slice 9c)

Durable replacement for in-process `ApplicationEventPublisher` async events. Pattern:

1. Business code calls `OutboxService.publish(schoolId, eventObject)` **inside** its transaction.
2. A row lands in `outbox_events` atomically with the business write — rollback drops both.
3. `OutboxPoller` runs every 5 seconds, picks up pending rows (where `next_attempt_at <= now()`),
   deserialises the payload to the original event class, and calls
   `ApplicationEventPublisher.publishEvent(...)` — existing `@EventListener` / `@Async` listeners
   pick it up unchanged.
4. On failure: increment `attempts`, exponential backoff (capped at 10 min), give up after 10 tries.

The poller is single-instance assumption — multiple JVMs would double-deliver. For HA add
`FOR UPDATE SKIP LOCKED` on the query or use a leader-election strategy.

## 15. OpenTelemetry tracing (slice 9b)

Wired via Micrometer-Tracing + OTLP exporter. Disabled by default (no exporter endpoint set);
turn on by pointing at a collector:

```bash
# Sample 10% in prod, 100% in dev
TRACING_SAMPLE_RATE=0.1
# Any OTLP-compliant collector — Jaeger, Tempo, Honeycomb, Datadog, etc.
OTLP_ENDPOINT=http://otel-collector:4318
```

Traces include the HTTP request span and any downstream HTTP calls (RestClient). Tracing IDs
flow into the Logback MDC so `traceId` shows up in structured logs.

## 16. Idempotency cleanup cron (slice 9a)

`IdempotencyCleanupScheduler` runs daily at 04:00 IST and deletes `idempotency_keys` rows whose
`expires_at < now()`. The 24-hour TTL means a day's traffic accumulates between sweeps; without
this job the table grows unbounded.

## 17. Postgres RLS — deferred (slice 9e)

Production-grade defense-in-depth wasn't shipped in slice 9. To implement properly:

1. Flyway migration: `ALTER TABLE <each-tenant-scoped-table> ENABLE ROW LEVEL SECURITY` +
   `CREATE POLICY` keyed on `current_setting('app.current_school_id')::uuid`.
2. A Spring AOP advice that runs `SET LOCAL app.current_school_id = '<uuid>'` at the start of every
   `@Transactional` method when `TenantContext.getTenantId()` is non-null.
3. App DB user must not have `BYPASSRLS` (PostgreSQL superusers ignore policies).
4. Integration-test infrastructure needs the GUC set before any tenant query — adds harness work.

Service-layer `school_id` filtering today provides the correct behaviour; RLS catches the rare
service bug where someone forgets the filter. Real but bounded — it's a slice on its own.

---

## 18. Known gaps (deferred work)

After slice 11:

- **Postgres Row-Level Security** — deferred (see §17 for the proper plan).
- **OCR + LLM per-tenant credentials** — ocr-service would need to fetch per-tenant secrets
  from the backend over HTTP. Not wired.
- **Storage byte counter wiring** — counter API exists, no listener increments it.
- **Outbox poller single-instance** — second JVM would double-deliver. Add `FOR UPDATE SKIP
  LOCKED` before going multi-instance.
- **Frontend feature breadth** — only foundation pages (login / signup / dashboard) are
  built; ~40 more pages spec'd in [`docs/frontend/`](frontend/) waiting to be slotted in.
- **Mobile app** — sync API is shipped; React Native app from
  [`Analysis/PHASE1_FRONTEND_PLAN.md`](../Analysis/PHASE1_FRONTEND_PLAN.md) is unbuilt.

What's **shipped** as of slice 11:

- Per-tenant configurable concerns: WhatsApp, Payment, Email, Storage (all 4 dispatchers
  consume `*ConfigResolver`).
- Plan-limit enforcement on student create + message-sent counter wired.
- Stripe Billing webhook + grace-period auto-suspension cron.
- OpenTelemetry tracing (off by default; flip via `OTLP_ENDPOINT`).
- Transactional outbox for durable async event delivery.
- Idempotency-Key support on `POST /api/v1/**` with daily TTL sweep.
- Web admin scaffold + login + signup + dashboard (Next.js, npm-built).
