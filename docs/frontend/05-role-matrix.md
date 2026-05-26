# 05 — Role Matrix

Every mutating endpoint the backend exposes declares a required role via `@PreAuthorize`. The UI must mirror that gating — a button that calls a 403-returning endpoint is broken UX.

Sources scanned (2026-04-23):
- [AppRoles](../../backend/src/main/java/in/schoolapp/auth/AppRoles.java) — role-group constants
- [StaffRole](../../backend/src/main/java/in/schoolapp/school/entity/StaffRole.java) — enum values
- Every `*Controller.java` under [backend/src/main/java/in/schoolapp/](../../backend/src/main/java/in/schoolapp/) — actual `@PreAuthorize` usage

---

## The roles

Enum order matters (JWT claim serialises the name). No role has an implicit hierarchy — each `@PreAuthorize` lists the roles it accepts, and membership is checked explicitly.

| Role | 1-line description (from [StaffRole.java](../../backend/src/main/java/in/schoolapp/school/entity/StaffRole.java)) |
|---|---|
| `SUPER_ADMIN` | Platform-level (our team). Not used by tenant UIs. |
| `SCHOOL_OWNER` | Full access, owner of the school. |
| `PRINCIPAL` | Same as `SCHOOL_OWNER` for Phase 1 — operational lead. |
| `ADMIN` | All except school deletion + pricing. |
| `CLASS_TEACHER` | Own section: all; other sections: read-only. |
| `SUBJECT_TEACHER` | Marks entry for assigned subject/section only. |
| `ACCOUNTANT` | Fee module only. |
| `VIEWER` | Read-only, no PII phone numbers. |

Spring Security stores each authority as `ROLE_<NAME>` (the `ROLE_` prefix is added by [JwtAuthFilter](../../backend/src/main/java/in/schoolapp/auth/JwtAuthFilter.java)). `@PreAuthorize("hasAnyRole('X','Y')")` strips the prefix when comparing.

---

## Role-group constants

Convenience constants referenced by most controllers. Found in [AppRoles.java](../../backend/src/main/java/in/schoolapp/auth/AppRoles.java).

| Constant | Expression | Accepts |
|---|---|---|
| `AppRoles.OWNER_OR_ADMIN` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN')` | owner / principal / admin |
| `AppRoles.ANY_TEACHER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')` | owner / principal / admin / both teachers |
| `AppRoles.FEE_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','ACCOUNTANT')` | owner / principal / admin / accountant |
| `AppRoles.ATTENDANCE_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER')` | owner / principal / admin / class teacher |
| `AppRoles.MARKS_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')` | owner / principal / admin / both teachers |

Two controllers use a hard-coded expression that is **narrower** than `OWNER_OR_ADMIN` — it rejects `ADMIN`:

- `DELETE /api/v1/tenants/{tenantId}/staff/{staffId}` — `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')`
- `GET /api/v1/tenants/{tenantId}/audit` — `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')`
- `POST /api/v1/tenants/{tenantId}/data-deletion-requests` — `hasAnyRole('SCHOOL_OWNER','PRINCIPAL')`

---

## Legend

- ✅ — allowed
- ❌ — rejected (server returns `403 FORBIDDEN`)
- "read" — endpoint is a `GET` with no `@PreAuthorize`, so it's open to **any authenticated tenant user** (read-only column where it differs from a write column)

"Any authenticated user" in this doc means: any user with a valid JWT whose `tenantId` claim matches the URL's `{tenantId}`. The tenant check is enforced by `TenantInterceptor`, not `@PreAuthorize`.

Role columns below (abbreviated for width): **OWN** (`SCHOOL_OWNER`), **PRI** (`PRINCIPAL`), **ADM** (`ADMIN`), **CT** (`CLASS_TEACHER`), **ST** (`SUBJECT_TEACHER`), **ACC** (`ACCOUNTANT`), **VW** (`VIEWER`).

---

## Capability matrix

### Auth (public — no column applies)

| Endpoint | Notes |
|---|---|
| `POST /api/v1/auth/otp/send` | Public |
| `POST /api/v1/auth/otp/verify` | Public |
| `POST /api/v1/auth/token/refresh` | Public |
| `POST /api/v1/auth/logout` | Public |

### Tenant / school / onboarding

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants` | Public | Public | Public | Public | Public | Public | Public |
| `GET /api/v1/tenants/{tenantId}` | read | read | read | read | read | read | read |
| `PUT /api/v1/tenants/{tenantId}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/logo` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/onboarding-status` | read | read | read | read | read | read | read |

### Classes + sections

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/classes/bulk` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/classes` | read | read | read | read | read | read | read |

