# 10 — LLM Prompting Guide

How to feed these docs to a code-generating LLM so the output actually compiles, respects auth/roles, and matches the server wire-format.

---

## The "minimum viable" system prompt

Paste this into the LLM's system / instructions slot before any feature request:

```
You are generating code for a React + TypeScript web admin app. The authoritative
specs are the files under docs/frontend/. Follow them exactly.

Stack: Vite + React 18 + TypeScript (strict) + react-router-dom v6 +
@tanstack/react-query v5 + axios + react-hook-form + zod + shadcn/ui + Tailwind.

Hard rules:
1. Never invent an endpoint. If docs/frontend/02-api-reference.md doesn't list
   it, it doesn't exist on the server.
2. Never invent a DTO field. Use types from docs/frontend/08-typescript-dto-reference.md.
3. Every mutating call goes through a useMutation. Reads go through useQuery.
   Query keys follow the convention [resource, tenantId, ...args].
4. Every envelope-returning endpoint unwraps via the shared apiClient — handlers
   receive `data` directly, not `{ success, data, error }`.
5. Role gating: before rendering a button that calls a mutating endpoint, check
   the caller's role against docs/frontend/05-role-matrix.md. If the role isn't
   allowed, the button must be hidden — not disabled.
6. Errors are ApiError instances with .code (from docs/frontend/03-error-handling.md)
   and .message. Render .message in a toast; branch on .code for special cases
   (OTP_INVALID, VALIDATION_ERROR).
7. Currency is in paise (long). Divide by 100, format with Intl.NumberFormat IN-INR.
8. Dates on the wire are ISO strings. Parse with date-fns at the boundary; never
   send Date objects.
9. Multi-tenancy: every tenant-scoped path starts with /tenants/:tenantId/... on
   both frontend routes and backend calls. The tenantId must match the JWT claim.

Before writing code, confirm which docs/frontend/*.md file backs your claims.
```

---

## Per-feature prompt template

Use this shape when asking the LLM to build one feature:

```
Build the {FEATURE_NAME} page for the web admin.

Context files to use verbatim:
- docs/frontend/02-api-reference.md  (endpoints)
- docs/frontend/08-typescript-dto-reference.md  (TS types)
- docs/frontend/05-role-matrix.md  (who can do what)
- docs/frontend/07-feature-flows.md  (the user journey)

Deliverables:
- src/features/{feature}/pages/{Feature}Page.tsx
- src/features/{feature}/api.ts      — endpoint wrappers + query hooks
- src/features/{feature}/schemas.ts  — zod schemas for form validation
- src/features/{feature}/components/*.tsx — supporting components

Constraints:
- Use only endpoints listed in 02-api-reference.md for this feature.
- Use only role constants from 05-role-matrix.md.
- All forms use react-hook-form + zod resolver.
- All server reads use useQuery with keys [resource, tenantId, ...filters].
- All writes use useMutation with onSuccess invalidation.
- No optimistic updates unless I explicitly ask for them.

Output format:
- One code block per file. Include the filename as the first line of the block.
- No prose between blocks — I'll read the code.
```

---

## Worked example — asking the LLM to build "Students list"

> **Prompt:**
> Build the Students list page for the web admin. Paginated table, search by name / admission number, create-student dialog, row actions for view / edit / deactivate.
>
> Context files to use: 02-api-reference.md (`GET /tenants/{tenantId}/students`, `POST /tenants/{tenantId}/students`, `DELETE /tenants/{tenantId}/students/{studentId}`), 05-role-matrix.md (create + delete require `OWNER_OR_ADMIN`; list is open to any authenticated user), 08-typescript-dto-reference.md (`StudentResponse`, `CreateStudentRequest`, `ApiResponse<T>`, `Meta`).
>
> Files to produce:
> - `src/features/students/pages/StudentsListPage.tsx`
> - `src/features/students/api.ts`
> - `src/features/students/schemas.ts`
> - `src/features/students/components/CreateStudentDialog.tsx`
> - `src/features/students/components/StudentsTable.tsx`

Expected output: 5 code blocks, each a complete file, using the exact endpoint path + DTO names from the docs. If the LLM writes `/api/students` or invents a `status` field on `StudentResponse`, it's a spec violation — stop and correct.

---

## Common mistakes to watch for in LLM output

| Mistake | What to do |
|---|---|
| Endpoint path missing `/api/v1/tenants/{tenantId}/` prefix | Reject — the backend routes are all tenant-scoped |
| Response handler expects `{ success, data }` instead of unwrapped `data` | Ask it to use the shared apiClient that unwraps the envelope |
| Role names like `"admin"` or `"teacher"` (lower-case, invented) | Must be exactly `SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `CLASS_TEACHER`, `SUBJECT_TEACHER`, `ACCOUNTANT`, `VIEWER` |
| Currency shown in rupees directly | Wire format is paise — must divide by 100 |
| Date formatting with `toLocaleString()` and no timezone handling | Use `date-fns` with explicit locale + timezone |
| Storing JWT in Redux / useState | JWT goes in one place: httpOnly cookie (server-set) or localStorage with an interceptor. See 01. |
| Optimistic update on a mutation that the server rejects 50% of the time | Don't optimistically update unless the operation is idempotent and near-certain to succeed |
| Hardcoded tenant id | `tenantId` comes from the URL via `useParams()` and must be cross-checked with the JWT claim |

---

## Testing prompts the LLM generated code

Sanity checks before merging AI-generated features:

1. **Endpoint present?** `grep` the backend for the exact path + method the frontend calls.
2. **Role gate present?** If the component renders a button for a mutating endpoint, confirm a role check wraps it.
3. **Envelope handled?** Search for `response.data` (double-unwrap) — usually a sign the LLM forgot the shared client.
4. **No hardcoded tenantId?** Search for `tenantId = "`. Any match is a bug.
5. **Types match the wire?** Open Swagger UI (`http://localhost:8080/swagger-ui/index.html`), hit the endpoint, diff the JSON shape against the TS type.

---

## Regenerating these docs

These docs are derived from the backend. When the backend changes, ask an LLM to regenerate specific files:

```
Regenerate docs/frontend/02-api-reference.md from the current backend code.
Scan:
- backend/src/main/java/in/schoolapp/**/*Controller.java
- backend/src/main/java/in/schoolapp/**/dto/*.java
Keep the existing file structure and formatting. Only update sections where the
backend actually changed.
```

Same pattern for `03-error-handling.md` (scan `ErrorCode.java`), `05-role-matrix.md` (scan all `@PreAuthorize` usages), and `08-typescript-dto-reference.md` (scan all DTOs).

---

## What the LLM should NOT do

- Propose new backend endpoints. The frontend is a consumer; backend changes need their own PR.
- Suggest using a different auth scheme (OAuth, sessions, anything). The server does JWT-only.
- Build a parent-facing page. Parents are WhatsApp-only; the web app is staff-only.
- Build mobile layouts first. This is web-first; responsive is fine but mobile has a separate React Native app with a different API contract (`sync.md`).
- Use GraphQL / tRPC / anything that isn't plain REST against the backend's routes.
