# Architecture

Multi-tenant school management backend. Spring Boot 3.3.5 modular monolith, Java 21, PostgreSQL 16,
Redis 7, Flyway-managed schema. One JVM, 15 domain packages, every tenant-scoped table keyed by
`school_id`, every provider switch driven by a single `app.<concern>.provider` env var.

---

## Stack

| Layer | Choice |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 (Web MVC, Data JPA, Security, Actuator, Validation, Mail) |
| DB | PostgreSQL 15+ (dev: 16) — shared schema, `school_id` row scoping |
| Cache / ephemeral | Redis 7 — OTPs, refresh-token denylist, rate-limit counters |
| Migrations | Flyway — [V1 … V5](../backend/src/main/resources/db/migration/), `baseline-on-migrate=true`, `ddl-auto=validate` |
| API spec | OpenAPI 3 via springdoc — `/v3/api-docs`, `/swagger-ui/index.html` |
| Metrics | Micrometer + Prometheus scrape at `/actuator/prometheus` |
| Logs | Logback — plain text in dev, one-line JSON when `SPRING_PROFILES_ACTIVE=json` |

---

## Package structure

```
in.schoolapp
├── SchoolManagementApplication      entrypoint
├── config/                          AsyncConfig · SecurityConfig · JpaConfig · RedisConfig
│                                    OpenApiConfig · WebMvcConfig · JacksonConfig
│                                    HttpClientConfig · RateLimitFilter
├── common/                          TenantContext · ApiResponse · AppException · normalizers
├── auth/                            OTP + JWT + refresh-token rotation
├── school/                          tenant itself — School, AcademicYear, Classes, Sections, Staff
├── student/                         Students, Parents, sibling detection, family view
├── attendance/                      reverse-marking + sibling-aware absence alerts
├── fee/                             quick-collect, invoices, receipts, reminders, dashboard
├── academics/                       subjects, exams, grid marks entry, report cards
├── communication/                   WhatsApp + email dispatch, templates, inbound webhooks, circulars, inbox
├── payment/                         Stripe / Razorpay / LOGGING gateways + webhook verification
├── storage/                         local + S3-compatible file storage (MinIO SDK)
├── migration/                       OCR + LLM paper-register ingestion
├── analytics/                       dashboards, at-risk detector, daily digest, alert detectors
├── audit/                           audit log writes + GDPR-style data-deletion controller
├── sync/                            mobile offline sync — pull/push endpoints
└── export_/                         CSV/XLSX data export ([trailing `_` avoids `java.lang.export`-style reserved clashes])
```

Module dependency rules (what each reads from neighbours):

- `student` → `school`
- `attendance` → `student`, `school`, `communication`
- `fee` → `student`, `school`, `payment`, `storage`, `communication`
- `academics` → `student`, `school`, `storage`, `communication`
- `migration` → `fee`, `attendance`, `academics`, `student`, `storage`
- `analytics` → `student`, `attendance`, `fee`, `academics`, `communication`
- `audit` → `common` only (publishes from every module via `AuditLogger`)
- `sync` → `student`, `attendance`, `fee`, `academics`, `school`
- `export_` → every read-side module
- `payment`, `storage`, `communication` → `common` only

---

## Multi-tenant boundary

**Strategy:** shared database, row-level `school_id` scoping. No schema-per-tenant, no per-tenant
DB pool.

Enforcement is four concentric barriers:

1. **URL** — every tenant route takes `@PathVariable UUID tenantId`; no tenant route without it.
2. **Interceptor** — [`TenantInterceptor`](../backend/src/main/java/in/schoolapp/common/TenantInterceptor.java)
   auto-compares path `tenantId` against the JWT claim; mismatch → 403. Controllers never need to
   call `validateTenant` explicitly.
3. **Service** — every repository call passes tenantId (enforced at PR review).
4. **Entity** — `BaseEntity.schoolId` is `nullable=false, updatable=false` — can't be switched post-creation.

Internally "tenant" = "school". `TenantContext.getTenantId()` returns `school.id`.

---

## JWT auth

- **Access token** — 15 min, HS256-signed JWT, carries `staffId` / `tenantId` / `role` / `exp`.
- **Refresh token** — 7 days, opaque UUID (122-bit entropy), SHA-256-hashed in Redis. Raw token
  never stored.
- **Rotation** — every successful `/auth/token/refresh` issues a new token and deletes the old hash.
- **Redis denylist** — explicit logout deletes the hash; access tokens naturally expire in ≤ 15 min.
- Public routes: `/api/v1/auth/**`, `POST /api/v1/tenants` (signup), `/webhooks/**`,
  `/actuator/health|info|prometheus`, `/swagger-ui/**`, `/v3/api-docs/**`, `/files/**`, `/api/v1/ping`.