### Staff

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/staff` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/staff` | read | read | read | read | read | read | read |
| `DELETE /api/v1/tenants/{tenantId}/staff/{staffId}` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Substitute teachers

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/substitutes` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/substitutes` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `DELETE /api/v1/tenants/{tenantId}/substitutes/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Students (incl. documents, photo, timeline)

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/students` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/students` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}` | read | read | read | read | read | read | read |
| `PUT /api/v1/tenants/{tenantId}/students/{studentId}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `DELETE /api/v1/tenants/{tenantId}/students/{studentId}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}/timeline` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/students/{studentId}/photo` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/students/{studentId}/documents` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}/documents` | read | read | read | read | read | read | read |
| `DELETE /api/v1/tenants/{tenantId}/students/documents/{documentId}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Attendance

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}/attendance` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/attendance/summary` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/attendance/unmarked` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/attendance/chronic` | read | read | read | read | read | read | read |

### Fees (payments, invoices, dashboard, reminders)

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/fees/payments` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/v1/tenants/{tenantId}/fees/payments/{paymentId}` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/fees/invoices` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `POST /api/v1/tenants/{tenantId}/fees/opening-balances` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}/fee-summary` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/fees/dashboard` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/fees/defaulters` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/fees/reminders` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |

### Fee heads

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/fee-heads` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/v1/tenants/{tenantId}/fee-heads` | read | read | read | read | read | read | read |
| `PUT /api/v1/tenants/{tenantId}/fee-heads/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `DELETE /api/v1/tenants/{tenantId}/fee-heads/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |

### Fee reminder schedules

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/fee-reminder-schedules` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/fee-reminder-schedules` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `PUT /api/v1/tenants/{tenantId}/fee-reminder-schedules/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `DELETE /api/v1/tenants/{tenantId}/fee-reminder-schedules/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `POST /api/v1/tenants/{tenantId}/fee-reminder-schedules/run-now` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |

### Academics — subjects, exams, marks, report cards

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/subjects/bulk` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/subjects` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/exams` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/exams` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/exams/{examId}/publish` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/exams/{examId}/marks/{sectionId}` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/exams/{examId}/marks` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/exams/{examId}/completion/{sectionId}` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/exams/{examId}/report-cards/generate/{sectionId}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/students/{studentId}/report-card/{examId}` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/sections/{sectionId}/exam-eligibility` | read | read | read | read | read | read | read |

### Teacher assignments

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/teacher-assignments` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/teacher-assignments` | read | read | read | read | read | read | read |
| `DELETE /api/v1/tenants/{tenantId}/teacher-assignments/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Analytics — dashboard + alerts

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/dashboard` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/alerts` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/alerts/{alertId}/dismiss` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Communication — inbox

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/inbox/mine` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/inbox/mine/unread-count` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/inbox/unrouted` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/inbox` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/inbox/{messageId}/read` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/inbox/{messageId}/resolve` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/inbox/{messageId}/reassign` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Communication — circulars

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/circulars` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/circulars` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/circulars/{id}` | read | read | read | read | read | read | read |

### Migration (OCR / LLM imports)

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/migration` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/migration` | read | read | read | read | read | read | read |
| `GET /api/v1/tenants/{tenantId}/migration/{jobId}` | read | read | read | read | read | read | read |
| `POST /api/v1/tenants/{tenantId}/migration/{jobId}/commit` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Mobile sync

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/sync/pull` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/v1/tenants/{tenantId}/sync/push` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Exports

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/export/students` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/v1/tenants/{tenantId}/export/fees` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/v1/tenants/{tenantId}/export/attendance` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Audit log

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/v1/tenants/{tenantId}/audit` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### DPDP data-deletion requests

| Endpoint | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `POST /api/v1/tenants/{tenantId}/data-deletion-requests` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Webhooks (no JWT — HMAC signatures)

| Endpoint | Notes |
|---|---|
| `POST /webhooks/whatsapp` | HMAC-SHA256 via `X-WA-Signature` |
| `POST /webhooks/stripe` | HMAC-SHA256 via `Stripe-Signature`, 5-min replay window |
| `POST /webhooks/razorpay` | HMAC-SHA256 via `X-Razorpay-Signature` |

### Files (LOCAL storage)

| Endpoint | Notes |
|---|---|
| `GET /files/**` | Unauthenticated — path segments contain unguessable UUIDs |

---

## Self-service role exceptions

Read (`GET`) endpoints that lack an explicit `@PreAuthorize` are open to **any authenticated user** whose JWT's `tenantId` claim matches the URL's `{tenantId}`. The tenant boundary is enforced by [TenantInterceptor](../../backend/src/main/java/in/schoolapp/common/), not by method security. The matrix shows "read" for those columns.

Writes without `@PreAuthorize` do not exist in the codebase — every mutation is guarded. If you find one without, treat it as a backend bug.

---

## Frontend gating guidance

