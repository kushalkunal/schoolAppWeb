# SaaS architecture (slices 1–6)

What's been built on top of the original feature-complete monolith. Slice-by-slice with
the design rationale for each, plus the cross-cutting picture.

---

## High-level picture

```
                    ┌──────────────────────────────────────────┐
                    │            Platform Admin UI             │
                    │  (SUPER_ADMIN only — manages all tenants)│
                    └─────────────────┬────────────────────────┘
                                      │  JWT (SUPER_ADMIN)
                                      ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │                          BACKEND  (port 8080)                   │
   │ ┌─────────────────────────────────────────────────────────────┐ │
   │ │  School/Student/Attendance/Fee/Academics/...  (slice 0)     │ │
   │ │  + Communication · Payment · Storage · Migration · Audit    │ │
   │ │  + Sync · Export · Analytics                                │ │
   │ ├─────────────────────────────────────────────────────────────┤ │
   │ │  SaaS spine (slices 1–4):                                   │ │
   │ │    feature/      RequiresFeature aspect, FeatureFlagService │ │
   │ │    billing/      Plans · Subscriptions · UsageCounters      │ │
   │ │    platform/     /api/v1/platform/* (SUPER_ADMIN only)      │ │
   │ │    tenantconfig/ AES-GCM per-tenant credentials             │ │
   │ │    common/idempotency/  Idempotency-Key filter              │ │
   │ ├─────────────────────────────────────────────────────────────┤ │
   │ │  Domain modules added in slice 6:                           │ │
   │ │    timetable/ · homework/ · library/ · transport/           │ │
   │ └─────────────────────────────────────────────────────────────┘ │
   │                  │            │              │                  │
   │              PostgreSQL    Redis        local/S3 storage         │
   └─────────────┬────────────────────────────────┬───────────────────┘
                 │  RestClient                    │  RestClient
                 ▼  /extract                      ▼  /api/v1/sendSessionMessage, etc
   ┌────────────────────────────────┐    ┌────────────────────────────────┐
   │  OCR-SERVICE  (port 8090,      │    │   External providers           │
   │  slice 5 — own JVM,            │    │   WATI · Stripe · Razorpay     │
   │  stateless, no DB)             │    │   R2/S3 · Google Vision        │
   │                                │    │   OpenAI/Anthropic/Gemini      │
   │  OCR → LLM → records JSON      │    │                                │
   └────────────────────────────────┘    └────────────────────────────────┘
```

---

## Slice 1 — SaaS core: feature flags + plans + platform admin

**Why:** every tenant-scoped controller method needed a runtime check for "is this feature
enabled for this school?" — beyond Spring Security's role check. And the platform team needed
endpoints to manage subscriptions across tenants without entering a tenant's context.

### Tables (Flyway V6)

- `plans` — catalog: FREE, STARTER, GROWTH, ENTERPRISE.
- `plan_features` — link rows: which features each plan includes.
- `plan_limits` — per-(plan, metric) ceilings, `-1` = unlimited.
- `features` — feature catalog with `default_enabled`.
- `feature_overrides` — per-school override row that wins over the plan.
- `subscriptions` — one row per school; `(status, plan_id, trial_ends_at, ...)`.
- `subscription_events` — append-only audit of state transitions.
- `usage_counters` — `(school_id, metric, period_key) → count`.

### Code surface

- `feature.FeatureKey` — string constants for ~19 toggleable features.
- `feature.FeatureFlagService.isEnabled(schoolId, key)` — override > plan inclusion, cached
  in-memory.
- `feature.@RequiresFeature("KEY")` + `feature.FeatureCheckAspect` — AOP enforcement at the
  controller method level.
- `billing.SubscriptionService.startTrialForSchool` (called by signup),
  `.changePlan / .suspend / .resume / .cancel` — each writes a `SubscriptionEvent`.
- `billing.SubscriptionGuardInterceptor` — blocks POST/PUT/PATCH/DELETE on suspended
  tenants, registered as a `@Bean` (not `@Component`) so slice tests stay green.
- `billing.usage.UsageService` — atomic Postgres `ON CONFLICT` increment + `enforceLimit`.
- `platform.PlatformAdminController` — `/api/v1/platform/*` with `@PreAuthorize("hasRole('SUPER_ADMIN')")`.

---

## Slice 2 — Per-tenant provider configs

**Why:** WhatsApp via WATI, Stripe creds, R2 buckets — different schools need different
credentials. A single `WHATSAPP_PROVIDER=WATI` env var across the whole JVM doesn't model
that. We needed per-(school, concern) configurations with secrets encrypted at rest.

### Table (Flyway V7) — `tenant_provider_configs`

One row per `(school_id, concern)`. `concern` is one of `WHATSAPP / EMAIL / PAYMENT / STORAGE
/ OCR / LLM`. `provider` is the constant string (`WATI`, `STRIPE`, …). `config` is a JSONB
map; sensitive keys (`token`, `secret_key`, `webhook_secret`, `password`, `api_key`, …) are
AES-GCM-256 encrypted via `SecretCipher` before write.