See [`JwtAuthFilter`](../backend/src/main/java/in/schoolapp/auth/JwtAuthFilter.java),
[`RefreshTokenService`](../backend/src/main/java/in/schoolapp/auth/RefreshTokenService.java).

---

## Async + event model

Three dedicated thread pools ([AsyncConfig](../backend/src/main/java/in/schoolapp/config/AsyncConfig.java)):

| Pool | Purpose | Core / Max / Queue |
|---|---|---|
| `notificationExecutor` | WhatsApp + email fan-out, receipt/report-card delivery, payment-event handling | 5 / 20 / 500 |
| `pdfExecutor` | Receipt + report-card PDF generation | 2 / 5 / 100 |
| `ocrExecutor` | OCR + LLM extraction for migration jobs | 2 / 4 / 50 |

**Event bus:** Spring's `ApplicationEventPublisher`. Side-effectful listeners use
`@TransactionalEventListener(phase = AFTER_COMMIT)` so a rolled-back DB transaction never fires a
WhatsApp message; `@Async("...")` on the same method runs dispatch on the right pool.

Representative in-process events:

| Event | Published by | Consumed by |
|---|---|---|
| `AttendanceSubmittedEvent` | `AttendanceService` | `AbsenceAlertService` |
| `FeePaymentCreatedEvent` | `FeePaymentService` | `ReceiptDeliveryListener` (AFTER_COMMIT) |
| `ReportCardGeneratedEvent` | `ReportCardService` | `ReportCardDeliveryListener` (AFTER_COMMIT) |
| `MigrationJobUploadedEvent` | `MigrationJobService` | `MigrationProcessor` (ocrExecutor) |
| `PaymentEvent` | `StripeWebhookController`, `RazorpayWebhookController` | `PaymentEventListener` |

---

## Scheduled jobs

Cron-driven background work, all IST (`Asia/Kolkata`):

| Cron | Job | Purpose |
|---|---|---|
| `0 0 7 * * *` | [`DailyDigestScheduler`](../backend/src/main/java/in/schoolapp/analytics/DailyDigestScheduler.java) | Daily 07:00 WhatsApp summary to principals — attendance + fees yesterday |
| `0 30 9 * * *` | [`FeeReminderSchedulerService`](../backend/src/main/java/in/schoolapp/fee/FeeReminderSchedulerService.java) | 09:30 fee reminders (after morning school starts, before break) |
| `0 0 10 * * MON-SAT` | [`AttendanceNotSubmittedDetector`](../backend/src/main/java/in/schoolapp/analytics/detector/AttendanceNotSubmittedDetector.java) | 10:00 Mon–Sat — nudges teachers who haven't marked attendance |
| `0 30 15 * * *` | [`ConsecutiveAbsenceDetector`](../backend/src/main/java/in/schoolapp/analytics/detector/ConsecutiveAbsenceDetector.java) | 15:30 daily — flags students absent N days in a row |
| `0 0 9 5 * *` | [`FeeCollectionDropDetector`](../backend/src/main/java/in/schoolapp/analytics/detector/FeeCollectionDropDetector.java) | 5th of every month, 09:00 — detects month-over-month collection drop |
| `0 0 3 * * SUN` | [`AtRiskDetectionService`](../backend/src/main/java/in/schoolapp/analytics/detector/AtRiskDetectionService.java) | Sundays 03:00 — composite 0-100 student risk scoring → `student_risk_scores` |

`@EnableScheduling` lives on `AsyncConfig`; all crons use server time zone `Asia/Kolkata` explicitly.

---

## Flyway migrations

| Version | What it adds |
|---|---|
| [V1](../backend/src/main/resources/db/migration/V1__initial_schema.sql) | Base schema — 27 tables (schools, staff, students, parents, enrollments, attendance, fees, exams, marks, report cards, migration jobs, notification log, circulars) + trigram GIN index on students.name |
| [V2](../backend/src/main/resources/db/migration/V2__auth_identifier_uniqueness.sql) | Case-insensitive uniqueness on `staff.email` + unique on `staff.phone` |
| [V3](../backend/src/main/resources/db/migration/V3__notification_webhook_correlation.sql) | `fee_payments.provider_reference` + partial unique index (payment-webhook idempotency); `notification_log(recipient_phone, created_at)` for inbox threading |
| [V4](../backend/src/main/resources/db/migration/V4__student_risk_scores.sql) | `student_risk_scores` table for the at-risk detector |
| [V5](../backend/src/main/resources/db/migration/V5__student_documents.sql) | `student_documents` — admission forms, birth certs, TCs, photos |

