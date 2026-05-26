# SchoolApp — Handoff Context for Next AI

**Last updated:** 2026-05-26
**State:** Backend feature-complete. Web frontend BLOCKER/HIGH/MEDIUM audit bugs fixed. tsc green. Not yet driven end-to-end in browser.

---

## 1. What this app is

Multi-tenant SaaS school management system for Indian K-12 schools.
- **Backend:** Spring Boot 3.3.5, Java 21, Postgres 16, Flyway V23, JWT auth, BCrypt passwords + OTP.
- **Web:** Next.js 14 (app router), TypeScript strict, TanStack Query, Tailwind v2 with branded design tokens.
- **Infra (dev):** Docker Compose for Postgres + Redis. SMTP via Gmail in `application.yml`.

---

## 2. Local run

```bash
# infra
cd backend && docker compose up -d            # postgres:55432  redis:56379

# backend on :61400
cd backend
DATABASE_URL=jdbc:postgresql://localhost:55432/schoolapp \
mvn spring-boot:run

# web on :61402
cd web
npm run dev -- -p 61402
```

Env files: `web/.env.local` already has `NEXT_PUBLIC_API_BASE_URL=http://localhost:61400` and `NEXT_PUBLIC_SIGNUP_CHANNEL=BOTH`.

Postgres uses a named volume (`postgres_data`) — data survives `docker compose down`. It does **not** survive `docker compose down -v`; never run that against a tenant-bearing DB.

---

## 3. Authentication model

Implemented in slice 35 (Flyway V23):
- Signup wizard at `/signup` — 3 steps: Account → Verify OTP (phone or email) → optional Set Password.
- Login at `/login` defaults to **password**, with "Forgot password? → OTP" fallback. Channel toggle Phone/Email shown when `SIGNUP_CHANNEL=BOTH` (default).
- Endpoints:
  - `POST /api/v1/auth/password/login` (public) — phone+password or email+password
  - `POST /api/v1/auth/password/set` (authenticated) — requires verified identifier, ≥8 chars
  - Existing `/auth/otp/request` and `/auth/otp/verify`
- Lockout: 5 failed password logins → 15 min lock (`failed_login_count`, `locked_until` on `staff`).
- Error codes: `INVALID_CREDENTIALS`, `PASSWORD_NOT_SET`, `ACCOUNT_LOCKED`, `IDENTIFIER_NOT_VERIFIED`.

---

## 4. Audit fixes applied this session

Pickers replace raw UUID inputs and 500-row `<select>`s across:
- `incidents/page.tsx` — `StudentPicker`
- `infirmary/page.tsx` — `StudentPicker`
- `ptm/page.tsx` — `StaffPicker` (teachers) + `StudentPicker`
- `circulars/page.tsx` — checkbox lists for class/section/student targets
- `library/issues/page.tsx` — `StudentPicker` + "Unknown student" fallback
- `hostel/page.tsx` (AllocateModal) — `StudentPicker`
- `inventory/page.tsx` (IssueItemModal) — `StaffPicker` + `StudentPicker`
- `transport/page.tsx` (CreateVehicleModal) — `StaffPicker` for driver

Reusable components live at `web/src/components/pickers/`:
- `EntityPicker.tsx` — generic search-as-you-type combobox primitive
- `StudentPicker.tsx`, `StaffPicker.tsx` — wrappers

