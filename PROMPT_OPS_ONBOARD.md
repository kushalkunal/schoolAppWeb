# Prompt 2 — Ops / Next-AI Onboarding: Understand Current State & Pick Up Work

> Paste this into a fresh Claude Code / Opus session at the repo root (`C:\Users\kunal\OneDrive\Desktop\SM`).
> Use this when the goal is to **understand what exists and continue** — not to start production hardening from scratch.
>
> For the long-horizon target see [PROMPT_PRODUCTION_GRADE.md](PROMPT_PRODUCTION_GRADE.md).

---

You are joining an in-flight project called **SchoolApp** (multi-tenant SaaS school ERP). The previous developer left structured handoff notes. Your first job is to **understand the current state thoroughly** before making any changes.

## Phase 0 — Repo baseline check (do this first)

Run `git status` in the repo root.

- If the repo **is** a git repo, note the current branch and any dirty state, then continue to Phase 1.
- If it is **not** a git repo, stop and ask the user before running `git init`. The working rules below assume version control exists; without it, "commit-sized slices" is meaningless and rollback is manual. Resolve this before touching code.

## Phase 1 — Orient yourself (read-only, no edits)

Read these files **in order**. Do not skip.

1. [HANDOFF.md](HANDOFF.md) — single source of truth for current state. Memorize the "Audit fixes applied" and "Known remaining gaps" sections.
2. [PROMPT_PRODUCTION_GRADE.md](PROMPT_PRODUCTION_GRADE.md) — the production checklist that defines what "done" looks like long-term.
3. [README.md](README.md) (root) — high-level intro.
4. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — backend layering, dispatcher pattern, multi-tenancy enforcement.
5. [docs/SAAS_ARCHITECTURE.md](docs/SAAS_ARCHITECTURE.md) — plans, features, subscriptions, usage counters.
6. [docs/CONFIGURATION.md](docs/CONFIGURATION.md) — env vars + per-tenant provider configs (WhatsApp/SMS/Email/Storage).
7. [docs/SETUP.md](docs/SETUP.md) — first-time dev environment.
8. [docs/frontend/00-stack-and-conventions.md](docs/frontend/00-stack-and-conventions.md) — web conventions.
9. [docs/frontend/02-api-reference.md](docs/frontend/02-api-reference.md) — endpoint contracts the web consumes.
10. [docs/frontend/05-role-matrix.md](docs/frontend/05-role-matrix.md) — RBAC.
11. [backend/README.md](backend/README.md) — module catalog (one paragraph per module).
12. [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) — full config surface (note `${VAR:default}` patterns).
13. [backend/docker-compose.yml](backend/docker-compose.yml) — current ports (Postgres 55432, Redis 56379) and named volumes (`postgres_data`, `redis_data`).
14. [web/.env.local](web/.env.local) — frontend base URL + signup channel.
15. [ocr-service/](ocr-service/) — quick scan only. Sibling Spring Boot service for document OCR. Has its own `pom.xml` + `Dockerfile`; read its `README.md` if present.

The other 8 docs under `docs/frontend/` (01, 03, 04, 06, 07, 08, 09, 10) are skipped here to keep onboarding light. Skim them only if your assigned slice touches their area (auth, error handling, multi-tenant, IA, flows, DTOs, data fetching, LLM prompting).

After reading, **post a 10-bullet summary** of your understanding covering:
- Stack (backend + web + infra + ocr-service)
- Multi-tenancy model (how tenant ID flows)
- Auth (password vs OTP, signup wizard, lockout)
- Module list and which are feature-complete
- The dispatcher pattern (WhatsApp/SMS/Email/Storage — tenant override → env fallback)
- Plans/features/subscriptions and how `@RequiresFeature` works
- Current verification status — quote the green/red marks from HANDOFF.md "Verification status" section verbatim, do not re-run anything in this phase
- Known gaps from HANDOFF.md "Known remaining gaps" section
- The leaked-credential security TODO (see HANDOFF.md "Security TODO" section — do **not** re-quote the literal value in your summary)
- Ports to use locally (61400 backend / 61402 web / 55432 Postgres / 56379 Redis)

**Do not skip this step.** Do not write any code until you've posted the summary.

## Phase 2 — Verify the build still works (no edits)

This block uses PowerShell syntax (the project default on Windows). On bash, swap `;` for `&&` and adjust path separators.

