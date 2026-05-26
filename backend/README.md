# School Management Backend

Multi-tenant school management system backend. Spring Boot 3.3 monolith, Java 21, PostgreSQL 16.

## 📚 Documentation

- **[../docs/README.md](../docs/README.md)** — documentation index
- **[../docs/SETUP.md](../docs/SETUP.md)** — local dev in 10 minutes + env vars + troubleshooting
- **[../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)** — system overview, request flow, module graph
- **[../docs/INTEGRATIONS.md](../docs/INTEGRATIONS.md)** — step-by-step 3rd-party setup (WhatsApp / SMTP / Stripe / Razorpay / R2 / OCR / LLMs)
- **[../docs/modules/](../docs/modules/)** — per-module deep dives (11 docs)
- **[../IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md)** — what's done, what's pending, next-phase plan

## Quick start

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Build + run
mvn spring-boot:run

# 3. Smoke test
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/ping
```

Expected ping response:
```json
{
  "success": true,
  "data": {
    "service": "school-management",
    "status": "ok",
    "timestamp": "2026-04-22T12:34:56+05:30"
  }
}
```

## Tests

```bash
mvn test                   # unit + webmvc slice tests
```

## Structure (Slices 1–9)

```
src/main/java/in/schoolapp/
├── SchoolManagementApplication.java
├── config/         # Security, JPA, Async, Jackson, WebMvc, Redis, HttpClient
├── common/         # ApiResponse, ErrorCode, AppException, BaseEntity, TenantContext,
│                   #   TenantInterceptor, PhoneNormalizer, EmailNormalizer
├── auth/           # OtpService, JwtService, RefreshTokenService, JwtAuthFilter,
│                   #   AuthService, AuthController (phone OR email; signup channel flag)
├── school/         # School, AcademicYear, Staff, SchoolClass, Section + services
├── student/        # Student, Parent, StudentParentLink, StudentEnrollment,
│                   #   StudentService (.createStudent + .createHistoricalStudent),
│                   #   ParentService, FamilyService, StudentController
├── attendance/     # AttendanceRecord, AttendanceService (reverse-marking + createHistorical),
│                   #   AbsenceAlertService
├── fee/            # FeeHead, FeeInvoice, FeePayment; ReceiptService, FeePaymentService
│                   #   (.quickCollect + .createHistorical), FeeInvoiceService,
│                   #   FeeDashboardService, FeeReminderService
├── academics/      # Subject, Exam, ExamMark, ReportCard + services, GradeCalculator,
│                   #   MarksService (.submitBulk + .createHistorical), ReportCardPdfGenerator
├── communication/  # dispatcher/ — OtpDispatcher, WhatsAppNotifier (Logging | Wati),
│                   #   EmailSender (Logging | Smtp) — @ConditionalOnProperty switched,
│                   #   template/, event/, webhook/
├── payment/        # PaymentGateway interface + 3 impls (Logging, Stripe, Razorpay),
│                   #   PaymentLinkService, per-provider webhook controllers
├── storage/        # FileStorageService (Local | S3 via MinIO SDK — works with R2/B2/
│                   #   MinIO/AWS/DO/Wasabi), /files/** controller, presigned URLs
└── migration/      # OCR + LLM paper-register ingestion
                    #   ocr/ — OcrProvider (Logging | GoogleCloudVision)
                    #   llm/ — LlmExtractionProvider (Logging | OpenAI | Anthropic | Gemini)
                    #          + PromptTemplates + tolerant LlmJsonParser
                    #   EntityMatchingService (Levenshtein fuzzy match on trigram index),
                    #   MigrationJobService (@Async OCR→LLM→match pipeline + per-type commit
                    #     dispatch to FeePaymentService / AttendanceService / MarksService /
                    #     StudentService createHistorical methods),
                    #   MigrationController (upload / get / commit)
```

Subsequent slices add modules: `analytics/`, `sync/`, `export/`.

## Multi-tenant URL convention

All tenant-scoped endpoints take `{tenantId}` as a path variable:

```
/api/v1/tenants/{tenantId}/...
```

The `TenantInterceptor` compares the path's tenantId against the caller's JWT claim on every request — mismatch = 403. Internal code continues to use `schoolId` (matches DB column); `TenantContext.getTenantId()` returns the same value.

## Signup channel feature flag

```yaml
app:
  signup:
    channel: PHONE   # PHONE | EMAIL | BOTH
