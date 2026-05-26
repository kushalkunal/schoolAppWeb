# `school` module

The tenant itself — `School`, `AcademicYear`, `Classes`, `Sections`, `Staff`, plus daily substitute-teacher scheduling.

**Package:** `in.schoolapp.school`

---

## Entities

| Entity | Table | Notes |
|---|---|---|
| [`School`](../../backend/src/main/java/in/schoolapp/school/entity/School.java) | `schools` | Tenant root — does NOT extend `BaseEntity` (it IS the tenant). `settings` is a JSONB column holding onboarding flags, `receiptSequence`, and `minAttendancePct`. |
| [`AcademicYear`](../../backend/src/main/java/in/schoolapp/school/entity/AcademicYear.java) | `academic_years` | One "current" per tenant via partial unique index on `is_current`. |
| [`SchoolClass`](../../backend/src/main/java/in/schoolapp/school/entity/SchoolClass.java) | `school_classes` | Named `SchoolClass` because `class` is reserved. Unique per `(school_id, name)`. |
| [`Section`](../../backend/src/main/java/in/schoolapp/school/entity/Section.java) | `sections` | Unique per `(school_id, class_id, academic_year_id, name)`. |
| [`Staff`](../../backend/src/main/java/in/schoolapp/school/entity/Staff.java) | `staff` | Login subject. Phone + email are globally unique (partial indexes in V2). |
| [`SubstituteAssignment`](../../backend/src/main/java/in/schoolapp/school/entity/SubstituteAssignment.java) | `substitute_assignments` | Unique per `(section_id, assigned_date)` — two substitutes cannot be scheduled for the same class on the same day. |
| [`StaffRole`](../../backend/src/main/java/in/schoolapp/school/entity/StaffRole.java) | enum | `SUPER_ADMIN`, `SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `CLASS_TEACHER`, `SUBJECT_TEACHER`, `ACCOUNTANT`, `VIEWER` |
| [`Board`](../../backend/src/main/java/in/schoolapp/school/entity/Board.java) | enum | `CBSE`, `ICSE`, `STATE`, `IGCSE`, `OTHER` |

---

## Services

### [`SchoolService`](../../backend/src/main/java/in/schoolapp/school/SchoolService.java)
Owns the `schools` row lifecycle.
- `createSchool(req)` — public signup, transactional. Creates the `School`, a `PRINCIPAL` `Staff` record, and the current `AcademicYear` in one unit. Accepted identifiers depend on the `app.signup.channel` flag (`PHONE` | `EMAIL` | `BOTH`); identifiers are globally unique across all schools and staff. Returns `nextStep` so the client knows which OTP flow to invoke.
- `updateSchool(tenantId, req)` — patch-style `PUT`. Only non-null fields in `UpdateSchoolRequest` are applied (`name`, `principalName`, `address`, `city`, `state`, `pincode`, `whatsappNumber`). Identifier changes (phone/email/board) are deliberately refused — they would require a fresh OTP verification.
- `uploadLogo(tenantId, bytes, contentType)` — stores the logo at the deterministic key `logos/{tenantId}/logo{ext}` via `FileStorageService`, so a subsequent upload overwrites the previous file. Writes `logo_url` back to the school row.
- `getSchool(id)` / `getSchoolEntity(id)` — reads.

### [`AcademicYearService`](../../backend/src/main/java/in/schoolapp/school/AcademicYearService.java)
- `createCurrentYearForSchool(schoolId)` — derives the April–March window from today's month (Jan–Mar uses last year as the start).
- `getCurrentOrThrow(schoolId)` / `findCurrent(schoolId)` — the throwing variant is used by write paths; the `Optional` variant is used by sync so an un-onboarded tenant still gets a usable (empty) pull.

### [`ClassSectionService`](../../backend/src/main/java/in/schoolapp/school/ClassSectionService.java)
- `bulkCreate(schoolId, req)` — idempotent upsert of classes + sections against the current academic year; re-running with the same payload reuses existing rows.
- `listClasses(schoolId)` — joins each class with its sections for the current year.
- `getSectionOrThrow(schoolId, sectionId)` — tenant-boundary helper used across modules.

### [`StaffService`](../../backend/src/main/java/in/schoolapp/school/StaffService.java)
- `createStaff(schoolId, req)` — enforces global phone uniqueness, persists, fires an audit row, then **fire-and-forgets a WhatsApp welcome+login-instructions invite** via `WhatsAppNotifier` (`EMERGENCY` class so it bypasses quiet hours). A BSP outage must not block the create transaction.
- `listStaff(schoolId)` — active staff only.
- `deactivateStaff(schoolId, staffId)` — soft-delete via `active=false`; the `PRINCIPAL` is explicitly refused here.

### [`OnboardingService`](../../backend/src/main/java/in/schoolapp/school/OnboardingService.java)
Computes status from live entity state (class count, non-principal staff count, `wa_configured` flag) — not from stored `settings.onboarding` flags that can drift. Returns a percent-complete, step list, and pending step keys.

### [`SubstituteTeacherService`](../../backend/src/main/java/in/schoolapp/school/SubstituteTeacherService.java)
One-day substitute scheduling (LLD §5.5).
- `assign(tenantId, req)` — validates `absentTeacherId != substituteId`, both belong to the tenant, and no existing assignment for `(section_id, assigned_date)`. Persists, audits, and **WhatsApps the substitute** before homeroom so they find out in time.
- `listForDate(tenantId, date)` — populates the principal's daily dashboard.
- `listForTeacherOnDate(tenantId, substituteId, date)` — helper for a teacher's "what am I covering today" view.
- `cancel(tenantId, assignmentId)` — hard-delete the row (a one-day scheduling record has no long-term value); audit captures who/when.

---

## Endpoints

All routes live under `/api/v1/tenants`. The `TenantInterceptor` validates the path `tenantId` against the caller's JWT claim — controllers do not re-check.

| Method | Path | `@PreAuthorize` |
|---|---|---|
| POST | `/api/v1/tenants` | **public** (signup) |
| GET | `/api/v1/tenants/{tenantId}` | JWT |
| PUT | `/api/v1/tenants/{tenantId}` | `OWNER_OR_ADMIN` |
| POST | `/api/v1/tenants/{tenantId}/logo` (multipart) | `OWNER_OR_ADMIN` |
| GET | `/api/v1/tenants/{tenantId}/onboarding-status` | JWT |
| POST | `/api/v1/tenants/{tenantId}/classes/bulk` | `OWNER_OR_ADMIN` |
| GET | `/api/v1/tenants/{tenantId}/classes` | JWT |
| POST | `/api/v1/tenants/{tenantId}/staff` | `OWNER_OR_ADMIN` |
| GET | `/api/v1/tenants/{tenantId}/staff` | JWT |
| DELETE | `/api/v1/tenants/{tenantId}/staff/{staffId}` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')` |
| POST | `/api/v1/tenants/{tenantId}/substitutes` | `OWNER_OR_ADMIN` |
| GET | `/api/v1/tenants/{tenantId}/substitutes?date=` | `ANY_TEACHER` |
| DELETE | `/api/v1/tenants/{tenantId}/substitutes/{assignmentId}` | `OWNER_OR_ADMIN` |