### Encryption

- `tenantconfig.SecretCipher` — AES-GCM 256-bit, 12-byte nonce per value. Wire format:
  `"enc:v1:" || base64(nonce || ciphertext+tag)`. Idempotent on already-encrypted values.
- Key from `app.tenantconfig.encryption-key` env, SHA-256-derived to 32 bytes — any ≥16-char
  input accepted but production should use a long random value or KMS-issued secret.

### Service surface

- `TenantProviderConfigService.set(schoolId, concern, provider, config, ...)` — encrypts
  sensitive fields then upserts.
- `.getActive(schoolId, concern)` → `ResolvedConfig` with **decrypted** values for in-process
  dispatcher beans.
- `.getActiveMasked(schoolId, concern)` → secrets shown as `********` for school-owner reads.
- `ProviderType.allowedFor(concern)` — rejects `concern=PAYMENT, provider=GEMINI` etc. at
  write time.

### Platform endpoints

`PUT/GET/DELETE /api/v1/platform/tenants/{id}/providers[/{concern}]` (SUPER_ADMIN only,
decrypted view). `GET /api/v1/tenants/{tenantId}/providers` (school-owner, masked).

---

## Slice 3 — Dispatcher consumes per-tenant config (WhatsApp)

**Why:** slice 2 built the storage; slice 3 made the WhatsApp pipeline actually read from it.

- `communication.dispatcher.WhatsAppConfigResolver` — returns `Optional<WatiCreds>` for a
  `schoolId`. Order: tenant row → JVM env → empty.
- `WatiWhatsAppNotifier` refactored: drops constructor-time cred validation, looks up creds
  per send via the resolver. Per-(baseUrl, token) `RestClient` instances cached in a
  `ConcurrentHashMap` so rotating creds creates a fresh client.
- `tenantconfig.ProviderVerificationService` — `POST /platform/.../providers/{concern}/verify`
  does a real authenticated GET against WATI's `/getMessageTemplates` to confirm creds.
  Stamps `verified_at` on success, `last_error` on failure.

---

## Slice 4 — Dispatcher pattern for Payment, usage listeners, Idempotency-Key

### 4a — Payment per-tenant creds

`PaymentConfigResolver.resolveStripe(schoolId)` / `.resolveRazorpay(schoolId)`. Both
gateway impls refactored to consult the resolver per request — each school's Stripe or
Razorpay account is its own (settlement banks differ, KYC differs).

### 4b — Usage wired

`UsageService.increment(schoolId, MESSAGES_SENT_MONTHLY, 1)` now fires from
`NotificationLogger.recordQueued` on every queued WhatsApp send. `StudentService.createStudent`
pre-flight calls `enforceLimit(STUDENTS_COUNT)` (so a FREE plan blocks the 51st student) and
increments the counter after the audit log. Both use `ObjectProvider<UsageService>` so slice
tests without the billing module stay green.

### 4c — Idempotency-Key

- Flyway V8: `idempotency_keys` table.
- `IdempotencyFilter` (Servlet filter, `OncePerRequestFilter`) — only kicks on POST requests
  to `/api/**` with the `Idempotency-Key` header set. SHA-256 of the body; same key + same
  body → replay cached response with `X-Idempotency-Replay: true`. Same key + different body →
  `409 VALIDATION_ERROR`.
- 24-hour TTL hardcoded. Webhook paths excluded (providers handle their own dedup on event IDs).

---

## Slice 5 — OCR extracted into its own service

**Why:** OCR + LLM are the longest-tail operations in the backend (Google Vision: 5–30 s;
LLM: 1–10 s). Running them in-process pins the `ocrExecutor` pool on every upload and
couples the main JVM's stability to Vision's. Extracting them means we can scale OCR
horizontally, rate-limit it per-tenant, and recover from a Vision outage without restarting
the main backend.

### Repo layout

```
SM/
├── backend/                      ← existing main monolith
│   └── (migration/ocr/ and migration/llm/ packages DELETED)
└── ocr-service/                  ← NEW — sibling Maven project
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/in/schoolapp/ocrservice/
        ├── OcrServiceApplication.java
        ├── ExtractController.java   (POST /extract)
        ├── ExtractionService.java
        ├── JobType (mirror of MigrationJobType — no shared classpath)
        ├── ocr/   (OcrProvider + LoggingOcrProvider + GoogleCloudVisionOcrProvider)
        └── llm/   (LlmExtractionProvider + 4 impls + PromptTemplates + LlmJsonParser)
```

### Contract

Stateless HTTP. The backend posts the image (base64) + jobType; the OCR service runs OCR +
LLM and returns `{records: [...], rawText, ocrConfidence, recordCount}`. No DB, no Redis.

### Backend changes

