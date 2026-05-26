# `analytics` module

Principal dashboard, scheduled anomaly detectors, at-risk student scoring, and the daily WhatsApp digest. Everything that answers "what does the principal need to know today?"

**Package:** `in.schoolapp.analytics`

---

## Purpose

Two faces: a real-time read surface for the principal's dashboard and alert tray, plus a batch of scheduled detectors that write `Alert` rows and `StudentRiskScore` snapshots. Alerts are **only** created by detectors — there's no ad-hoc create endpoint, so a spammed dashboard is always a detector bug rather than user behaviour. The daily digest WhatsApps each principal a one-line morning roll-up so they don't have to log in to learn yesterday's state (gap analysis §5.4).

---

## Entities / tables

| Entity | Table | Migration |
|---|---|---|
| [`Alert`](../../backend/src/main/java/in/schoolapp/analytics/entity/Alert.java) | `alerts` | [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) |
| [`StudentRiskScore`](../../backend/src/main/java/in/schoolapp/analytics/entity/StudentRiskScore.java) | `student_risk_scores` | [V4](../../backend/src/main/resources/db/migration/V4__student_risk_scores.sql) |

`alerts` carries optional `student_id` / `section_id`, a severity, a dismissal flag + actor, and an `expires_at` TTL set by detectors (48h on student alerts, 24h on section alerts). Dismissal is soft — the row stays for audit.

`student_risk_scores` is `UNIQUE(school_id, student_id)` — exactly one row per student, upserted weekly. Fields: `score` (0-100 composite), `attendance_pct`, `fee_outstanding_paise`, `marks_trend`, `top_factor`, `calculated_at`.

### Enums

- [`AlertType`](../../backend/src/main/java/in/schoolapp/analytics/entity/AlertType.java) — `CONSECUTIVE_ABSENCE` · `ATTENDANCE_NOT_SUBMITTED` · `FEE_COLLECTION_DROP` · `AT_RISK_STUDENT` · `OTHER`. Stored as string so new types ship without a migration.
- [`AlertSeverity`](../../backend/src/main/java/in/schoolapp/analytics/entity/AlertSeverity.java) — `LOW` · `MEDIUM` · `HIGH` · `CRITICAL`. `HIGH + CRITICAL` are what the daily digest rolls up.
- [`RiskFactor`](../../backend/src/main/java/in/schoolapp/analytics/entity/RiskFactor.java) — `ATTENDANCE` · `FEE` · `MARKS`. The dominant contributor to a risk score; surfaces on the dashboard so staff know where to intervene first.
- [`MarksTrend`](../../backend/src/main/java/in/schoolapp/analytics/entity/MarksTrend.java) — `UP` · `DOWN` · `FLAT` · `UNKNOWN`. Direction of the last two published report-card percentages with a ±5% deadband.

---

## Services + key public methods

### [`AlertService`](../../backend/src/main/java/in/schoolapp/analytics/AlertService.java)

Read + dismiss surface; also the sole insert path used by detectors.

- `listActive(tenantId)` — all undismissed alerts, newest first.
- `listActiveBySeverity(tenantId, List<AlertSeverity>)` — severity-filtered variant; empty/null list falls through to `listActive`.
- `dismiss(tenantId, alertId)` — soft-dismiss with `dismissedAt` + `dismissedById`; idempotent on already-dismissed rows.
- `recordStudentAlert(...)` / `recordSectionAlert(...)` — one-per-day idempotency guard: if a row of the same `(tenantId, type, studentId|sectionId)` already exists today the insert is silently suppressed and the method returns `null`. Safe for crons to re-run.
- `countsForDigest(tenantId)` → `AlertCounts(total, high, critical)` — used by the daily digest and the dashboard's alerts tile.

### [`DashboardService`](../../backend/src/main/java/in/schoolapp/analytics/DashboardService.java)

One-call composer for the principal landing screen. Returns [`DashboardResponse`](../../backend/src/main/java/in/schoolapp/analytics/dto/DashboardResponse.java) with: attendance summary (today), alert counts, fee MTD (paise), at-risk count (score ≥ 70), unmarked-sections count, top-5 at-risk rows. Student name resolution for the top-5 uses a single `findAllById` to avoid N+1.

### [`DailyDigestScheduler`](../../backend/src/main/java/in/schoolapp/analytics/DailyDigestScheduler.java)

`@Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")` — at 07:00 IST sweeps active schools, skipping any with blank `phone`, and WhatsApps the principal a line with yesterday's attendance, open alert counts, and MTD fee collection. Uses `WhatsAppMessage.MessageType.EMERGENCY` so it isn't gated by quiet hours.

### Detectors ([`detector/`](../../backend/src/main/java/in/schoolapp/analytics/detector))

