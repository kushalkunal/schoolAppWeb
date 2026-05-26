# Frontend Documentation — Web Admin App

This folder is the **single source of truth** an LLM (or a human developer) needs to build a React web admin app that fully exercises the backend. Every file here is derived from the shipped backend — no aspirational or pending items. When the backend changes, these files should be regenerated.

**Scope**: React web admin for school staff (principals, admins, teachers, accountants). The mobile teacher app has its own contract in [../modules/sync.md](../modules/sync.md). Parents do not use the web app — they interact via outbound WhatsApp only.

---

## How to use these docs

**For an LLM pipeline**, feed files in this order. Each builds on the previous:

1. [00-stack-and-conventions.md](00-stack-and-conventions.md) — the tech stack + coding conventions the LLM must follow
2. [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md) — signup / OTP / JWT / refresh-token rotation — get this right or nothing else works
3. [02-api-reference.md](02-api-reference.md) — every endpoint with request/response shapes
4. [03-error-handling.md](03-error-handling.md) — error envelope, HTTP status map, validation-error shape
5. [04-multi-tenant-model.md](04-multi-tenant-model.md) — the `tenantId` URL segment and why it's there
6. [05-role-matrix.md](05-role-matrix.md) — role × capability × endpoint (drives UI gating)
7. [06-information-architecture.md](06-information-architecture.md) — pages, routes, navigation
8. [07-feature-flows.md](07-feature-flows.md) — critical user journeys end-to-end
9. [08-typescript-dto-reference.md](08-typescript-dto-reference.md) — TS types matching every DTO
10. [09-data-fetching-patterns.md](09-data-fetching-patterns.md) — React Query conventions (keys, invalidation, error retry)
11. [10-llm-prompting-guide.md](10-llm-prompting-guide.md) — prompt templates that produce code consistent with this stack

**For a human developer**, pick the feature you're building and read `07-feature-flows.md` for the user journey, then jump to the relevant section of `02-api-reference.md`. The TypeScript types in `08` can be copy-pasted as a starting point.

---

## What this documentation guarantees

- **No hallucinations.** Every endpoint, DTO, role, and status code in these files exists in the backend code as of the last sync (2026-04-23). Source links point at specific Java files.
- **Every endpoint is covered.** If the backend exposes it, it's in `02-api-reference.md`.
- **Role gating is explicit.** Every mutating endpoint declares its required role(s) — the UI must hide + the server will 403 anything else.
- **Copy-pasteable TypeScript.** `08-typescript-dto-reference.md` gives you types that match the JSON on the wire.

---

## Out of scope for these docs

- Mobile client (React Native) — see [../modules/sync.md](../modules/sync.md).
- Parent-facing experience — WhatsApp-only, no UI.
- Backend development — see [../modules/](../modules/) and [../../IMPLEMENTATION_STATUS.md](../../IMPLEMENTATION_STATUS.md).
- Visual design / UX wireframes — these docs describe **what** to build, not **how it looks**. [../../PHASE1_FRONTEND_PLAN.md](../../PHASE1_FRONTEND_PLAN.md) has the product spec.

---

## Backend at a glance (for context)

- Java 21 + Spring Boot 3.3.5, PostgreSQL 15+, Redis.
- JWT-based auth: 15-min access token + 7-day refresh token with rotation.
- Multi-tenant: every tenant-scoped URL is `/api/v1/tenants/{tenantId}/...`. The JWT carries the staff's tenant; the `TenantInterceptor` rejects mismatches.
- All write endpoints are guarded by `@PreAuthorize` — the web app must mirror this gating in the UI to avoid users hitting 403s.
- OpenAPI spec is live at **`GET /v3/api-docs`** and Swagger UI at **`GET /swagger-ui/index.html`** — both are unauthenticated so you can explore before wiring auth.
- Top-level overview: [../ARCHITECTURE.md](../ARCHITECTURE.md).

---

## When to regenerate

Regenerate this folder when any of these change in the backend:
- A new controller or endpoint is added / removed
- A DTO field is added, removed, or renamed
- A new `@PreAuthorize` rule changes the required role
- A new `ErrorCode` is added
- The JWT claim shape changes
- The envelope shape (`ApiResponse`) changes

`grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" backend/src/main/java` is a quick sanity-check against `02-api-reference.md`.
