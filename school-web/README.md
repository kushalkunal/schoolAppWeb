# SchoolApp Web — Admin Console

Next.js 14 (App Router) + TypeScript strict + Tailwind + React Query + axios.
Companion of [`backend/`](../backend) and [`ocr-service/`](../ocr-service). npm-based.

See [`docs/FRONTEND.md`](../docs/FRONTEND.md) for architecture and the auth lifecycle
diagram. This file is the practical "how do I run / extend / smoke-test it" cheat-sheet.

## Quick start

```bash
cd web
cp .env.example .env.local      # point at your backend if not :8080
npm install
npm run dev                     # http://localhost:3000
```

Backend's `SecurityConfig` CORS allowlist includes `http://localhost:3000` out of the box.

## Scripts

| Command            | What it does                                                |
| ------------------ | ----------------------------------------------------------- |
| `npm run dev`      | Hot-reload dev server on :3000                              |
| `npm run typecheck`| `tsc --noEmit` — runs in CI before `build`                  |
| `npm run lint`     | ESLint with next/core-web-vitals                            |
| `npm run build`    | Next.js production bundle                                   |
| `npm start`        | Serve the production bundle                                 |

## Routes shipped

### Tenant-scoped (under `/tenants/[tenantId]/*`, guarded by `RequireTenantMatch`)
- `dashboard` — KPI tiles via `GET /dashboard` (gated by `ANALYTICS` feature)
- `students` — list/search/paginate + create modal · `students/[studentId]` profile
- `attendance` — summary + unmarked sections · `attendance/[sectionId]` daily grid with
  status cycling (PRESENT → ABSENT → LATE → HALF_DAY → LEAVE)
- `fees/dashboard` — 4 KPI tiles · `fees/collect` — quick-collect · `fees/defaulters`
- `settings/school` — profile · `settings/classes` — multi-draft create ·
  `settings/staff` — list + role-gated add/deactivate
- `circulars` — list with delivery stats + compose with audience picker

### Platform-scoped (under `/platform/*`, SUPER_ADMIN only)
- `tenants` — paginated list of every school on the platform
- `tenants/[schoolId]` — detail: usage, feature overrides, provider configs,
  change-plan, suspend/resume
- `plans` — read-only plan catalogue
- `features` — feature-key reference grouped by category

### Public
- `/` — redirects to `/login` or the JWT's tenant dashboard
- `/login` — two-step OTP (channel toggle via `NEXT_PUBLIC_SIGNUP_CHANNEL`)
- `/signup` — public `POST /api/v1/tenants` school onboarding

## Auth lifecycle in one paragraph

`AuthProvider` boots from `tokenStorage` (localStorage), decodes the JWT and exposes
`loading | anonymous | authenticated` state. axios attaches the bearer, and a
**single-flight refresh** triggers on first 401 (`/auth/token/refresh`) and retries the
original request once. Anything else (or refresh-failed) clears tokens and bounces to
`/login?redirect=…`. Cross-tab consistency via the `storage` event.

## Adding a new feature page

1. Add the typed endpoint wrapper in `src/api/endpoints/<area>.ts`.
2. Add (or extend) the DTOs in `src/types/domain.ts`.
3. Create `src/app/tenants/[tenantId]/<area>/page.tsx` (client component) with a
   `useQuery({ queryKey: [<area>, tenantId, …], queryFn: () => <area>Api.method(...) })`.
4. Role-gate mutating UI with `<RequireRole roles={OWNER_OR_ADMIN}>` /
   `ANY_TEACHER` / `FEE_WRITER` / `ATTENDANCE_WRITER`.
5. Add a link in `src/app/tenants/[tenantId]/layout.tsx` if it deserves nav.

## End-to-end smoke flow

1. `docker compose -f backend/docker-compose.yml up -d`
2. `cd backend && mvn spring-boot:run`
3. `cd web && npm run dev`
4. Browse [http://localhost:3000](http://localhost:3000) → `/login`
5. Click "Sign up your school" → fill form → submit
6. Back to `/login` — enter the phone you signed up with → Send OTP
7. Look at the backend console: `[OTP-DISPATCH] channel=PHONE to=98765****10 otp=NNNNNN`
8. Enter OTP → land on `/tenants/<schoolId>/dashboard`
9. The dashboard requires the `ANALYTICS` feature; FREE plan returns
   `FEATURE_DISABLED` — see the upgrade prompt
10. SUPER_ADMIN users can navigate to `/platform/tenants` to manage plans and
    feature overrides for any school

## Environment

`.env.local` (copy from `.env.example`):

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_SIGNUP_CHANNEL=PHONE   # PHONE | EMAIL | BOTH
```

The base URL is consumed by `src/api/client.ts`. The signup channel just toggles
login/signup form variants; OTP delivery is decided by the backend.

## Known gaps

Per [`docs/frontend/06-information-architecture.md`](../docs/frontend/06-information-architecture.md)
there are still ~15 pages spec'd but not built — exams/marks entry, report-card layout,
inbox/alerts, transport, library, homework, timetable views. Each is mechanically a
`src/app/tenants/[tenantId]/<area>/page.tsx` plus a React Query hook calling the
relevant backend endpoint (full reference in
[`docs/frontend/02-api-reference.md`](../docs/frontend/02-api-reference.md)).
