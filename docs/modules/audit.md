# `audit` module

Compliance-grade change log plus DPDP Act 2023 data-deletion intake. Every significant write in the app funnels through one service; the trail is read-only afterwards.

**Package:** `in.schoolapp.audit`

---

## Purpose

Writes to critical entities — students, payments, staff, exam publishing, circulars, fees, school config — flow through [`AuditLogger`](../../backend/src/main/java/in/schoolapp/audit/AuditLogger.java) and land in a single `audit_log` table. Row-per-change with a JSONB `old_values` / `new_values` diff so any entity can be tracked without schema churn. DPDP Act 2023 requires "every data-handling decision must be traceable"; this module is the traceability primitive. The `DataDeletionController` records data-deletion requests as first-class audit events — actual purging is deferred to a compliance workflow.

---

## Entity / table

| Entity | Table | Migration |
|---|---|---|
| [`AuditLog`](../../backend/src/main/java/in/schoolapp/audit/entity/AuditLog.java) | `audit_log` | [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) |

Columns: `school_id`, `entity_type`, `entity_id`, `action`, `old_values` (jsonb), `new_values` (jsonb), `changed_by_id`, `changed_by_role`, `ip_address`, `created_at`. Indexes on `(entity_type, entity_id, created_at DESC)` for the per-entity activity timeline and `(school_id, created_at DESC)` for the tenant-wide stream.

### Enum

[`AuditAction`](../../backend/src/main/java/in/schoolapp/audit/entity/AuditAction.java): `CREATE` · `UPDATE` · `DELETE` · `ACTION`. The coarse-grained enum leaves field-level detail to the JSONB diff — `ACTION` covers non-CRUD domain events (exam published, alert dismissed, circular posted, DPDP deletion requested, data export).

---

## Services + key public methods

### [`AuditLogger`](../../backend/src/main/java/in/schoolapp/audit/AuditLogger.java)

Central write service. Every public method is `@Transactional(propagation = REQUIRES_NEW)` **and** wraps its write in a try/catch that swallows + logs exceptions — an audit failure must never roll back the caller's business write.

| Method | Signature | Purpose |
|---|---|---|
| `logCreate` | `(tenantId, entityType, entityId, newValues)` | Row creation; `old_values` null. |
| `logUpdate` | `(tenantId, entityType, entityId, oldValues, newValues)` | Row edit; both sides captured. |
| `logDelete` | `(tenantId, entityType, entityId, oldValues)` | Row deletion; `new_values` null. |
| `logAction` | `(tenantId, entityType, entityId, actionDescription, details)` | Non-CRUD business event. `details` becomes `new_values`; `action` key injected for the UI. |

`changedById` and `changedByRole` come from [`TenantContext`](../../backend/src/main/java/in/schoolapp/common/TenantContext.java); `ipAddress` comes from the active request (`X-Forwarded-For` first hop, then `remoteAddr`). Scheduled jobs calling the logger simply get a null IP — the row is still written.

---

## Endpoints

| Path | Method | `@PreAuthorize` | Description |
|---|---|---|---|
| `/api/v1/tenants/{tenantId}/audit?entityType=&entityId=` | GET | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')` | Per-entity history, newest first. |
| `/api/v1/tenants/{tenantId}/audit?page=&size=` | GET | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')` | Tenant-wide paginated stream (default size 50, max 200). |
| `/api/v1/tenants/{tenantId}/data-deletion-requests` | POST | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')` | DPDP intake. Validates `subjectType ∈ {STUDENT, PARENT, STAFF}` and returns `202 Accepted` with a `requestId`. |

Read controller: [`AuditLogController`](../../backend/src/main/java/in/schoolapp/audit/AuditLogController.java). DPDP intake: [`DataDeletionController`](../../backend/src/main/java/in/schoolapp/audit/DataDeletionController.java).