Other fixes:
- **Attendance** — `GET /tenants/{id}/students/by-section/{sectionId}` added; section page now seeds entire roster as PRESENT on first mark. Empty state when roster also empty.
- **Admissions kanban** — `REQUIRES_FORM` map blocks silent transitions for APPLICATION_SUBMITTED / TEST_SCHEDULED / OFFERED; kanban button becomes a Link to detail page. Source dropdown (WEBSITE/WALK_IN/REFERRAL/AD/OTHER).
- **Expenses** — payment-mode select (CASH/UPI/CHEQUE/BANK/CARD).
- **Settings/staff** — `prettyRole()` helper for human-readable role names.
- **Inbox** — channel filter chips (ALL / WHATSAPP / EMAIL / SMS) + search box.
- **Student detail** — quick-link row to fees / attendance / incidents / homework / risk.
- **Homework** — due-soon now uses local-date parse (IST timezone safe).
- **Backend infra fixes** — CORS wildcard `http://localhost:*`, NoResourceFoundException→404, LocalFileStorageService normalized path, MidYearAutoInvoiceListener REQUIRES_NEW, Stripe webhook secret soft-fail, `/inbox` collision moved to `/parent-messages`.

---

## 5. Verification status

- ✅ `mvn compile` — green
- ✅ `npx tsc --noEmit` (in `web/`) — green
- ❌ **Not driven in browser end-to-end** — Chrome extension pairing wasn't getting through. Code-level fixes only.

---

## 6. Known remaining gaps (LOW priority)

- Visitors history page is first-page only, no pagination next/prev.
- Cafeteria wallet/top-up UI absent — backend hooks exist (refund-on-cancel), no UI.
- Transport: no student-route assignment modal (backend may not support yet).
- A few places still show `id.slice(0,8)…` (cosmetic).
- Hostel allocation doesn't filter by room gender vs student gender.

---

## 7. Critical files for next AI

**Backend:**
- `backend/src/main/resources/db/migration/V23__slice35_password_login.sql`
- `backend/src/main/java/in/schoolapp/auth/AuthService.java`
- `backend/src/main/java/in/schoolapp/auth/AuthController.java`
- `backend/src/main/java/in/schoolapp/config/SecurityConfig.java`
- `backend/src/main/resources/application.yml` (SMTP creds, signup channel, ports)
- `backend/docker-compose.yml` (Postgres 55432, Redis 56379)

**Web:**
- `web/src/auth/AuthProvider.tsx` — `loginWithPassword`, `setPassword`
- `web/src/app/login/page.tsx`, `web/src/app/signup/page.tsx`
- `web/src/components/pickers/` — reusable comboboxes
- `web/src/api/endpoints/` — all backend API wrappers
- `web/.env.local` — base URL + signup channel

---

## 8. Security TODO

⚠️ A Gmail App Password for `thekkdotin@gmail.com` was shared in chat on 2026-05-26 (literal value redacted from this file to avoid further propagation; retrieve from the original chat transcript if still needed for rotation). The literal also previously appeared in `application.yml` — verify it has been removed and that SMTP credentials come from env vars only.

**Rotation procedure:**
1. Open https://myaccount.google.com/apppasswords while signed in as `thekkdotin@gmail.com`.
2. Revoke the leaked password.
3. Generate a new one, set it via the `MAIL_PASSWORD` env var (not committed), and restart the backend.
4. Delete the original chat message containing the literal value.
5. Once rotated, this section can be removed.

---

## 9. Architecture pointers (still accurate)

For deeper architecture/module-level docs see:
- `docs/ARCHITECTURE.md` — backend layering, multi-tenancy, dispatcher pattern
- `docs/SAAS_ARCHITECTURE.md` — plans/features/subscriptions/usage
- `docs/CONFIGURATION.md` — env vars + per-tenant provider configs
- `docs/SETUP.md` — first-time dev environment
- `docs/frontend/` — 11 sub-docs covering web stack, auth, API ref, role matrix
- `backend/README.md` — module list

The `Analysis/` folder contained stale planning docs from much earlier phases and has been deleted. Do not recreate it — use this file + the `docs/` tree instead.

---

## 10. Session log

Append new sessions here using this template (most recent on top):

```markdown
### Session N (YYYY-MM-DD) — short title

**Goal:** one sentence.

**Changed:**
- file:line — what + why

**Verification:** mvn ✅/❌ · tsc ✅/❌ · lint ✅/❌ · browser ✅/❌/skipped

**Still red:** anything new to add to "Known remaining gaps"
```