- `migration/ocr/` and `migration/llm/` packages **deleted**.
- `ExtractedRecord` moved to `migration/dto/` (was `migration/llm/dto/`).
- `MigrationProcessor` rewritten — calls `OcrServiceClient.extract(jobType, imageBytes)`
  instead of in-process providers.
- `OcrServiceClient` (HTTP client) — wraps RestClient against `app.migration.service.base-url`.
- Application.yml drops `app.migration.ocr.*` + `app.migration.llm.*` (those move to ocr-service).

### Deployment options

- Same machine: run both JARs on different ports.
- Docker compose: two services, OCR scaled independently.
- Kubernetes: separate Deployments + HPAs; the OCR Deployment can have its own resource
  limits + GPU node selector if you ever swap to a self-hosted OCR model.

---

## Slice 6 — Domain modules: Timetable, Homework, Library, Transport

Minimal-viable CRUD per module. Each lands as: entities → repositories → service →
controller → Flyway migration.

| Module | Migration | Entities | Highlights |
|---|---|---|---|
| Timetable | V9 | `timetable_periods`, `timetable_entries` | Section × DoW × period → (subject, teacher); teacher's daily schedule lookup |
| Homework | V10 | `homework_assignments`, `homework_submissions` | Per-section assignments; per-student submissions (upsert on resubmit); grade + remark |
| Library | V11 | `library_books`, `library_issues` | Catalog with `available_copies` decrement; issue/return; per-day overdue fine accrued on return |
| Transport | V12 | `transport_routes`, `transport_vehicles`, `student_transport_assignments` | Routes with JSONB stops; vehicle ↔ driver ↔ route; per-student assignment with start/end dates |

---

## Cumulative state

| | Slice 0 | Slice 1 | Slice 2 | Slice 3 | Slice 4 | Slice 5 | Slice 6 |
|---|---|---|---|---|---|---|---|
| Backend Java files | ~324 | +43 | +57 | +63 | +66 | -14 (ocr moved) | +30 |
| OCR-service Java files | — | — | — | — | — | +14 | +14 |
| Flyway migrations | V1–V5 | V1–V6 | V1–V7 | V1–V7 | V1–V8 | V1–V8 | **V1–V12** |
| Tests (backend) | 119 | 147 | 162 | 172 | 172 | 165 | 165* |
| Tests (ocr-service) | — | — | — | — | — | 7 | 7 |

*Slice 6 didn't add tests (kept scope tight). All existing tests still green.

---

## Cross-cutting design choices

### Multi-tenant boundary
Row-level (`school_id` on every domain table) + `TenantInterceptor` (URL path matches JWT
claim) + service-layer query filter. No Postgres RLS yet — defense-in-depth at the DB level
is a deferred hardening item.

### Why JVM-global `@ConditionalOnProperty` is kept alongside per-tenant configs
The global flag controls which provider **bean** loads at boot. The per-tenant config
controls which **credentials** that bean uses at request time. So in production:

- `WHATSAPP_PROVIDER=WATI` → `WatiWhatsAppNotifier` bean is wired in.
- Per-tenant rows in `tenant_provider_configs(WHATSAPP, WATI, {token: ...})` → each school
  uses its own WATI token. Schools without a row fall back to the JVM-global token from env.

This means: a deployment serving 100 schools can either give everyone one shared WATI account
(set env globals, leave per-tenant blank) or give each school its own (set per-tenant rows,
leave env blank). Or hybrid.

### Why `ObjectProvider<X>` shows up in several places
Slice tests (`@WebMvcTest`) don't load the full Spring context. Services like `UsageService`,
`SubscriptionGuardInterceptor`, `IdempotencyKeyRepository` aren't available in those slices.
Using `ObjectProvider<X>` (`getIfAvailable()`) lets the code no-op in tests rather than
breaking bean wiring.

### Idempotency vs feature flags vs role checks — three different gates
- **Idempotency** — POST replay protection.
- **`@RequiresFeature`** — "is this feature enabled for this school?" (subscription / override).
- **`@PreAuthorize`** — "does this user's role permit this endpoint?"
- **`SubscriptionGuardInterceptor`** — "is this school's subscription in a writable state?"
- **`TenantInterceptor`** — "does the URL's tenantId match the JWT claim?"

All five run on every mutating request. Each catches a different kind of failure.

---

## Where to go next

The big remaining buckets, roughly sized:

| Block | Slices |
|---|---|
| Refactor Email + Storage + OCR + LLM dispatchers to consume per-tenant configs | 1 |
| Stripe Billing webhook → real subscription transitions | 1 |
| Postgres Row-Level Security policies | 1 |
| Transactional outbox for durable events | 1–2 |
| OpenTelemetry distributed tracing | 1 |
| HR / Payroll, Hostel, Health-records modules | 1 each |
| Editable per-school PDF templates / grading scales | 1 |
| Frontend web admin (Next.js) | 3–4 |
| Mobile teacher app (React Native) | 2–3 |

Roughly 10–12 more slices to be "complete" by the broadest definition. The SaaS spine and
~80% of the requested domain breadth are done.
