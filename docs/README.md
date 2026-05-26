# Documentation index

| Doc | For |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System overview, request lifecycle, module graph, 3rd-party surface |
| [SETUP.md](SETUP.md) | Local dev in 10 minutes, env vars, smoke tests, troubleshooting |
| [INTEGRATIONS.md](INTEGRATIONS.md) | Step-by-step provider credentials + webhook URLs + smoke tests |
| [frontend/](frontend/) | **LLM-consumable specs to build the React web admin** — auth, endpoints, DTOs, role matrix, flows |
| [../IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md) | What's done, what's pending, next-phase roadmap |

## Per-module deep dives

Each describes the module's entities, services, endpoints, config, and cross-module relationships. (Pending / roadmap items live only in [../IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md).)

| Module | What it does |
|---|---|
| [common](modules/common.md) | Foundational utilities (TenantContext, ApiResponse, error handling, normalisers) |
| [auth](modules/auth.md) | OTP + JWT + refresh tokens; phone/email signup channel flag |
| [school](modules/school.md) | The tenant itself — School, AcademicYear, Classes, Sections, Staff |
| [student](modules/student.md) | Students, parents, sibling detection, family view |
| [attendance](modules/attendance.md) | Reverse-marking attendance + sibling-aware absence alerts |
| [fee](modules/fee.md) | Quick-collect, invoices, receipts, dashboard, reminders |
| [academics](modules/academics.md) | Subjects, exams, grid marks entry, report cards |
| [communication](modules/communication.md) | WhatsApp + email dispatchers, templates, event-driven delivery, webhooks |
| [payment](modules/payment.md) | Stripe / Razorpay / Logging gateways with webhook verification |
| [storage](modules/storage.md) | Local + S3-compatible file storage via MinIO SDK |
| [migration](modules/migration.md) | OCR + LLM paper-register ingestion with human review |
| [analytics](modules/analytics.md) | Alerts, at-risk scoring, dashboard, scheduled detectors, daily digest |
| [audit](modules/audit.md) | `audit_log` writes, read API, DPDP Act data-deletion intake |
| [sync](modules/sync.md) | Mobile offline-first attendance push + delta pull |
| [export](modules/export.md) | Streaming XLSX/CSV exports (students / fees / attendance) |

## Where to start

- **New developer onboarding** → [SETUP.md](SETUP.md) → run smoke test → [ARCHITECTURE.md](ARCHITECTURE.md) for context → dive into the module you're assigned
- **Production deploy** → [SETUP.md § env vars](SETUP.md#6-environment-variables-reference) → [INTEGRATIONS.md](INTEGRATIONS.md) provider-by-provider
- **Picking up the next phase** → [../IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md) for prioritised pending work
- **Specific module work** → [modules/](modules/) — each doc links into source files