Per [00-stack-and-conventions.md](00-stack-and-conventions.md): **hide** buttons/links the current role cannot use. Don't render them as disabled, because disabled controls are still discoverable and imply "you could if you were more permissioned" — that leaks organisational structure and confuses support scripts.

### `RequireRole` component

```tsx
// src/auth/RequireRole.tsx
import { ReactNode } from 'react';
import { useAuth } from './useAuth';
import type { StaffRole } from '@/api/types';

interface Props {
  roles: StaffRole[];
  children: ReactNode;
  /** Optional fallback when user lacks role. Default: render nothing. */
  fallback?: ReactNode;
}

export function RequireRole({ roles, children, fallback = null }: Props) {
  const { user } = useAuth();
  if (!user) return null;
  return roles.includes(user.role) ? <>{children}</> : <>{fallback}</>;
}
```

Usage:
```tsx
<RequireRole roles={['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN']}>
  <Button onClick={openCreateStudentDialog}>New student</Button>
</RequireRole>
```

### `useHasRole` hook

For composable checks inside render logic (e.g. conditional columns in a table):

```ts
// src/auth/useHasRole.ts
import { useAuth } from './useAuth';
import type { StaffRole } from '@/api/types';

export function useHasRole(...allowed: StaffRole[]): boolean {
  const { user } = useAuth();
  return !!user && allowed.includes(user.role);
}
```

Usage:
```tsx
const canDismissAlert = useHasRole('SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN');
return (
  <AlertCard>
    …
    {canDismissAlert && <Button onClick={dismiss}>Dismiss</Button>}
  </AlertCard>
);
```

### Canonical role-group helpers

Match the backend's constants (reduces drift risk when a new role is added):

```ts
// src/auth/roleGroups.ts
import type { StaffRole } from '@/api/types';

export const OWNER_OR_ADMIN: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN'];

export const ANY_TEACHER: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER'];

export const FEE_WRITER: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'ACCOUNTANT'];

export const ATTENDANCE_WRITER: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER'];

export const MARKS_WRITER: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER'];

/** The two narrow cases that reject ADMIN. */
export const OWNER_OR_PRINCIPAL: StaffRole[] =
  ['SCHOOL_OWNER', 'PRINCIPAL'];
```

Prefer passing the constant to `RequireRole`:

```tsx
import { OWNER_OR_ADMIN } from '@/auth/roleGroups';

<RequireRole roles={OWNER_OR_ADMIN}>
  <Button>Create invoice</Button>
</RequireRole>
```

### "Hide vs disable" rule — worked examples

**Correct (hide):**
```tsx
<RequireRole roles={OWNER_OR_PRINCIPAL}>
  <Button onClick={deactivateStaff}>Deactivate staff member</Button>
</RequireRole>
```

**Wrong (disable — leaks the button's existence):**
```tsx
<Button disabled={!useHasRole(...OWNER_OR_PRINCIPAL)} onClick={deactivateStaff}>
  Deactivate staff member
</Button>
```

Exception: for **form fields in a shared dialog** where the action as a whole is available but one field isn't editable by the current role, you may `readOnly` that field rather than hiding it — users shouldn't discover mysteriously-missing form fields.

### Route-level guarding

```tsx
// src/router.tsx
<Route
  path="tenants/:tenantId/fees/*"
  element={
    <RequireRole roles={FEE_WRITER} fallback={<Navigate to="../dashboard" replace />}>
      <FeesLayout />
    </RequireRole>
  }
/>
```

Route guards protect *pages*; in-page `RequireRole` protects *buttons + sections*. Use both.

---

## Tenant boundary (not a role check)

The JWT's `tenantId` claim is compared against the URL's `{tenantId}` path segment on every request by [TenantInterceptor](../../backend/src/main/java/in/schoolapp/common/). A mismatch returns `403 FORBIDDEN` *before* any `@PreAuthorize` check runs. The frontend mirror is:

```tsx
const { tenantId: urlTenant } = useParams<{ tenantId: string }>();
const { user } = useAuth();
useEffect(() => {
  if (user && urlTenant && user.schoolId !== urlTenant) {
    navigate('/login', { replace: true });
  }
}, [user, urlTenant, navigate]);
```

This catches the user swapping the URL to a tenant they don't own; the backend will already reject their requests, but the redirect prevents a broken page state.

---

## Regenerating this matrix

When a controller's `@PreAuthorize` changes, or a new endpoint is added, re-scan:

```bash
grep -rn "@PreAuthorize" backend/src/main/java/in/schoolapp/
```

Cross-reference the expression against [AppRoles.java](../../backend/src/main/java/in/schoolapp/auth/AppRoles.java) to map the constant back to a role list, and update the matrix row. The endpoint list itself should already be in sync with [02-api-reference.md](02-api-reference.md).