---

## Key design decisions

- **Phone + email are globally unique across all staff and all schools** (V2 partial unique indexes). Phone is nullable so an `EMAIL`-only signup is possible; email is nullable so a `PHONE`-only signup works. The pair drives login identity — collision would be a security hole.
- **`School` is the tenant root**, not scoped under something else, so it deliberately does not extend `BaseEntity` and has no `school_id` of its own.
- **Signup is transactional and all-or-nothing** — `School` + principal `Staff` + `AcademicYear` are committed together; a failure in any step rolls back the whole onboarding.
- **Indian academic year (April–March) is auto-derived** from today's month — no day-one config.
- **Onboarding status is computed from live entity state** — the progress bar stays truthful even if `schools.settings.onboarding` flags drift from reality.
- **Substitute assignments are unique per `(section_id, assigned_date)`** so the UI can never schedule two subs for one class; cancel is a hard-delete (audit keeps history).
- **Logos and later uploads overwrite the same storage key** — no orphan accumulation, no GC pass needed.
- **Staff creation WhatsApps the new hire** with login instructions — the most common "how do I get in?" support ticket is eliminated before it's asked.

---

## Relationships to other modules

- **Reads from `common`:** `TenantContext`, `PhoneNormalizer`, `EmailNormalizer`, `AppException`/`ErrorCode`
- **Reads from `communication`:** `WhatsAppNotifier` + `WhatsAppMessage` (staff invite + substitute notify)
- **Reads from `storage`:** `FileStorageService` (logo upload)
- **Reads from `audit`:** `AuditLogger`
- **Written to by:** every domain module — `student`, `attendance`, `fee`, `academics`, `migration`, `analytics`, `sync` all start from `tenantId` and look up `School`/`AcademicYear`/`Section`/`Staff` through these services.
- **Events:** none published, none consumed.

---

## Related migrations

- **V1** — creates `schools`, `academic_years` (partial unique on `is_current`), `school_classes`, `sections`, `staff` (`UNIQUE(school_id, phone)`), `substitute_assignments` (`UNIQUE(section_id, assigned_date)`), plus indexes `idx_sub_assign_school` and `idx_sub_assign_teacher`.
- **V2** — drops the `NOT NULL` on `schools.phone` and `staff.phone` to support email-only signup; adds the global partial unique indexes `uq_staff_phone_global`, `uq_staff_email`, `uq_schools_email`.
