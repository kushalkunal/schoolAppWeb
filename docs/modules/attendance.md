# `attendance` module

Reverse-marking daily attendance, sibling-aware absence alerts, and the read-side analytics that power the principal dashboard and scheduled detectors.

**Package:** `in.schoolapp.attendance`

---

## Purpose

Indian schools mark attendance in under two minutes, and on most days 90%+ of students are present. Listing the whole roster and ticking "present" 40 times is the wrong UX. The write model is instead **reverse marking**: every enrolled student is PRESENT by default, and the teacher lists only the exceptions (absent / late / half-day / leave). Submitted entries upsert per `(student_id, date)` so mobile offline sync can re-submit idempotently. After commit, an `AttendanceSubmittedEvent` fans out to two async consumers — `AbsenceAlertService` (sibling-aware WhatsApp alerts) and detectors in the `analytics` module. A read-only `AttendanceAnalyticsService` joins across sections, enrollments, and staff to render dashboard-ready DTOs (summary tiles, unmarked-section list, chronic absentees).

---

## Entities and tables

| Entity | Table | Notes |
|---|---|---|
| [`AttendanceRecord`](../../backend/src/main/java/in/schoolapp/attendance/entity/AttendanceRecord.java) | `attendance_records` (V1) | Unique on `(student_id, date)`. Columns include `status`, `arrival_time` (LATE only), `marked_by_id`, `note`, `is_historical`, `synced_from_mobile`. |
| [`AttendanceStatus`](../../backend/src/main/java/in/schoolapp/attendance/entity/AttendanceStatus.java) | enum | `PRESENT \| ABSENT \| LATE \| HALF_DAY \| LEAVE`. |

---

## Services and key methods

| File | Key methods | Notes |
|---|---|---|
| [`AttendanceService`](../../backend/src/main/java/in/schoolapp/attendance/AttendanceService.java) | `submitAttendance(tenantId, sectionId, req)`, `createHistorical(...)`, `getSectionAttendance(...)`, `getStudentHistory(...)` | Write path. `submitAttendance` fetches the ACTIVE roster, validates every submitted studentId is in that roster (prevents cross-section injection via a JWT scoped to a different section), upserts every roster row, and publishes `AttendanceSubmittedEvent`. `createHistorical` is called by the migration flow — marks `is_historical=true` and suppresses the event so imported rows don't trigger alerts. |
| [`AbsenceAlertService`](../../backend/src/main/java/in/schoolapp/attendance/AbsenceAlertService.java) | `onAttendanceSubmitted(event)` — `@Async("notificationExecutor") @EventListener` | Sibling-aware: `FamilyService.groupStudentsByPrimaryParent` buckets absent students per primary parent; one combined message is sent when a parent has 2+ absent children, one per child otherwise. Late alerts are always per-child because parents expect specifics. Orphaned students (no primary parent) are logged and skipped. |
| [`AttendanceAnalyticsService`](../../backend/src/main/java/in/schoolapp/attendance/AttendanceAnalyticsService.java) | `schoolSummary(tenantId, date)`, `unmarkedSections(tenantId, date)`, `chronicAbsentees(tenantId, windowDays, minAbsences)`, `resolveTeachersForSections(sections)` | Read-only. Deliberately separate from `AttendanceService` so the write path stays small and transactional. `resolveTeachersForSections` returns `(section, teacher)` pairs filtered to only sections with a configured teacher phone — used by `analytics.AttendanceNotSubmittedDetector` to WhatsApp each teacher directly. |
| [`AttendanceSubmittedEvent`](../../backend/src/main/java/in/schoolapp/attendance/event/AttendanceSubmittedEvent.java) | record | Carries tenant id, section id, date, absent + late record lists, and submitter staff id. |

### Repository

[`AttendanceRepository`](../../backend/src/main/java/in/schoolapp/attendance/repository/AttendanceRepository.java) is a JPA repo plus three native queries:

