# Prompt 1 — Take SchoolApp to Production-Grade ERP (End-to-End)

> Paste this into a fresh Claude Code / Opus session at the repo root (`C:\Users\kunal\OneDrive\Desktop\SM`).
>
> **If you are joining this repo for the first time**, run [PROMPT_OPS_ONBOARD.md](PROMPT_OPS_ONBOARD.md) first. This prompt assumes you already know the current state.

---

You are taking ownership of **SchoolApp**, a multi-tenant SaaS school ERP. Your mission is to drive it from "feature-complete dev build" to **production-grade, end-to-end deployable**.

## Required reading (in this order, before writing any code)

1. [HANDOFF.md](HANDOFF.md) — current state, what's fixed, what's pending, ports, auth model.
2. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — backend layering, multi-tenancy, dispatcher pattern.
3. [docs/SAAS_ARCHITECTURE.md](docs/SAAS_ARCHITECTURE.md) — plans / features / subscriptions / usage counters.
4. [docs/CONFIGURATION.md](docs/CONFIGURATION.md) — env vars + per-tenant provider configs.
5. All 11 docs under [docs/frontend/](docs/frontend/) (00 through 10).
6. [backend/README.md](backend/README.md) — module catalog.
7. [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) — current config surface.
8. [backend/docker-compose.yml](backend/docker-compose.yml).
9. [ocr-service/](ocr-service/) — sibling Spring Boot service for document OCR. Has its own `pom.xml` and `Dockerfile`; treat as a second deployable in all infra gates below.

Confirm you've read these before proceeding.

## Definition of "production-grade"

The system is production-grade when **every** item below is true. Treat each as a gate.

### A. Security hardening
- [ ] Audit `application.yml` for any secret with a non-empty default in the committed YAML. Every secret (DB password, JWT secret, AES envelope key, SMTP, Stripe, Twilio/MSG91, WATI, S3, Gmail App Password) must be `${VAR}` with **no default** under the `prod` profile.
- [ ] Rotate the Gmail App Password referenced in HANDOFF.md "Security TODO" via https://myaccount.google.com/apppasswords. Confirm rotation completed before flipping the `prod` profile live. Do not propagate the literal value into any new file.
- [ ] Add a `prod` Spring profile that fails fast if any required secret is missing.
- [ ] Enable Postgres Row-Level Security (RLS) on every tenant-scoped table, OR document why the app-level guard is sufficient and add a tripwire test that proves cross-tenant reads return zero rows.
- [ ] Audit JWT: short access token (15 min) + refresh token rotation, refresh-token revocation list in Redis.
- [ ] CORS allow-list locked to the production domain (e.g. `https://app.schoolapp.in`) — no `localhost:*` wildcard in prod profile.
- [ ] CSRF strategy documented (SameSite=Lax cookies vs Bearer header — pick one).
- [ ] Rate limit `/auth/*` endpoints (Bucket4j or Redis token bucket).
- [ ] Password policy enforced: min 10 chars, complexity, breach check against HIBP k-anonymity API (optional).
- [ ] Audit log every privileged action (plan change, role change, password set, refund, deactivation).
- [ ] Run `mvn dependency:tree` + OWASP dependency-check; resolve criticals.

### B. Observability
- [ ] Structured JSON logging (Logback `JsonEncoder`) with tenant-id MDC on every request.
- [ ] OpenTelemetry traces export to a real collector (Tempo / Honeycomb / Jaeger) — currently disabled.
- [ ] Prometheus metrics endpoint (`/actuator/prometheus`) + Grafana dashboard JSON committed under `ops/grafana/`. The `ops/` folder does not exist today — create it.
- [ ] Health checks: `/actuator/health/liveness` and `/readiness` differentiate DB / Redis / SMTP outages.
- [ ] Alert rules for: 5xx rate, p99 latency, DB connection saturation, Redis failure, OTP send failure rate.

### C. Data durability
- [ ] Confirm Postgres named volume mapping (already present in `docker-compose.yml` as `postgres_data:/var/lib/postgresql/data`). Verify it survives `docker compose down` (no `-v` flag) and document the operational rule "never run `docker compose down -v` against a tenant-bearing DB."
- [ ] Documented backup strategy: `pg_dump` cron + WAL archiving for RPO ≤ 1 hour, or managed RDS/Cloud SQL.
- [ ] Restore drill script in `ops/restore-drill.sh` with documented success criteria.
- [ ] Flyway baseline locked; migrations are **forward-only**; add `flyway:validate` to CI.

### D. CI/CD
- [ ] No `.github/` folder exists today — create `.github/workflows/ci.yml` from scratch. Workflow runs: `mvn verify`, `npm run lint`, `npx tsc`, `npm run build`, container build for both backend and ocr-service.
- [ ] On main: build + push images to a registry, tag with git SHA. The repo must be a git repository first; if `git status` errors, initialize and commit a baseline before this gate.
- [ ] Staging auto-deploy; prod gated behind manual approval.
- [ ] Smoke test job hits `/actuator/health` and a tenant signup flow after deploy.

### E. Containerization & deploy
**Decision required up front:** pick the deployment target (k8s vs single-host docker compose vs managed PaaS) and record it in `docs/OPERATIONS.md` before starting this gate. The rest of E branches on that choice.

