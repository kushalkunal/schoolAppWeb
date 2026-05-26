# 00 — Stack and Conventions

The backend is opinionated about a small number of things that affect the web app's shape. This file locks those down so every LLM prompt and every generated component lands in the same grammar.

---

## Recommended tech stack

Treat this as **the** stack. An LLM generating code should use exactly these choices unless the user says otherwise.

| Layer | Choice | Why |
|---|---|---|
| Bundler / dev server | **Vite** | Fast, zero-config for a SPA. Dev origin `http://localhost:5173` is in the backend's CORS allowlist. |
| Framework | **React 18** (function components + hooks) | |
| Language | **TypeScript 5** (strict mode) | Matches the backend's typed DTOs 1:1 |
| Routing | **react-router-dom v6** | Nested routes + loaders fit the `/tenants/{tenantId}/...` URL shape |
| Data fetching | **@tanstack/react-query v5** | Cache, invalidation, optimistic updates. Patterns in `09-data-fetching-patterns.md` |
| HTTP client | **axios** (or fetch) | Must have one global interceptor for: bearer header, 401→refresh, envelope unwrapping |
| Forms | **react-hook-form** + **zod** | zod resolver pairs well with the typed DTOs |
| UI primitives | **shadcn/ui** on **Tailwind CSS** | Accessible defaults; no opinion from the backend. |
| Icons | **lucide-react** | |
| Tables | **@tanstack/react-table v8** | Pairs with react-query for server-side pagination |
| Date handling | **date-fns** | Avoid moment (legacy). Server emits ISO-8601 strings — parse at the boundary. |
| Charts | **recharts** | For the analytics dashboard |
| PDFs | **browser-native** — receipts + report cards are PDFs the backend returns URLs to. Don't parse them in-browser. |

**Package manager**: pnpm (fast, workspace-friendly). npm works too.

---

## Project layout

```
src/
├── api/                      # axios instance, endpoint wrappers, error mapper
│   ├── client.ts             # axios config + interceptors
│   ├── endpoints/            # one file per module (students.ts, fees.ts, …)
│   └── types.ts              # re-export from 08-typescript-dto-reference.md
├── auth/
│   ├── AuthProvider.tsx      # JWT + TenantContext provider
│   ├── useAuth.ts
│   └── RequireRole.tsx       # role-gate wrapper
├── components/
│   ├── ui/                   # shadcn primitives
│   └── shared/               # cross-feature (EmptyState, ErrorBanner, …)
├── features/                 # one folder per domain
│   ├── dashboard/
│   ├── students/
│   ├── attendance/
│   ├── fees/
│   ├── academics/
│   ├── communication/        # inbox + circulars
│   ├── analytics/            # alerts + at-risk
│   ├── staff/                # incl. substitutes
│   └── settings/             # school profile, logo, fee heads, reminder schedules
├── hooks/                    # generic hooks (useDebounce, useMediaQuery, …)
├── lib/                      # zod schemas, date utils, currency format
├── pages/                    # top-level route components (thin — delegate to features)
├── router.tsx                # route tree with role guards
└── main.tsx                  # providers + queryClient + router
```

One folder per domain keeps reviewers reading a single area. No shared "services" folder — each feature owns its own query hooks.

---

## URL conventions on the frontend

Backend routes use `/api/v1/tenants/{tenantId}/...`. The frontend URL mirrors this for mental mapping, but drops the `/api/v1`:

```
/login
/tenants/:tenantId/dashboard
/tenants/:tenantId/students
/tenants/:tenantId/students/:studentId
/tenants/:tenantId/attendance
/tenants/:tenantId/fees
/tenants/:tenantId/fees/dashboard
/tenants/:tenantId/academics/exams
/tenants/:tenantId/academics/exams/:examId/marks/:sectionId
/tenants/:tenantId/alerts
/tenants/:tenantId/inbox
/tenants/:tenantId/circulars
/tenants/:tenantId/settings/school
/tenants/:tenantId/settings/staff
/tenants/:tenantId/settings/fee-heads
/tenants/:tenantId/settings/reminder-schedules
```

The `:tenantId` in the URL must match the `tenantId` claim in the JWT — the backend enforces this. If they diverge (token switch, URL tampering), redirect to `/login`.

---

## Currency, dates, and phone numbers

- **Currency**: all amounts on the wire are in **paise** (long). Divide by 100 for rupees. Format with `new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`.
- **Dates**: ISO-8601. `LocalDate` → `"YYYY-MM-DD"`. `OffsetDateTime` → `"YYYY-MM-DDTHH:mm:ssZ"` (UTC). Display in local timezone; send in ISO.
- **Phone numbers**: always 10-digit Indian mobiles (`/^[6-9]\d{9}$/`). The server normalises — strip the `+91` or leading `0` before sending.
- **UUIDs**: server-generated. Never let the frontend mint them.

---

## Coding conventions

**Components**
- Function components + hooks. No class components.
- One component per file. Co-locate `.test.tsx` next to the component.
- Use named exports. Default exports only for `page`-level components that the router imports.

**Types**
- `strict: true` and `noUncheckedIndexedAccess: true` in `tsconfig.json`.
- Derive TypeScript types from DTOs in `08-typescript-dto-reference.md`. Don't re-declare them.
- Use `z.infer<typeof schema>` for form values.

**Styling**
- Tailwind utility classes on JSX. No separate CSS files except for global resets and Tailwind directives.
- `clsx` for conditional classes.

**State**
- **Server state** → react-query. Never store server data in `useState`.
- **URL state** → `useSearchParams`. Filters, pagination, date ranges all go here so browser back works.
- **Local UI state** → `useState` / `useReducer`.
- **Global app state** → a thin `AuthProvider` context is enough. Avoid Redux / Zustand unless something screams for it.

**Error handling**
- Every `useQuery` / `useMutation` consumes the envelope shape. The shared `apiClient` unwraps `{ success, data, error }` and either returns `data` or throws an `ApiError`.
- `<ErrorBoundary>` at the page level only. Inline `ErrorBanner` for mutation failures.
- Never swallow a 401 — let the interceptor handle refresh / redirect.

**Naming**
- Query hooks: `useXQuery` (reads), `useXMutation` (writes). Example: `useStudentsQuery`, `useCreateStudentMutation`.
- Query keys: `['students', tenantId, filters]` — tenantId is always the 2nd element.
- Endpoint wrappers: `studentsApi.list(tenantId, params)` — one namespace per module.

---

## API base URL

```env
VITE_API_BASE_URL=http://localhost:8080
```

Always use the environment variable. No hardcoded URLs.

---

## Files an LLM should *never* invent

- New endpoints — if it's not in `02-api-reference.md`, the backend doesn't have it.
- New error codes — use only the ones in `03-error-handling.md`.
- New roles — use only the ones in `05-role-matrix.md`.
- Custom envelope shapes — the server always returns `{ success, data, error, meta }` (fields null-omitted).