- `findStudentsWithAbsencesInWindow(schoolId, from, to, threshold)` — absence counts over a window, filtered to students with `≥ threshold`. Used by `analytics.ConsecutiveAbsenceDetector` as a practical substitute for true consecutive-run SQL (window functions don't help the multi-tenant optimiser here, and "absent on all 3 recent marked days" is equivalent for the typical 3-day window).
- `findChronicAbsentees(schoolId, from, to, minAbsences)` — `(studentId, absentCount)` tuples ordered by count desc, projected via `ChronicAbsenteeRow`. Drives the principal's chronic-absentees endpoint.
- `countPerStudentInWindow(schoolId, from, to)` — `(studentId, totalCount, absentCount)` tuples over the window. Feeds at-risk scoring; the detector computes the percentage itself so different calling policies can apply.

Plus derived-query methods for idempotent upsert (`findByStudentIdAndDate`), section roll (`findBySchoolIdAndSectionIdAndDate`), per-status count tiles (`countBySchoolIdAndDateAndStatus`), paged export iteration, and the "was today marked?" checks used by analytics.

---

## Reverse marking — submit flow

```json
POST /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance
{
  "date": "2026-04-23",
  "entries": [
    { "studentId": "<rohan>", "status": "ABSENT" },
    { "studentId": "<priya>", "status": "LATE",
      "arrivalTime": "2026-04-23T09:15:00+05:30" }
  ]
}
```

1. Fetch `ACTIVE` enrollments for the section — that's the baseline.
2. Index the submitted entries by `studentId`. Reject the request if any submitted id is not in the roster.
3. For every roster student: override status if present in the submitted map, else `PRESENT`. Upsert per `(studentId, date)`.
4. Publish `AttendanceSubmittedEvent` with separate lists for absent + late records.
5. Return per-status counts + the upserted rows + `notificationsSent` (absent + late count).

---

## Endpoints

All under `/api/v1/tenants/{tenantId}` — see [`AttendanceController`](../../backend/src/main/java/in/schoolapp/attendance/AttendanceController.java).

| Path | Method | `@PreAuthorize` |
|---|---|---|
| `/sections/{sectionId}/attendance` | POST | `ATTENDANCE_WRITER` |
| `/sections/{sectionId}/attendance?date=YYYY-MM-DD` | GET | (authenticated) |
| `/students/{studentId}/attendance?from=&to=` | GET | (authenticated) |
| `/attendance/summary?date=` | GET | (authenticated) |
| `/attendance/unmarked?date=` | GET | (authenticated) |
| `/attendance/chronic?windowDays=30&minAbsences=5` | GET | (authenticated) |

`ATTENDANCE_WRITER` = `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER')` — see [`AppRoles`](../../backend/src/main/java/in/schoolapp/auth/AppRoles.java). Read endpoints inherit the module-wide "authenticated" default from [`SecurityConfig`](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java).

---

## Design decisions

- **Upsert, not insert.** A mobile client that falls offline, re-submits on reconnect, and then the user tweaks one exception and re-submits again — all three calls must converge. Idempotent per `(student_id, date)`.
- **Cross-section injection defence.** Before writing, every submitted studentId is checked against the section's current ACTIVE roster. A JWT scoped to Section A cannot sneak an entry for a student enrolled in Section B.
- **Historical rows skip the event.** `createHistorical` never publishes `AttendanceSubmittedEvent` — bulk-importing a year of paper registers must not page thousands of parents with "your child was absent (7 months ago)".
- **Late alerts stay per-child.** Parents expect a specific child + arrival time. The sibling-combined template is only used for absence, where "Rohan and Priya were both absent today" is actually the preferred wording.
- **Analytics is a separate service.** The write path is hot, transactional, and narrowly scoped. The dashboard joins across sections, classes, staff, and enrollments — keeping them apart avoids accidentally pulling read-side beans into the write path's classpath.
- **`unmarkedSections` uses a native query in `SectionRepository`**, not this module's repository. The "section exists AND no attendance row today" predicate belongs to the section entity, not the attendance entity.
- **Sibling grouping lives in `student.FamilyService`.** `AbsenceAlertService` doesn't contain parent-resolution logic; it delegates to `FamilyService.groupStudentsByPrimaryParent(List<Student>) → Map<UUID, List<Student>>`.

---

## Cross-module calls

- **Reads:** `school.ClassSectionService` (section lookup + tenant check), `school.SectionRepository` (unmarked-sections native query), `school.SchoolClassRepository` (class name batch-fetch for dashboard DTOs), `school.StaffRepository` (teacher name batch-fetch), `student.StudentEnrollmentRepository` (roster + most-recent enrollment for chronic absentee DTOs), `student.StudentRepository`, `student.FamilyService`, `school.SchoolService` (school name for alert templates).
- **Dispatches via `communication`:** `WhatsAppNotifier` (via the `AuditingWhatsAppNotifier` decorator → `notification_log`), `MessageTemplateService`.
- **Publishes events:** `AttendanceSubmittedEvent`.
- **Consumed by:** `communication` indirectly — `AbsenceAlertService` is the consumer living in this package. `analytics` module's detectors (`AttendanceNotSubmittedDetector`, `ConsecutiveAbsenceDetector`, at-risk scoring) call the repository + `AttendanceAnalyticsService` directly; they do not consume the event.

---

## Migrations

- **V1** — creates `attendance_records` with the `(student_id, date)` uniqueness constraint plus supporting indexes on `(school_id, date)` and `(section_id, date)`. All attendance behaviour is served by V1; no subsequent migration has touched this table.
