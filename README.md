# School Management System

Multi-tenant school management SaaS for small Indian private schools (50–600 students). Spring Boot 3.3 monolith · Java 21 · PostgreSQL 16 · Redis 7. One codebase, one deploy, many tenants.

---

## 🧭 Start here

| I want to… | Go to |
|---|---|
| Run it locally | [`docs/SETUP.md`](docs/SETUP.md) |
| Understand the architecture | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| Plug in a real provider (WhatsApp / Stripe / R2 / Gemini / …) | [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md) |
| Work on a specific module | [`docs/modules/`](docs/modules/) — 11 per-module docs |
| Know what's done vs pending | [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) |
| Read the original problem space | [`01_GAP_ANALYSIS.md`](01_GAP_ANALYSIS.md) |
| Read the backend LLD (design source of truth) | [`PHASE2_BACKEND_SPRINGBOOT_LLD.md`](PHASE2_BACKEND_SPRINGBOOT_LLD.md) |

---

## Status at a glance

**9 slices shipped · 71 tests green · ~195 Java files · 10 domain modules**

- ✅ Signup → OTP login → multi-tenant boundary (path-based + JWT-verified)
- ✅ Students + sibling auto-detection + family view
- ✅ Reverse-marking attendance + sibling-aware absence alerts
- ✅ Zero-config fee collection + partial payment + receipt PDFs + dashboard + defaulters
- ✅ Subjects, exams, grid marks entry, board-specific grading, report cards with delivery
- ✅ Configurable WhatsApp (WATI) / email (SMTP) dispatchers
- ✅ Configurable payments (Stripe / Razorpay)
- ✅ Configurable file storage (Local / S3-compatible — R2, B2, MinIO, AWS)
- ✅ OCR + LLM paper-register migration (Google Cloud Vision + OpenAI/Anthropic/Gemini)

**Next up** (see [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)):
- 🔴 Analytics + alerts (consecutive absences, fee-drop detection, daily principal digest)
- 🔴 Mobile offline sync
- 🔴 Data export (XLSX/CSV streaming)
- 🔴 `notification_log` persistence + inbox routing (Slice 6.5)
- 🔴 Auto-create `FeePayment` from verified `PAID` webhook (Slice 7.5)
- 🔴 RBAC hardening + audit log writes

---

## The 30-second architecture

One app, 10 domain modules, every tenant-scoped table has `school_id`, every tenant-scoped URL has `{tenantId}`. Every external provider (WhatsApp BSP, SMTP, Stripe, Razorpay, S3, OCR, LLMs) is abstracted behind an interface with a LOGGING dev default and real implementations toggled via `@ConditionalOnProperty` — switching providers is a config change, never a code change.

Request flow:

```
JWT → JwtAuthFilter (populates TenantContext ThreadLocal)
    → TenantInterceptor (verifies {tenantId} path = JWT claim)
    → Controller → Service (@Transactional)
    → publishes ApplicationEvent
    → @Async @TransactionalEventListener(AFTER_COMMIT)
    → external dispatcher (WhatsApp / email / payment / storage / OCR / LLM)
```

Full diagram: [docs/ARCHITECTURE.md § module graph](docs/ARCHITECTURE.md#module-graph).

---

## Run it in 2 minutes (LOGGING providers, no credentials needed)

```bash
cd backend
docker compose up -d           # Postgres 16 + Redis 7
mvn spring-boot:run            # app on :8080

# Signup — creates a school + principal + academic year
curl -X POST http://localhost:8080/api/v1/tenants \
  -H 'Content-Type: application/json' \
  -d '{"schoolName":"Demo","principalName":"Rajesh","phone":"9876543210","state":"MH","board":"CBSE"}'

# Watch app logs for:
#   [OTP-DISPATCH] channel=PHONE to=98765****10 otp=123456

curl -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' -d '{"phone":"9876543210"}'

curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' -d '{"phone":"9876543210","otp":"<paste>"}'
```

Full walkthrough with 23 sequenced curls: [backend/README.md § smoke test](backend/README.md#end-to-end-smoke-test).

---

## Free-tier production stack

A fully free-tier deploy is viable for a single-school pilot:

| Concern | Provider | Free tier |
|---|---|---|
| Database | Railway / Neon PostgreSQL | 500 MB – 1 GB free |
| Redis | Upstash | 10 000 commands/day free |
| WhatsApp | WATI | Paid (~₹1,500/mo) — the only non-free link |
| Email | Gmail SMTP | 500/day free |
| Payments | Razorpay (India) / Stripe (global) | No fixed cost, % per txn |
| File storage | Cloudflare R2 | 10 GB forever + zero egress |
| OCR | Google Cloud Vision | 1000 pages/month free |
| LLM | Google Gemini (`gemini-1.5-flash`) | 15 RPM / 1500 RPD free |

Details: [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md).

---

## Repository layout

```
SchoolManagementSystem/
├── README.md                          ← you are here
├── IMPLEMENTATION_STATUS.md           what's done + pending, module-by-module
├── 01_GAP_ANALYSIS.md                 market problem space + user personas
├── 02_DEVELOPMENT_PLAN.md             (historical — superseded by Spring Boot LLD)
├── PHASE1_FRONTEND_PLAN.md            frontend design spec (UI, not yet implemented)
├── PHASE2_BACKEND_SPRINGBOOT_LLD.md   backend LLD — design source of truth
├── docs/                              developer docs
│   ├── README.md                      docs index
│   ├── ARCHITECTURE.md                system overview
│   ├── SETUP.md                       local dev guide
│   ├── INTEGRATIONS.md                3rd-party setup walkthroughs
│   └── modules/                       11 per-module deep dives
└── backend/                           Spring Boot 3.3 app
    ├── pom.xml
    ├── docker-compose.yml             Postgres + Redis
    ├── Dockerfile
    ├── README.md                      backend-specific setup + smoke-test curls
    └── src/
        ├── main/java/in/schoolapp/
        │   ├── SchoolManagementApplication.java
        │   ├── config/                Security, JPA, Async, Jackson, WebMvc, Redis, HTTP
        │   ├── common/                Base utilities
        │   ├── auth/                  OTP + JWT + refresh
        │   ├── school/                School, academic year, classes, sections, staff
        │   ├── student/               Students, parents, sibling detection
        │   ├── attendance/            Reverse-marking + sibling alerts
        │   ├── fee/                   Quick-collect, invoices, receipts, reminders
        │   ├── academics/             Subjects, exams, marks, report cards
        │   ├── communication/         WhatsApp / email / templates / webhooks
        │   ├── payment/               Stripe / Razorpay / Logging gateways
        │   ├── storage/               Local + S3-compatible file storage
        │   └── migration/             OCR + LLM paper ingestion
        ├── main/resources/
        │   ├── application.yml        All config + env var overrides
        │   └── db/migration/          Flyway — V1 + V2
        └── test/
```

---

## License + contribution

Proprietary / unlicensed — to be decided when open-sourcing.

Contribution flow (internal): fork → branch → tests green (`mvn test`) → PR → review. Per-module docs carry the design conventions any change should respect.