| Detector | Cron (IST) | Output |
|---|---|---|
| [`ConsecutiveAbsenceDetector`](../../backend/src/main/java/in/schoolapp/analytics/detector/ConsecutiveAbsenceDetector.java) | `0 30 15 * * *` (daily 15:30) | `CONSECUTIVE_ABSENCE` alert (HIGH) for students absent ≥3 of the last 5 school days. Thresholds tunable via `app.analytics.consecutive-absence.*`. |
| [`AttendanceNotSubmittedDetector`](../../backend/src/main/java/in/schoolapp/analytics/detector/AttendanceNotSubmittedDetector.java) | `0 0 10 * * MON-SAT` (10:00 Mon-Sat) | `ATTENDANCE_NOT_SUBMITTED` alert (MEDIUM) per unmarked section + WhatsApp nudge to the class teacher. |
| [`FeeCollectionDropDetector`](../../backend/src/main/java/in/schoolapp/analytics/detector/FeeCollectionDropDetector.java) | `0 0 9 5 * *` (5th of month, 09:00) | `FEE_COLLECTION_DROP` alert (CRITICAL) when prior-month collection drops ≥20% vs the 3-month trailing average. Skips schools without enough baseline (`min-prior-collection-paise`). Uses a deterministic `UUID.nameUUIDFromBytes("fee-drop-" + yearMonth)` as the idempotency key so same-day re-runs are safe. |
| [`AtRiskDetectionService`](../../backend/src/main/java/in/schoolapp/analytics/detector/AtRiskDetectionService.java) | `0 0 3 * * SUN` (Sunday 03:00) | Upserts `student_risk_scores`; emits `AT_RISK_STUDENT` alert (HIGH) for scores ≥ `alert-threshold` (default 70). Composite is `max(attendanceSub, feeSub, marksSub)` — **not** a sum, so a marks-only flag doesn't inflate past a pure-attendance flag. |

### Configurable thresholds (`app.analytics.*`)

| Property | Default |
|---|---|
| `app.analytics.consecutive-absence.window-days` | 5 |
| `app.analytics.consecutive-absence.threshold` | 3 |
| `app.analytics.fee-drop.threshold-pct` | 20 |
| `app.analytics.fee-drop.min-prior-collection-paise` | 100000 |
| `app.analytics.at-risk.window-days` | 30 |
| `app.analytics.at-risk.fee-benchmark-paise` | 5000000 |
| `app.analytics.at-risk.alert-threshold` | 70 |

---

## Endpoints

| Path | Method | `@PreAuthorize` | Description |
|---|---|---|---|
| `/api/v1/tenants/{tenantId}/alerts?severity=...` | GET | *(none — any authenticated tenant user)* | Active alerts, optional `severity` multi-value filter. |
| `/api/v1/tenants/{tenantId}/alerts/{alertId}/dismiss` | POST | `AppRoles.OWNER_OR_ADMIN` | Soft-dismiss. |
| `/api/v1/tenants/{tenantId}/dashboard` | GET | *(none — any authenticated tenant user)* | Single-call dashboard payload. |

Controllers: [`AlertController`](../../backend/src/main/java/in/schoolapp/analytics/AlertController.java), [`DashboardController`](../../backend/src/main/java/in/schoolapp/analytics/DashboardController.java).

---

## Design decisions

- **Detectors are the only writers.** `AlertController` has no POST for create. Every alert has a known provenance (type + config) which makes triage deterministic — a spammed tray is always a cron bug.
- **One-per-day idempotency is in `AlertService`, not the detectors.** Keeps every detector honest without repeating the `existsBy...CreatedAtAfter` check four times. Returning `null` on suppression lets callers count real inserts.
- **At-risk score is `max`, not sum.** A single 100-point signal shouldn't be drowned out by noise in the other two, and a student with three moderate 40s should still surface. `topFactor` explains which signal won so the UI can recommend an intervention.
- **Marks deadband.** `±5%` on report-card-percentage delta avoids a student flipping between `UP` and `DOWN` on insignificant moves. `UNKNOWN` (new student, first exam) contributes 30 points — we err slightly toward flagging so new arrivals get noticed.
- **Fee-drop uses a synthetic "section" key for idempotency.** `FEE_COLLECTION_DROP` isn't student-scoped and the schema has `section_id` but no tenant-wide key; we hash the year-month into a deterministic UUID via `UUID.nameUUIDFromBytes` so same-month re-runs dedupe.
- **Dashboard tiles fail independently.** Missing academic year / no fee data / no risk rows all degrade to zero or empty rather than an error — a fresh school still gets a usable dashboard.
- **Top-5 student name resolution is batched.** One `findAllById` for the whole tile instead of N lookups; observable N+1 guard documented in the service.

---

## Cross-module calls

### Depends on

- [`attendance`](attendance.md) — `AttendanceAnalyticsService`, `AttendanceRepository` (per-student absence counts + unmarked-sections query).
- [`fee`](fee.md) — `FeePaymentRepository.sumCollectedBetween`, `FeeInvoiceService.getOutstanding`.
- [`academics`](academics.md) — `ReportCardRepository` (for marks trend).
- [`school`](school.md) — `SchoolRepository`, `SectionRepository`, `AcademicYearService`.
- [`student`](student.md) — `StudentRepository`.
- [`communication`](communication.md) — `WhatsAppNotifier` (digest + class-teacher nudges).
- [`common`](common.md) — `TenantContext` for the dismiss actor.

### Depended on by

- The mobile / web frontend calls `GET /dashboard` and `GET /alerts` for the principal home.
- No other backend module imports from `analytics` — it's a read-only leaf.

---

## Relevant migrations

- [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) — creates `alerts` with `(school_id, is_dismissed, created_at DESC)` and `(student_id, created_at DESC)` indexes.
- [V4](../../backend/src/main/resources/db/migration/V4__student_risk_scores.sql) — creates `student_risk_scores` with `UNIQUE(school_id, student_id)` for the per-student upsert and `(school_id, score DESC)` for the dashboard top-N tile.
