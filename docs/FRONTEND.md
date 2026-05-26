# Frontend — web admin

Next.js 14 (App Router) + TypeScript + React Query + Tailwind. Lives in [`web/`](../web)
as a sibling of `backend/` and `ocr-service/`. **npm-based build** per project convention.

## What's in the scaffold (slice 10)

```
web/
├── package.json                 next 14.2, react 18, @tanstack/react-query 5, axios, zod
├── tsconfig.json                strict + noUncheckedIndexedAccess
├── tailwind.config.ts           palette matches mobile NativeWind palette
├── next.config.js
├── postcss.config.js
├── .env.example                 NEXT_PUBLIC_API_BASE_URL, NEXT_PUBLIC_SIGNUP_CHANNEL
└── src/
    ├── api/
    │   ├── client.ts            axios + bearer + single-flight refresh + envelope unwrap
    │   └── errors.ts            ApiError class + ErrorCodeName union (every backend code)
    ├── auth/
    │   ├── tokenStorage.ts      localStorage wrapper (SSR-safe)
    │   ├── jwt.ts               decodeJwt (no signature verify; server already did)
    │   ├── AuthProvider.tsx     loading→anonymous→authenticated state, sendOtp/login/logout
    │   └── RequireTenantMatch.tsx  guards /tenants/[tenantId]/* — URL ↔ JWT claim check
    ├── lib/
    │   ├── queryClient.ts       transient-only retry, 30s stale default
    │   └── providers.tsx        QueryClientProvider + AuthProvider
    └── app/
        ├── globals.css          Tailwind directives
        ├── layout.tsx           root with <Providers>
        ├── page.tsx             "/" — redirects per auth state
        ├── login/page.tsx       two-step OTP login (identifier → OTP)
        ├── signup/page.tsx      public POST /api/v1/tenants
        └── tenants/[tenantId]/
            ├── layout.tsx       AppShell w/ sidebar + topbar + logout
            └── dashboard/page.tsx  React Query → /dashboard with skeleton + error fallback
```

## Running it

### Prerequisites
- Node 20+
- The backend running at `http://localhost:8080` (see [`backend/README.md`](../backend/README.md))
- Optional: ocr-service at `http://localhost:8090`

### First-time install
```bash
cd web
cp .env.example .env.local       # edit if your backend isn't on :8080
npm install
```

### Dev server (hot reload, port 3000)
```bash
npm run dev
```
Open [http://localhost:3000](http://localhost:3000). The root redirects to `/login`.

### Type-check (separate from build)
```bash
npm run typecheck
```

### Production build
```bash
npm run build
npm start
```

The CORS allowlist on the backend (see [`SecurityConfig.java`](../backend/src/main/java/in/schoolapp/config/SecurityConfig.java))
already includes `http://localhost:3000` so dev works out of the box.

## End-to-end smoke flow

1. `cd backend && docker compose up -d && mvn spring-boot:run` — backend on `:8080`
2. `cd web && npm install && npm run dev` — UI on `:3000`
3. Browse to [http://localhost:3000](http://localhost:3000) → redirects to `/login`
4. Click "Sign up your school" → fill the form → submit
   - Backend creates School + Principal Staff + AcademicYear + TRIAL Subscription
   - Browser pushed back to `/login` with the identifier remembered in sessionStorage
5. Enter the phone you signed up with → "Send OTP"
   - Backend logs `[OTP-DISPATCH] channel=PHONE to=98765****10 otp=123456`
   - In dev, the OTP appears in the backend's console
6. Enter the 6-digit OTP → "Verify & sign in"
   - Backend returns `{accessToken, refreshToken, expiresInSeconds, user}`
   - Tokens saved to `localStorage`, JWT decoded, navigation to `/tenants/<tenantId>/dashboard`
7. Dashboard loads via `GET /api/v1/tenants/{tenantId}/dashboard`. If the school's plan
   doesn't include the `ANALYTICS` feature (FREE plan doesn't), you get the
   `FEATURE_DISABLED` upgrade prompt instead of data — slice 1's @RequiresFeature aspect is
   exercising end-to-end.

## Auth lifecycle — quick mental model

```
                          ┌─────────────────────┐
                          │  tokenStorage       │  localStorage backed
                          │  read/write/clear   │
                          └──────────┬──────────┘
                                     │
   ┌────────────┐       set     ┌────▼──────┐      decodes
   │ /login     │ ─────────────▶│ AuthCtx   │ ◀──────── decodeJwt
   │ /signup    │               │ state     │
   └────────────┘               └────┬──────┘
                                     │ used by
            ┌────────────────────────┤
            ▼                        ▼
   ┌────────────────┐    ┌──────────────────────┐
   │ apiClient      │    │ RequireTenantMatch   │
   │ (axios)        │    │ wraps /tenants/[id]  │
   │  - attach Bearer    └──────────────────────┘
   │  - 401-on-expired:
   │      single-flight POST /auth/token/refresh
   │      retry once
   │      else → /login?redirect=…
   │  - unwrap { success, data } envelope
   └────────────────┘
```

## What this scaffold deliberately doesn't have yet

Per [`docs/frontend/06-information-architecture.md`](frontend/06-information-architecture.md)
the full app has ~40 pages. Slice 10 lands the **foundation pages only**:

- ✅ `/` redirect, `/login`, `/signup`, `/tenants/[tenantId]/dashboard`
- ❌ Students list/detail/create, attendance grid, fee dashboard/collect, marks entry,
     report cards, circulars, inbox, alerts, platform admin pages — all spec'd in
     [`docs/frontend/`](frontend/) and ready to be built slice by slice

Picking up where this scaffold leaves off is mechanical: each new feature is a
`src/app/tenants/[tenantId]/<feature>/page.tsx` plus a React Query hook calling the relevant
backend endpoint (full reference in
[`docs/frontend/02-api-reference.md`](frontend/02-api-reference.md)).