DPDP requests are logged via `auditLogger.logAction(tenantId, subjectType, subjectId, "DPDP_DELETION_REQUESTED", { requestId, reason, requestedByStaffId })`. The endpoint does **not** delete data inline — the controller comment is explicit: actual purging needs retention-policy orchestration + financial-record exemptions + DPO review, so the intake's only job is durably recording the request.

---

## Design decisions

- **REQUIRES_NEW + swallowed exceptions.** A missed audit row is a compliance ding; a rolled-back payment is a financial incident. We'd rather log and continue than break business writes. Every failure is ERROR-logged with entity type + id + action so gaps are observable.
- **JSONB over wide columns.** `old_values` / `new_values` are opaque maps so a new entity type requires zero schema changes. Cost: queries over historic values use `jsonb_path_ops` indexes instead of B-trees. Acceptable — audit reads are always filtered by `(entity_type, entity_id)` or `(school_id, time-range)` first.
- **Call from services, not controllers.** Controllers don't know the prior state of an entity; services do. The logger is injected into domain services and called inline after the business write commits.
- **`school_id` is nullable.** Non-tenant events (platform-level admin actions) can still be recorded. All tenant-scoped reads filter by `school_id`.
- **Per-entity query filters defensively.** The per-entity endpoint loads by natural key, then drops rows where `school_id != tenantId` (rows with null `school_id` pass through) so a leaked entity id can't expose cross-tenant history.
- **DPDP deletion is intake-only by design.** The controller comment documents the choice: building the full purge workflow is a larger slice, but every request must still land in `audit_log`.

---

## Cross-module calls

### What the audit module depends on

- [`TenantContext`](../../backend/src/main/java/in/schoolapp/common/TenantContext.java) — current staff id and role for `changed_by_*`.
- Spring `RequestContextHolder` — client IP extraction.
- [`AppException`](../../backend/src/main/java/in/schoolapp/common/AppException.java) / [`ErrorCode`](../../backend/src/main/java/in/schoolapp/common/ErrorCode.java) — DPDP validation errors.

### What depends on the audit module

`AuditLogger` is injected into domain services across the app. Observed call sites:

| Module | File | Events |
|---|---|---|
| Students | [`StudentService`](../../backend/src/main/java/in/schoolapp/student/StudentService.java) | create / update / delete + photo upload + document upload/delete |
| Payments | [`FeePaymentService`](../../backend/src/main/java/in/schoolapp/fee/FeePaymentService.java) | `quickCollect` + `createOnlinePayment` |
| Fee heads | [`FeeHeadService`](../../backend/src/main/java/in/schoolapp/fee/FeeHeadService.java) | CRUD |
| Fee reminders | [`FeeReminderScheduleController`](../../backend/src/main/java/in/schoolapp/fee/FeeReminderScheduleController.java) | schedule CRUD |
| Staff | [`StaffService`](../../backend/src/main/java/in/schoolapp/school/StaffService.java) | create / delete |
| School | [`SchoolService`](../../backend/src/main/java/in/schoolapp/school/SchoolService.java) | config update + logo upload |
| Substitute assignments | [`SubstituteTeacherService`](../../backend/src/main/java/in/schoolapp/school/SubstituteTeacherService.java) | create / cancel |
| Teacher-subject assignments | [`TeacherAssignmentService`](../../backend/src/main/java/in/schoolapp/academics/TeacherAssignmentService.java) | create / delete |
| Exam publishing | [`ExamService`](../../backend/src/main/java/in/schoolapp/academics/ExamService.java) | publish |
| Circulars | [`CircularService`](../../backend/src/main/java/in/schoolapp/communication/CircularService.java) | create |
| DPDP | [`DataDeletionController`](../../backend/src/main/java/in/schoolapp/audit/DataDeletionController.java) | `DPDP_DELETION_REQUESTED` |
| Export | [`ExportService`](../../backend/src/main/java/in/schoolapp/export_/ExportService.java) | `DATA_EXPORT` on every export |

---

## Relevant migrations

- [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) — creates `audit_log` with `(entity_type, entity_id, created_at DESC)` and `(school_id, created_at DESC)` indexes.