```

- `PHONE` — phone required at signup, OTP via phone (WhatsApp)
- `EMAIL` — email required at signup, OTP via email (SMTP)
- `BOTH` — either works; login uses whichever identifier the user supplies

Override at runtime: `SIGNUP_CHANNEL=EMAIL mvn spring-boot:run`.

## Dispatcher providers (WhatsApp + Email)

Both default to `LOGGING` (console output). Flip to real providers via env vars:

```bash
# Real WhatsApp via WATI
WHATSAPP_PROVIDER=WATI \
WATI_BASE_URL=https://live-mt-server.wati.io/<accountId> \
WATI_TOKEN=<bearer token> \
WHATSAPP_WEBHOOK_SECRET=<hmac secret from WATI dashboard> \
mvn spring-boot:run

# Real email via SMTP (Gmail, SendGrid, Mailgun, etc.)
EMAIL_PROVIDER=SMTP \
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_USERNAME=school@gmail.com \
SMTP_PASSWORD=<app password> \
EMAIL_FROM="Sunshine School <school@gmail.com>" \
mvn spring-boot:run
```

With `LOGGING`, outbound messages appear in the app logs as `[WA-SEND …]` / `[EMAIL-SEND …]`. With `WATI`/`SMTP`, they hit the real providers. No consumer code changes either way — `AbsenceAlertService`, `ReceiptDeliveryListener`, `FeeReminderService`, and `ReportCardDeliveryListener` all depend on the interfaces.

## Payment provider switch

Same pattern for payment link generation — pick LOGGING (dev default, fake URLs), STRIPE, or RAZORPAY:

```yaml
app:
  payment:
    provider: LOGGING          # LOGGING | STRIPE | RAZORPAY
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

```bash
# Real Stripe
PAYMENT_PROVIDER=STRIPE \
STRIPE_SECRET_KEY=sk_live_... \
STRIPE_WEBHOOK_SECRET=whsec_... \
mvn spring-boot:run

# Real Razorpay (recommended for Indian schools — UPI support)
PAYMENT_PROVIDER=RAZORPAY \
RAZORPAY_KEY_ID=rzp_live_... \
RAZORPAY_KEY_SECRET=... \
RAZORPAY_WEBHOOK_SECRET=... \
mvn spring-boot:run
```

**Webhooks**:
- Stripe → `POST /webhooks/stripe` (signature in `Stripe-Signature`, 5-minute replay guard)
- Razorpay → `POST /webhooks/razorpay` (signature in `X-Razorpay-Signature`)

Each endpoint is only mounted when its provider is active. Tenant + student IDs round-trip through the provider's metadata/notes, so webhook handlers can correlate payments back to the originating flow. Verified `PAID` events publish a provider-neutral `PaymentEvent` via `ApplicationEventPublisher` — Slice 7.5 adds a listener that auto-creates a `FeePayment` record.

FeeReminderService uses the active provider automatically — a reminder sent while `provider=RAZORPAY` includes a real Razorpay payment link with pre-filled amount. If the provider API is down, the reminder still goes out (without the link) rather than the entire batch being skipped.

## File storage switch (free-tier-friendly)

Receipt PDFs + report card PDFs go through `FileStorageService`. Default is LOCAL (disk + `/files/**`); swap to any S3-compatible provider via config.

```yaml
app:
  storage:
    provider: LOCAL               # LOCAL | S3
    presign-ttl-minutes: 10080    # 7 days (S3 max)
    local:
      base-dir: ./storage
      public-base-url: http://localhost:8080/files
    s3:
      endpoint: https://<acct>.r2.cloudflarestorage.com
      region: auto                # "auto" for R2, "us-east-1" for AWS, etc.
      bucket: schoolapp-files
      access-key-id: ...
      secret-access-key: ...
      public-base-url:            # set only if bucket is public (skips presigning)
      force-path-style: false     # true for MinIO self-hosted
```

**Free-tier recommendations:**

| Provider | Free tier | Egress | Notes |
|---|---|---|---|
| **Cloudflare R2** | **10 GB forever** | **$0** | Best pick. Endpoint: `https://<accountId>.r2.cloudflarestorage.com`, region `auto` |
| Backblaze B2 | 10 GB + 1 GB/day free | free 3× storage | Strong alternative. S3-compatible endpoint in dashboard |
| MinIO self-hosted | ∞ (own VPS) | $0 | Use for air-gapped deployments |
| AWS S3 | 5 GB (12 months only) | $0.09/GB after free tier | Standard, but costly egress at scale |

```bash
# Activate Cloudflare R2 (recommended)
STORAGE_PROVIDER=S3 \
S3_ENDPOINT=https://<accountId>.r2.cloudflarestorage.com \
S3_REGION=auto \
S3_BUCKET=schoolapp-files \
S3_ACCESS_KEY_ID=... \
S3_SECRET_ACCESS_KEY=... \
mvn spring-boot:run

# Activate MinIO self-hosted
STORAGE_PROVIDER=S3 \
S3_ENDPOINT=http://minio.internal:9000 \
S3_REGION=us-east-1 \
S3_BUCKET=schoolapp \
S3_ACCESS_KEY_ID=minioadmin \
S3_SECRET_ACCESS_KEY=... \
S3_FORCE_PATH_STYLE=true \
mvn spring-boot:run
```

