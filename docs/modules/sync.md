# `sync` module

Mobile sync API for the teacher's offline-first attendance app. Two routes: a delta pull for reference data, a batch push for queued attendance entries.

**Package:** `in.schoolapp.sync`

---

## Purpose

Teachers take attendance on their phones, sometimes in basements and playgrounds where the network is flaky. The mobile app writes to a local SQLite queue and syncs when it reconnects. This module is the server side of that contract: one `GET /pull` returns everything the phone needs to show the attendance screen (current academic year, sections, delta-filtered students), and one `POST /push` applies a batch of queued attendance rows with per-entry results so the client can reconcile row-by-row. No new entities — sync reuses `attendance_records`, `students`, `sections`, `student_enrollments`, and `academic_years`.

---

## Entities / tables

None. The module persists through existing domain entities.

---

## DTOs

### [`SyncPullResponse`](../../backend/src/main/java/in/schoolapp/sync/dto/SyncPullResponse.java)

```
serverTime        — wall-clock of this response; client stores it and sends as next `since`
academicYear      — current year DTO, or null if none marked current for the tenant
sections[]        — full list for the current year (no updated_at column yet; volume is tiny)
students[]        — delta-filtered by updatedAt >= since (full set when since is null)
  .currentSectionId  — resolved from most-recent StudentEnrollment
studentCount      — for mobile-side progress bars
```

### [`SyncPushRequest`](../../backend/src/main/java/in/schoolapp/sync/dto/SyncPushRequest.java)

List of `AttendanceEntry` with `localId` (client UUID), `sectionId`, `studentId`, `date`, `status`, optional `arrivalTime`, optional `note`. Bean-validation caps the batch at **1000** entries (`@Size(max = 1000)`).

### [`SyncPushResponse`](../../backend/src/main/java/in/schoolapp/sync/dto/SyncPushResponse.java)

`accepted`, `rejected`, and a list of `EntryResult(localId, serverId, status, error)` with status ∈ {`CREATED`, `UPDATED`, `REJECTED`}. The `localId` echoes what the client sent so the phone can correlate without rescanning by natural key.

---

## Services + key public methods

### [`SyncPullService`](../../backend/src/main/java/in/schoolapp/sync/SyncPullService.java)

- `pull(tenantId, since)` — `@Transactional(readOnly = true)`. Reads current academic year, all sections in that year, and students with `updated_at > since` (or all active on the very first pull). `currentSectionId` for each student comes from the most recent `StudentEnrollment`.
- No N+1 by design: enrollment lookups hang off a `HashMap<studentId, sectionId>` built once per pull.
- Graceful fallback when no current academic year exists — returns `academicYear = null`, empty `sections`, and still delivers the student delta.

### [`SyncPushService`](../../backend/src/main/java/in/schoolapp/sync/SyncPushService.java)

- `apply(tenantId, req)` — orchestrator; iterates entries and delegates each to `upsertOne`. Runs **without** an outer transaction so per-entry rollbacks can't poison the batch.
- `upsertOne(tenantId, entry)` — `@Transactional(propagation = REQUIRES_NEW)`. Upserts via the `(student_id, date)` unique constraint; returns `CREATED` on insert, `UPDATED` on edit, or `REJECTED` with a reason.

Validation guards (each `REJECTED` with a distinct reason string):

- Duplicate `localId` **within** the batch — tracked in a `Set<UUID>` in the orchestrator.
- `date` in the future — paranoia against clock-skewed phones.
- Section must resolve to this tenant (via `ClassSectionService.getSectionOrThrow`).
- Student must be enrolled in the claimed section — scans `StudentEnrollment` by student, matches on `sectionId`. Guards against phones with stale section ids after a transfer.
- Existing `attendance_records` row's `school_id` must equal `tenantId` — defensive cross-tenant check on the upsert path (the unique index already scopes via student, but a compromised JWT shouldn't be able to cross the boundary here).

---

## Endpoints

| Path | Method | `@PreAuthorize` | Description |
|---|---|---|---|
| `/api/v1/tenants/{tenantId}/sync/pull?since=ISO_DATE_TIME` | GET | `AppRoles.ANY_TEACHER` | Delta snapshot: serverTime, current academic year, sections, changed students. |
| `/api/v1/tenants/{tenantId}/sync/push` | POST | `AppRoles.ATTENDANCE_WRITER` | Batch upsert of attendance entries; returns per-entry results (200 OK body, not 207 — see design below). |

Controller: [`SyncController`](../../backend/src/main/java/in/schoolapp/sync/SyncController.java).

---

## Design decisions

- **Per-entry `REQUIRES_NEW` transaction.** One bad row (wrong section, future date, cross-tenant) rejects only itself — the rest of the batch still commits. Spring self-invocation rules force the split across two methods (`apply` → `upsertOne`); both live on the same bean so the proxy boundary actually kicks in.
- **Idempotent on `(student_id, date)`.** The mobile client can re-send a batch after a network glitch and the server reports `UPDATED` rather than throwing on a unique-constraint violation. No client-side dedup needed.
- **Duplicate-`localId` guard is in-memory per batch.** The unique constraint is on `(student_id, date)` not on `localId`, so two entries in the same batch with different `localId`s pointing at the same student+date can both succeed (second wins, both get `UPDATED`). A single `localId` appearing twice is a client bug and is rejected.
- **`localId` is the phone's contract.** The server doesn't persist it; it only echoes it back so the mobile app can key back to its SQLite row without natural-key rescans. The phone typically generates a UUID when it first queues the row.
- **200 OK with per-entry results, not HTTP 207.** Multi-status (207) isn't widely supported by mobile HTTP clients and the body already carries everything the client needs. Accepted/rejected counts are on the body.
- **Pull is delta on students only.** Sections + academic year are returned in full every pull because neither has an `updated_at` column and the volume is tiny (typically < 100 section rows per school). The mobile app replaces its local copies of those two on every pull.
- **First pull returns all active students.** No pagination, no "initial bootstrap" endpoint — Phase 1 schools are a few hundred students and the round-trip is acceptable. A future large-school release will need chunking.
- **Mobile contract recap.** Client stores `serverTime` from the pull response and sends it verbatim as the next `since`; push `EntryResult.localId` keys back to the phone's SQLite rows.

---

## Cross-module calls

### Depends on

- [`attendance`](attendance.md) — `AttendanceRecord`, `AttendanceRepository` (upsert by `(studentId, date)`).
- [`school`](school.md) — `AcademicYearService.findCurrent`, `SectionRepository`, `ClassSectionService.getSectionOrThrow` (tenant-scoped section lookup).
- [`student`](student.md) — `StudentRepository` (delta query by `updatedAt`), `StudentEnrollmentRepository` (current-section resolution, section-membership guard).
- [`auth`](auth.md) — `AppRoles` constants.
- [`common`](common.md) — `TenantContext` for `markedById` on synced rows.

### Depended on by

No backend consumers — this module is the mobile app's API contract; it's consumed exclusively by the teacher mobile client.

---

## Relevant migrations

None specific to sync. The `attendance_records` table (with the `(student_id, date)` unique constraint that makes push idempotent) is defined in [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql); the `synced_from_mobile` column on the same table is the only sync-aware field on the server.