Applied automatically on boot (`baseline-on-migrate=true`, `spring.jpa.hibernate.ddl-auto=validate`).

---

## Observability

| Surface | Path | Notes |
|---|---|---|
| Liveness | `/actuator/health` | `{"status":"UP"}`, show-details off |
| Metrics | `/actuator/prometheus` | HTTP request timing percentiles + SLO buckets at 50/100/250/500/1000 ms |
| OpenAPI spec | `/v3/api-docs` | Full JSON spec |
| Swagger UI | `/swagger-ui/index.html` | Browsable + bearerAuth wired so testers paste a JWT |
| Structured logs | `SPRING_PROFILES_ACTIVE=json` | Single-line pseudo-JSON via logback — drops into Loki / CloudWatch with no extra deps |

`HandlerMapping` + MDC `traceId`/`spanId` are preserved in the JSON pattern so distributed-trace
headers work when the gateway injects them.

---

## External integrations

Every external call sits behind an interface with a logging-based dev default and real
implementations toggled via `@ConditionalOnProperty`. Switching providers is an env change plus
restart — never a code change.

| Concern | Interface | Providers (env switch) |
|---|---|---|
| WhatsApp | [`WhatsAppNotifier`](../backend/src/main/java/in/schoolapp/communication/dispatcher/WhatsAppNotifier.java) | `LOGGING` · `WATI` — `app.whatsapp.provider` (inbound webhook is Meta Cloud API shape) |
| Email | [`EmailSender`](../backend/src/main/java/in/schoolapp/communication/dispatcher/EmailSender.java) | `LOGGING` · `SMTP` (any server: Gmail / SES / Mailgun / SendGrid) — `app.email.provider` |
| Payments | [`PaymentGateway`](../backend/src/main/java/in/schoolapp/payment/PaymentGateway.java) | `LOGGING` · `STRIPE` · `RAZORPAY` — `app.payment.provider` |
| File storage | [`FileStorageService`](../backend/src/main/java/in/schoolapp/storage/FileStorageService.java) | `LOCAL` · `S3` (Cloudflare R2, Backblaze B2, MinIO, AWS S3, DO Spaces) — `app.storage.provider` |
| OCR | [`OcrProvider`](../backend/src/main/java/in/schoolapp/migration/ocr/OcrProvider.java) | `LOGGING` · `GOOGLE_CLOUD_VISION` — `app.migration.ocr.provider` |
| LLM | [`LlmExtractionProvider`](../backend/src/main/java/in/schoolapp/migration/llm/LlmExtractionProvider.java) | `LOGGING` · `OPENAI` · `ANTHROPIC` · `GEMINI` — `app.migration.llm.provider` |

**Webhook endpoints** (gated the same way):

| URL | Active when | Verifier |
|---|---|---|
| `POST /webhooks/whatsapp` | always | HMAC-SHA256 via `app.whatsapp.webhook-secret` on raw body |
| `POST /webhooks/stripe` | `app.payment.provider=STRIPE` | `Stripe-Signature: t=…,v1=…` + 5-min replay window |
| `POST /webhooks/razorpay` | `app.payment.provider=RAZORPAY` | `X-Razorpay-Signature` HMAC-SHA256 on raw body |

Provider-specific credential + webhook setup lives in [INTEGRATIONS.md](INTEGRATIONS.md).

---

## Module docs

| Module | Doc |
|---|---|
| common | [modules/common.md](modules/common.md) |
| auth | [modules/auth.md](modules/auth.md) |
| school | [modules/school.md](modules/school.md) |
| student | [modules/student.md](modules/student.md) |
| attendance | [modules/attendance.md](modules/attendance.md) |
| fee | [modules/fee.md](modules/fee.md) |
| academics | [modules/academics.md](modules/academics.md) |
| communication | [modules/communication.md](modules/communication.md) |
| payment | [modules/payment.md](modules/payment.md) |
| storage | [modules/storage.md](modules/storage.md) |
| migration | [modules/migration.md](modules/migration.md) |
| analytics | [modules/analytics.md](modules/analytics.md) |
| audit | [modules/audit.md](modules/audit.md) |
| sync | [modules/sync.md](modules/sync.md) |
| export_ | [modules/export.md](modules/export.md) |

For setup and integration details: [SETUP.md](SETUP.md), [INTEGRATIONS.md](INTEGRATIONS.md).