Keys use a flat convention: `receipts/{tenantId}/{paymentId}.pdf`, `report-cards/{tenantId}/{reportCardId}.pdf` — maps cleanly to both directory layout and S3 object keys. Callers ([ReceiptService](backend/src/main/java/in/schoolapp/fee/ReceiptService.java), [ReportCardService](backend/src/main/java/in/schoolapp/academics/ReportCardService.java)) don't care about the backend.

## End-to-end smoke test

With Postgres + Redis running (`docker compose up -d`) and the app running (`mvn spring-boot:run`):

```bash
# 1. Signup (public) — returns tenantId
curl -X POST http://localhost:8080/api/v1/tenants \
  -H 'Content-Type: application/json' \
  -d '{
    "schoolName":"Sunshine Public School",
    "principalName":"Rajesh Kumar",
    "phone":"9876543210",
    "state":"Maharashtra",
    "city":"Pune",
    "board":"CBSE"
  }'

# 2. Request OTP — check app console logs for:
#    [OTP-DISPATCH] channel=PHONE to=98765****10 otp=123456
curl -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210"}'

# 3. Verify (paste the OTP from the console)
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210","otp":"123456"}'

# 4. Bulk create classes + sections (authenticated)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/classes/bulk \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{"classes":[{"name":"Class 1","sections":["A","B"]},{"name":"Class 2","sections":["A"]}]}'

# 5. Create a student (3-field minimum — the section_id comes from step 4)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/students \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName":"Rohan",
    "sectionId":"<sectionId>",
    "parentPhone":"9999911111",
    "parentName":"Ramesh Kumar"
  }'

# 6. Create a sibling (same parent phone → same Parent row → siblings detected)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/students \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName":"Neha",
    "sectionId":"<other-section-id>",
    "parentPhone":"9999911111"
  }'

# 7. Submit attendance — mark Rohan ABSENT; everyone else auto-PRESENT
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/sections/<sectionId>/attendance \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "date":"2026-04-23",
    "entries":[{"studentId":"<rohanId>","status":"ABSENT"}]
  }'

# 8. If Rohan and Neha share a parent AND both were absent, watch the logs for the
#    sibling-aware combined message:
#    [WA-SEND type=ABSENCE_ALERT] to=99999****11 body="Dear Ramesh, Rohan and Neha were both
#                                                   marked ABSENT today (23 Apr 2026)…"

# 9. Quick-collect a fee payment (zero-config — no fee structure required up front)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/fees/payments \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "studentId":"<rohanId>",
    "amountPaise":450000,
    "paymentMode":"CASH",
    "notes":"Term 1 fees"
  }'
# Response includes receiptNumber (REC-2026-000001), receiptPdfUrl (file:… local path),
# and outstandingBalancePaise. A WhatsApp receipt is dispatched to the primary parent:
#   [WA-SEND type=FEE_RECEIPT] to=99999****11 body="🧾 Fee Receipt — Sunshine Public School…"
# The PDF lands under ./receipts/<tenantId>/<paymentId>.pdf.

# 10. Principal's fee dashboard
curl http://localhost:8080/api/v1/tenants/<tenantId>/fees/dashboard \
  -H 'Authorization: Bearer <accessToken>'

# 11. Defaulters list
curl http://localhost:8080/api/v1/tenants/<tenantId>/fees/defaulters \
  -H 'Authorization: Bearer <accessToken>'

# 12. Record opening balances (bulk paper-ledger migration)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/fees/opening-balances \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "balances":[
      {"studentId":"<rohanId>","amountPaise":250000,"note":"Carried forward from Term 1"}
    ]
  }'

# 13. Bulk fee reminder (async — returns immediately, dispatches in the background)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/fees/reminders \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{"studentIds":["<rohanId>"]}'

# 14. Bulk-create subjects
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/subjects/bulk \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{"subjects":[{"name":"Mathematics","code":"MATH"},{"name":"English","code":"ENG"},{"name":"Science","code":"SCI"}]}'

# 15. Create an exam (uses the current academic year automatically)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/exams \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Unit Test 1","examType":"UNIT_TEST","startDate":"2026-05-10"}'

# 16. Fetch the marks entry grid (roster + existing marks, if any)
curl http://localhost:8080/api/v1/tenants/<tenantId>/exams/<examId>/marks/<sectionId> \
  -H 'Authorization: Bearer <accessToken>'

# 17. Submit marks grid — only non-absent rows need obtainedMarks; absent=true records AB
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/exams/<examId>/marks \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "sectionId":"<sectionId>",
    "submitFinal": false,
    "entries":[
      {"studentId":"<rohanId>","subjectId":"<mathId>","maxMarks":25,"obtainedMarks":22,"absent":false},
      {"studentId":"<rohanId>","subjectId":"<engId>","maxMarks":25,"obtainedMarks":20,"absent":false}
    ]
  }'

# 18. Completion status — which subjects still need marks entered?
curl http://localhost:8080/api/v1/tenants/<tenantId>/exams/<examId>/completion/<sectionId> \
  -H 'Authorization: Bearer <accessToken>'

# 19. Generate report cards for the whole section (async WhatsApp delivery via the
#     ReportCardDeliveryListener — watch the logs for [WA-SEND type=REPORT_CARD] lines).
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/exams/<examId>/report-cards/generate/<sectionId> \
  -H 'Authorization: Bearer <accessToken>'

# 20. Fetch a specific student's report card (PDF at pdfUrl)
curl http://localhost:8080/api/v1/tenants/<tenantId>/students/<rohanId>/report-card/<examId> \
  -H 'Authorization: Bearer <accessToken>'

# 21. Upload a scanned paper receipt for OCR migration (dev uses LOGGING providers — canned
#     text + mock records; swap app.migration.ocr/llm.provider for real Google Vision + LLM)
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/migration \
  -H 'Authorization: Bearer <accessToken>' \
  -F 'type=FEE_RECEIPT' \
  -F 'file=@receipt-book-page-3.jpg'
# Returns jobId with status=UPLOADED; async pipeline moves it to REVIEW within seconds.

# 22. Poll the job — when status=REVIEW, the response includes reviewableRecords with match
#     candidates per row and a confidence score for each
curl http://localhost:8080/api/v1/tenants/<tenantId>/migration/<jobId> \
  -H 'Authorization: Bearer <accessToken>'

# 23. Commit the human-confirmed rows. For FEE_RECEIPT (as shown); other types — ATTENDANCE,
#     MARKS, ADMISSION_FORM — use the type-specific fields on ConfirmedRow.
curl -X POST http://localhost:8080/api/v1/tenants/<tenantId>/migration/<jobId>/commit \
  -H 'Authorization: Bearer <accessToken>' \
  -H 'Content-Type: application/json' \
  -d '{
    "rows":[
      {"rowIndex":0,"studentId":"<rohanId>","amountPaise":450000,"paymentMode":"CASH",
       "paymentDate":"2025-04-12","externalReceiptNumber":"1845","notes":"Term 2"}
    ]
  }'
```