- [ ] Multi-stage `Dockerfile` for backend (distroless or `eclipse-temurin:21-jre-alpine`).
- [ ] Multi-stage `Dockerfile` for web (Next.js standalone output).
- [ ] Multi-stage `Dockerfile` for ocr-service (or confirm existing one is multi-stage).
- [ ] All containers run as non-root with read-only filesystem.
- [ ] If k8s target: Helm chart with: backend (≥2 replicas), Postgres (managed external), Redis, web (≥2 replicas), ocr-service, reverse proxy with TLS termination (Caddy / Traefik), HPA based on CPU + request-rate.
- [ ] If single-host target: `docker-compose.prod.yml` with the same components, behind Caddy/Traefik, with explicit restart policies and resource limits.

### F. End-to-end UI test pass
**Scope rule:** only modules listed as feature-complete in HANDOFF.md "Audit fixes" count toward gate F. Modules with open LOW items under "Known remaining gaps" (cafeteria wallet UI, transport student-route assignment, hostel gender filter, visitors pagination) must clear those items first (gate K) or be scoped out of the E2E suite with an explicit note.

- [ ] Drive every in-scope module in the browser using Playwright or Cypress: Login → Signup → Students → Attendance → Fees → Admissions → HR → Library → Hostel → Inventory → Communication → Reports → Settings.
- [ ] Each happy path scripted under `web/e2e/` and runs in CI against a seeded tenant.
- [ ] Visual regression for the dashboard.
- [ ] Test the parent inbox + WhatsApp + Email delivery against sandboxes.

### G. Performance budgets
- [ ] Backend p99 < 300ms on 95% of endpoints under 100 RPS sustained load (k6 script in `ops/load/` — `ops/` folder needs creating).
- [ ] DB query budget: every endpoint < 5 queries; add `n+1` regression test via Datasource-Proxy.
- [ ] Web Lighthouse score ≥ 90 perf, ≥ 95 a11y, ≥ 100 best-practices on `/login`, `/dashboard`, `/students`.
- [ ] Bundle size budget: initial JS < 250KB gzipped per route.

### H. Multi-tenant correctness
- [ ] Cross-tenant integration test that proves Tenant A cannot read/write Tenant B's data for **every** controller (loop over `@RestController` beans via reflection).
- [ ] Idempotency-Key middleware tested under concurrent retries.
- [ ] Per-tenant quota enforcement (students, staff, SMS sends) with clear 402/429 errors.

### I. Compliance & legal
- [ ] GDPR / DPDP data-export endpoint per parent: `GET /api/v1/parents/me/export` returns ZIP of all child data.
- [ ] Right-to-erasure flow with soft-delete + scheduled hard-delete after retention window.
- [ ] Privacy policy + Terms of Service pages.
- [ ] Cookie consent banner (only if analytics added).
- [ ] Audit log retention for fee receipts ≥ 7 years. Statutory floor is 6 years (Income Tax Act §44AA: 6 years from end of relevant AY; GST §35-36: 72 months from due date of annual return); 7 is a safety margin. Cite this rule in `docs/OPERATIONS.md` so it isn't "optimized" down later.

### J. Documentation
- [ ] `RUNBOOK.md` — on-call procedures (DB failover, restoring from backup, rotating secrets, draining a pod, replaying outbox).
- [ ] `INCIDENT_RESPONSE.md` — severity ladder, comms tree, postmortem template.
- [ ] OpenAPI spec auto-generated and published at `/api/v1/openapi.json` (springdoc).
- [ ] `THREAT_MODEL.md` — STRIDE pass per trust boundary.

### K. Known LOW-priority cleanups from HANDOFF.md
Fix all items listed under the **"Known remaining gaps"** heading in HANDOFF.md (currently: visitors pagination, cafeteria wallet UI, transport student-route assignment, `id.slice(0,8)…` cosmetic cleanups, hostel allocation gender filter). Cross-reference HANDOFF.md by heading text, not section number, since numbering will drift as Session N blocks are appended.

## Working style

1. **Plan before code.** Before any change, post a 5-line plan and the order of slices.
2. **Slice work into PR-sized commits** (≤ 400 lines diff). Each commit: green tests, green tsc, green lint.
3. **Never delete a Flyway migration**; only forward-migrate.
4. **Tests first** for security-critical changes (RLS, auth, idempotency).
5. **Update HANDOFF.md** at the end of each slice — append a "Session N (YYYY-MM-DD)" block using the template in [PROMPT_OPS_ONBOARD.md](PROMPT_OPS_ONBOARD.md) Phase 4.7.
6. After each major slice, run the full verification quartet: `mvn verify` · `cd web; npx tsc --noEmit; npm run lint; npm run build`.
7. Ask before touching: production data, billing webhooks, RLS rollout, secret rotation procedure.

## What to deliver at the end

- Green CI pipeline.
- Deployable Docker images for backend, web, and ocr-service.
- Smoke E2E suite passing.
- Updated HANDOFF.md with "Production-Ready: YES" gate checklist all ticked.
- A single `RELEASE_NOTES.md` summarizing what changed from dev-build → production.

Begin by reading HANDOFF.md and then post your first plan.