```powershell
# 1. Infra
Set-Location backend
docker compose ps
# If empty: docker compose up -d
Set-Location ..

# 2. Backend
Set-Location backend
mvn -q compile
mvn -q test
# Report green/red and the actual test count. Cross-check against HANDOFF.md
# "Verification status" — flag any drift from the recorded baseline.
Set-Location ..

# 3. Web
Set-Location web
npx tsc --noEmit
npm run lint
npm run build
Set-Location ..
```

If anything fails, **stop and report** — don't try to fix without understanding why.

## Phase 3 — Pick a slice

Based on what you learned, ask the user **which slice they want next**. Examples:

- **A — Browser verification:** drive the UI end-to-end via Playwright (preferred, scripted) or a manual run, prove the BLOCKER/HIGH/MEDIUM fixes work, file any regressions.
- **B — LOW gap cleanup:** work through HANDOFF.md "Known remaining gaps". Acceptance criteria for each:
  - **Visitors pagination** — `?page=N&size=25` query params, prev/next buttons disabled at boundaries, total-count badge.
  - **Cafeteria wallet UI** — top-up modal + balance widget on parent dashboard; consumes the existing refund-on-cancel hooks; uses `EntityPicker` for child selection.
  - **Transport student-route assignment** — confirm backend endpoint exists; if not, scope = add endpoint + modal in one slice. Modal uses `StudentPicker` + route dropdown.
  - **`id.slice(0,8)…` cleanups** — replace with the entity's display name from cache; fall back to "Unknown" if not loaded.
  - **Hostel allocation gender filter** — filter rooms by `room.gender === student.gender` in the allocation modal; show a banner if the student's gender field is empty.
- **C — Production hardening:** start [PROMPT_PRODUCTION_GRADE.md](PROMPT_PRODUCTION_GRADE.md) checklist from gate A (Security).
- **D — A specific feature** the user names.

Wait for the user to pick. Do not silently start.

## Phase 4 — Working rules

When you do start coding:

1. **Plan before code.** Post a 5-line plan and the order of changes before opening any file in edit mode.
2. **Small slices.** Each commit ≤ 400 lines diff. Each commit: green `mvn compile` + green `npx tsc --noEmit`.
3. **Never delete a Flyway migration**; only add forward migrations. Check `backend/src/main/resources/db/migration/` for the highest existing `V<N>__*.sql` and use `V<N+1>__*.sql`.
4. **Never commit secrets.** The Gmail App Password named in HANDOFF.md "Security TODO" is exposed and must be rotated — flag this every time you touch `application.yml`. Do not re-quote the literal value in any new file.
5. **Follow existing patterns.** Look at how similar features are implemented before inventing new ones — there are reusable pickers, dispatchers, error codes, role gates already in place.
6. **Use the right tools and know where they live:**
   - `EntityPicker`, `StudentPicker`, `StaffPicker` → `web/src/components/pickers/`
   - `RequireRole` for RBAC gates, `isFeatureEnabled` for feature flags, `useToast` for notifications, `ErrorBanner` for query errors — grep `web/src/` for definitions before re-inventing.
7. **Update HANDOFF.md** at the end of every session. Append a block using this template:
   ```markdown
   ## Session N (YYYY-MM-DD) — short title

   **Goal:** one sentence.

   **Changed:**
   - file:line — what + why

   **Verification:** mvn ✅/❌ · tsc ✅/❌ · lint ✅/❌ · browser ✅/❌/skipped

   **Still red:** anything new in "Known remaining gaps"
   ```
8. **Ask before touching:** secret rotation, RLS rollout, billing webhooks, plan changes, production data.
9. **Do not recreate `Analysis/`.** That folder held stale planning docs and was deleted intentionally. Use HANDOFF.md, the docs/ tree, and this prompt instead.

## Phase 5 — Definition of done per slice

A slice is done when:
- [ ] Code compiles (backend) and type-checks (web).
- [ ] Tests pass (`mvn test`).
- [ ] Lint passes (`npm run lint`).
- [ ] Manual smoke of the affected flow in the browser (or note "not browser-verified" in the commit message).
- [ ] HANDOFF.md updated with a new Session block (template in Phase 4.7).
- [ ] Commit message follows existing style (imperative mood, focused scope).

## What success looks like

By the time you hand off to the next AI, the repo should be **closer to the gate checklist in [PROMPT_PRODUCTION_GRADE.md](PROMPT_PRODUCTION_GRADE.md)** than when you started, and HANDOFF.md should accurately reflect that progress.

Begin Phase 0 now. Do not skip ahead.