### LLM provider switch

Dev default is `LOGGING` (canned mock records — zero cost). Flip to a real provider with
credentials:

```bash
# OpenAI
LLM_PROVIDER=OPENAI OPENAI_API_KEY=sk-... mvn spring-boot:run

# Anthropic (Claude Haiku 4.5 — cheap + fast for OCR structuring)
LLM_PROVIDER=ANTHROPIC ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run

# Gemini (generous free tier — 15 RPM on gemini-1.5-flash)
LLM_PROVIDER=GEMINI GEMINI_API_KEY=AIza... mvn spring-boot:run
```

OCR flip:
```bash
OCR_PROVIDER=GOOGLE_CLOUD_VISION GOOGLE_VISION_API_KEY=AIza... mvn spring-boot:run
```

### Signup via email (EMAIL channel)

Set `SIGNUP_CHANNEL=EMAIL` before starting, then:
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -d '{"schoolName":"...", "principalName":"...", "email":"principal@school.in",
       "state":"...", "board":"CBSE"}'
# OTP logged as: [OTP-DISPATCH] channel=EMAIL to=pr****@school.in otp=123456
curl -X POST http://localhost:8080/api/v1/auth/otp/send -d '{"email":"principal@school.in"}'
```

## Env vars (dev defaults baked into application.yml)

| Var | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/schoolapp` | Swap for Railway/RDS in prod |
| `DATABASE_USER` | `schoolapp` | |
| `DATABASE_PASSWORD` | `schoolapp_dev_password` | Never commit prod value |
| `JWT_SECRET` | dev-only fallback | **Required** in prod, ≥64 chars |
| `SERVER_PORT` | `8080` | |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` for production |

## Flyway migrations

- `V1__initial_schema.sql` — all core tables: schools, students, attendance, fees, academics, communication, migration, audit, plus v1.1 additions (alerts, whatsapp_inbox_messages, fee_reminder_schedules, substitute_assignments) and `attendance_records.arrival_time`.
- `V2__auth_identifier_uniqueness.sql` — phone made nullable (email-only signup), global unique indexes on `staff.phone`, `staff.email`, `schools.email`.

Add new migrations as `V3__<description>.sql`, `V4__...` — never modify a migration that has been applied.
