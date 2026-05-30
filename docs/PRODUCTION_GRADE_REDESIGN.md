# Production-Grade Redesign Blueprint — School Management ERP

> **Status:** Engineering blueprint. Implementation-ready.
> **Author hats:** Principal/Enterprise Architect · ERP Domain Expert · Sr. PM · QA Architect · Security Architect · DB Architect · UX Architect · Eng Director.
> **Grounding:** This is *not* generic advice. Every fix references the real codebase (`in.schoolapp.*`), the real conventions (`BaseEntity`, `TenantContext`, `ApiResponse<T>`, `ErrorCode`, `JwtAuthFilter`, `@PreAuthorize`), and the issues found in the pre-launch audit.
> **Target scale:** 1,000+ schools · 100,000+ students · 20,000+ staff · single multi-tenant deploy.

---

## 0. How to read this document

The audit found that the product is **feature-rich and architecturally sound at the edges, but unsafe at the data layer and thin on approval/audit controls**. This blueprint is organized so a senior team can pick up any section and build:

- **Part A — Foundations** (the cross-cutting fixes that everything else depends on): multi-tenant isolation, permission-based RBAC, audit-everything, approval engine.
- **Part B — Module redesigns** (per the requested module list), each with the mandated 9-point breakdown: *Fix · Why · Business value · Backend · Frontend · DB · API · RBAC · Testing.*
- **Part C — System architecture**: backend package/layers, DB schema standards, API conventions, frontend per-role design.
- **Part D — Run it in production**: security hardening, DR/backup, observability, testing strategy, deployment, SaaS strategy, go-live checklist.

Companion file: [`docs/RBAC_MATRIX.md`](RBAC_MATRIX.md) — the full permission catalog and role→permission matrix.

---

## 1. Target Non-Functional Requirements (the numbers everything is sized for)

| Dimension | Target | Design implication |
|---|---|---|
| Tenants (schools) | 1,000+ | Shared-schema multi-tenancy with `school_id` discriminator + RLS. Not schema-per-tenant (1,000 schemas × ~120 tables = unmanageable migrations). |
| Students | 100,000+ | Largest tables: `attendance_record` (100k × 220 days ≈ **22M rows/yr**), `exam_mark`, `fee_invoice`. Mandatory partitioning + composite indexes leading with `school_id`. |
| Staff | 20,000+ | Auth, RBAC, payroll at this cardinality. |
| Concurrency | 5k RPS peak (morning attendance + fee windows) | Stateless app, horizontal scale, Redis for sessions/rate-limit/idempotency, read replicas for reports. |
| Availability | 99.9% (≈8.7h/yr) | Multi-AZ DB, rolling deploys, health-gated. |
| RPO / RTO | RPO ≤ 5 min · RTO ≤ 1 h | PITR (WAL archiving) + automated restore runbook. |
| p95 latency | < 300 ms read, < 800 ms write | Indexed tenant-scoped queries, no N+1, cursor pagination. |
| Data residency | India (DPDP Act 2023) | India-region storage; per-tenant data-deletion + audit. |

---

# PART A — FOUNDATIONS (cross-cutting fixes)

These four foundations resolve the audit's systemic issues. **Build these first** — every module depends on them.

---

## A1. Multi-Tenant Isolation — defense in depth (fixes audit #1 Critical, #2 High)

### The problem (verified)
`TenantInterceptor` validates the URL `{tenantId}` against the JWT, but methods that accept a `studentId`/`parentId` argument query by that id **alone** and never check the returned row's `school_id`. Confirmed live cross-tenant IDOR in `IncidentService.forStudent`, `VaultService.listForStudent`, `PtmService.bookingsForStudent`, plus ~18 `findByStudentId…`/`findByParentId…` repository methods. Isolation today is **convention-only** — no ORM or DB safety net.

### 1. The Fix — three independent layers (any one alone is insufficient)

**Layer 1 — PostgreSQL Row-Level Security (RLS): the hard backstop.**
RLS makes cross-tenant reads *impossible at the database*, even if application code forgets a filter.

```sql
-- Flyway: V{n}__enable_rls.sql  (applied to every tenant-scoped table)
ALTER TABLE attendance_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_record FORCE ROW LEVEL SECURITY;   -- applies even to table owner

CREATE POLICY tenant_isolation ON attendance_record
  USING (school_id = current_setting('app.current_tenant')::uuid)
  WITH CHECK (school_id = current_setting('app.current_tenant')::uuid);
```

The app sets the GUC once per transaction, from a connection-acquisition hook:

```java
// common/tenant/TenantConnectionInterceptor.java — runs at tx start
@Component
@RequiredArgsConstructor
public class TenantSessionConfigurer {
    private final EntityManager em;

    /** Called by an AOP @Around on @Transactional service methods, after TenantContext is set. */
    public void bind() {
        UUID tenant = TenantContext.getTenantId();
        if (tenant == null) return; // platform/public path; RLS policies exclude these tables
        em.createNativeQuery("SET LOCAL app.current_tenant = :t")
          .setParameter("t", tenant.toString())
          .executeUpdate();
    }
}
```
> `SET LOCAL` scopes the GUC to the current transaction, so it cannot leak across pooled connections.

**Layer 2 — Hibernate `@TenantId` (ORM-level auto-filter).**
Hibernate 6 supports `@TenantId`; it auto-appends `school_id = ?` to every query and auto-populates it on insert. Promote `BaseEntity.schoolId` to a tenant discriminator:

```java
// common/BaseEntity.java — evolve the existing field
@TenantId
@Column(name = "school_id", nullable = false, updatable = false)
private UUID schoolId;
```
Wire a `CurrentTenantIdentifierResolver` that reads `TenantContext.getTenantId()`. After this, **`findByStudentId(studentId)` is automatically `… AND school_id = :currentTenant`** — the IDOR closes without touching 18 repositories.

**Layer 3 — Explicit object-ownership guard for defense-in-depth + clear 404s.**
A tiny reusable guard so services fail with a clean `RESOURCE_NOT_FOUND` instead of leaking existence:

```java
// common/tenant/TenantGuard.java
@Component @RequiredArgsConstructor
public class TenantGuard {
    /** Throws 404 (not 403 — don't reveal the row exists in another tenant) if not owned. */
    public <T extends BaseEntity> T owned(Optional<T> row, String type, UUID id) {
        return row.filter(r -> r.getSchoolId().equals(TenantContext.getTenantId()))
                  .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, type, id));
    }
}
```
Repositories standardize on `findByIdAndSchoolId(...)`; ban single-arg `findById` on tenant entities via an ArchUnit test (see A-tests).

### 2. Why needed
A single forgotten `schoolId` is a reportable personal-data breach under DPDP. Convention cannot be the only control across 40 modules and a growing team.

### 3. Business value
This *is* the SaaS trust boundary. One cross-tenant leak ends the company. RLS turns "we hope every query is scoped" into "the database guarantees it."

### 4. Backend impact
Add `CurrentTenantIdentifierResolver`, the tx-bind AOP aspect, `TenantGuard`. Remove ad-hoc `findByStudentId`-only methods or let `@TenantId` neutralize them. Background jobs/schedulers that span tenants must run with an explicit per-tenant loop (set context per tenant) — never with RLS disabled.

### 5. Frontend impact
None functionally; previously-leaking endpoints now correctly 404 for foreign ids. Remove any client code that "worked" by passing arbitrary ids.

### 6. Database impact
RLS policies on every tenant table (Flyway). A dedicated non-superuser app DB role (superusers bypass RLS — **the app must not connect as superuser**). Confirm `FORCE ROW LEVEL SECURITY`.

### 7. API impact
No contract change. Cross-tenant access now returns `404 RESOURCE_NOT_FOUND` uniformly.

### 8. RBAC impact
Isolation is orthogonal to RBAC and runs *before* it. Document: tenant boundary (RLS) → authentication → permission check → object guard.

### 9. Testing
- **Security/integration:** for every by-id and by-student endpoint, a test where caller's JWT tenant ≠ resource tenant must return 404. Parameterize across all modules.
- **RLS unit:** open a tx without setting the GUC → queries return 0 rows (proves FORCE works).
- **ArchUnit:** fail the build if any repository on a `BaseEntity` exposes a finder without `SchoolId` or `@TenantId` coverage.
- **Concurrency:** pooled-connection test proving `SET LOCAL` never bleeds tenant A's GUC into tenant B's request.

---

## A2. Permission-Based RBAC (fixes audit #3, #4, #15 + coarse role checks)

### The problem (verified)
Authorization is role-string checks (`hasAnyRole(...)`). Read endpoints mostly have **no** `@PreAuthorize`, so any authenticated user reads all PII (ACCOUNTANT/VIEWER included); VIEWER's "no PII" promise is unenforced; one write endpoint has no guard; there is **no Receptionist role** despite a visitor module; and "CLASS_TEACHER = own section only" is enforced for *writes* but not *reads*.

### 1. The Fix — move from roles to a **permission catalog**, with roles as named permission bundles

Introduce a fixed catalog of fine-grained permissions (verbs on resources), assign them to roles via a `role_permission` mapping (seeded per tenant, customizable later), and embed the *effective permission set* in the JWT. Object-scope (own-section/own-subject) stays a service-layer guard.

```java
// auth/Permission.java — the catalog (excerpt; full list in RBAC_MATRIX.md)
public enum Permission {
    STUDENT_READ, STUDENT_READ_PII, STUDENT_WRITE, STUDENT_DELETE,
    ATTENDANCE_READ, ATTENDANCE_WRITE, ATTENDANCE_CORRECT, ATTENDANCE_APPROVE,
    MARKS_WRITE, MARKS_VERIFY, RESULT_PUBLISH, RESULT_REOPEN,
    FEE_COLLECT, FEE_INVOICE_WRITE, FEE_DISCOUNT_REQUEST, FEE_DISCOUNT_APPROVE, FEE_REFUND_APPROVE,
    EXPENSE_RECORD, EXPENSE_APPROVE, CASH_RECONCILE,
    LEAVE_REQUEST, LEAVE_APPROVE_L1, LEAVE_APPROVE_L2,
    VISITOR_MANAGE, LIBRARY_ISSUE, AUDIT_READ, ...
}
```

Custom method-security expression so controllers read naturally:

```java
@PreAuthorize("hasPermission('FEE_DISCOUNT_APPROVE')")     // replaces hasAnyRole(...)
```
backed by a `PermissionEvaluator`/custom security expression root that checks the JWT's `perms` claim.

**Roles become bundles** (seed data, table `role_permission`). New roles cost a row, not code:

| Role | Bundle highlights |
|---|---|
| `RECEPTIONIST` *(new)* | `VISITOR_MANAGE`, `STUDENT_READ` (no PII), `ENQUIRY_WRITE`, `INBOX_READ`. **Resolves audit #15.** |
| `COUNSELOR` *(new, optional)* | `INCIDENT_READ/WRITE`, `STUDENT_READ`. |
| `VIEWER` | `*_READ` **without** any `_PII` or `_WRITE`. PII masking now enforced by the absence of `STUDENT_READ_PII` (fixes #3). |
| `ACCOUNTANT` | `FEE_*` (request, not approve), `EXPENSE_RECORD`, `CASH_RECONCILE`, `STUDENT_READ` (no PII beyond fee context). Narrowed to actually be "fee only" (fixes over-broad reads). |

**PII masking** becomes a serialization concern driven by permission:

```java
// student/dto/StudentResponse — phone serialized via a permission-aware serializer
public String phone() {
    return SecurityCtx.has(Permission.STUDENT_READ_PII) ? phone : PhoneNormalizer.mask(phone);
}
```

**Reads get guarded too.** Every GET declares its read permission (`@PreAuthorize("hasPermission('STUDENT_READ')")`). The default-open era ends.

### 2. Why needed
Role strings can't express "read but not PII," "request vs approve," or new front-desk roles without code changes. The audit's read-side leakage and missing Receptionist are direct symptoms.

### 3. Business value
Schools differ wildly in who-does-what. Tenant-customizable role→permission mapping is a sales differentiator and removes a whole class of "can you make a role that…" change requests.

### 4. Backend impact
Add `Permission`, `role_permission` table + seeder, custom `MethodSecurityExpressionHandler`, JWT `perms` claim, permission-aware DTO serialization. Migrate every controller from `hasAnyRole` to `hasPermission`. Keep object-scope guards (`AttendanceService` section check, `MarksService` assignment check) — they're the verified strengths.

### 5. Frontend impact
Client receives `perms[]` (not just role) at login; `RequireRole` becomes `RequirePermission`. Menus/buttons gate on permissions. Single source of truth shared with backend catalog.

### 6. Database impact
`permission` (static enum, no table needed), `role` (per-tenant customizable), `role_permission`, `staff_role` (supports **multiple roles per staff** — see Staff module). Seed defaults on tenant creation.

### 7. API impact
`GET /me` returns `{ role(s), perms[] }`. New admin endpoints: `GET/PUT /tenants/{t}/roles/{role}/permissions` (guarded by `RBAC_MANAGE`).

### 8. RBAC impact
This *is* the RBAC redesign. See [`RBAC_MATRIX.md`](RBAC_MATRIX.md) for the full matrix.

### 9. Testing
- **RBAC matrix test (data-driven):** a generated test per (endpoint × role) asserting allow/deny — the matrix file is the fixture.
- **Negative:** VIEWER receives masked phone; ACCOUNTANT 403 on `STUDENT_READ_PII`.
- **Token:** `perms` claim tamper → rejected (signature); permission removed at runtime → enforced on next token refresh (document the staleness window = access-token TTL).

---

## A3. Audit-Everything + Change Tracking (fixes audit #25, #7)

### The problem (verified)
`AuditLogger` is called in ~41 places but **not** on marks entry, attendance submission, fee discounts, refunds, invoice generation, report-card generation, admission status changes. The audit log is the basis of dispute resolution — and the riskiest actions aren't in it.

### 1. The Fix — make audit structural, not optional

**(a) Append-only `audit_log`** (already exists; standardize the schema):
`id, school_id, actor_staff_id, action, entity_type, entity_id, before_json, after_json, request_id, ip, user_agent, created_at`. Insert-only; `REVOKE UPDATE, DELETE` from the app role.

**(b) Auto-capture field-level changes via Hibernate Envers** for the high-value entities (`ExamMark`, `AttendanceRecord`, `FeeInvoice`, `FeeDiscount`, `FeeAdjustment`, `Student`, `Staff`, `SalaryStructure`). Envers writes `*_aud` tables automatically on every change — no service code to forget.

```java
@Entity @Audited            // Envers tracks every column change with a revision + actor
public class ExamMark extends BaseEntity { ... }
```

**(c) A `@Auditable` AOP aspect** for action-level intent (e.g. `RESULT_PUBLISH`, `FEE_REFUND`) where Envers' row-diff isn't enough:

```java
@Auditable(action = AuditAction.FEE_REFUND, entity = "FeePayment")
public FeeAdjustment refund(UUID tenantId, RefundRequest req) { ... }
```

**(d) "Nothing editable without history" rule, enforced:** the same ArchUnit suite that bans unscoped finders also asserts every `@Entity` that is mutable and financial/academic is `@Audited`.

### 2. Why needed
Parent disputes a grade or a waiver; the school must show who/when/what-before-after. Compliance and trust depend on it.

### 3. Business value
Audit trail is a procurement checkbox for any school board and the strongest internal-fraud deterrent (esp. fee discounts/refunds).

### 4–8. Impact
- **Backend:** Envers dependency + `@Audited` annotations + `@Auditable` aspect; `AuditLogger` becomes the aspect's sink.
- **Frontend:** a "History" tab on student, invoice, mark, and staff screens (`GET …/audit?entityType=&entityId=`).
- **DB:** Envers `_aud` tables + `revinfo` (revision → actor/timestamp). These grow fast → partition by month, archive to cold storage after 18 months.
- **API:** `GET /tenants/{t}/audit` (paged, filterable by entity/actor/date) — permission `AUDIT_READ`.
- **RBAC:** `AUDIT_READ` for OWNER/PRINCIPAL/auditor only.

### 9. Testing
Edit a mark → assert an `exam_mark_aud` row with correct before/after + actor; attempt UPDATE on `audit_log` as app role → permission denied; verify revision actor matches `TenantContext.getStaffId()`.

---

## A4. Generic Approval / Maker-Checker Engine (fixes audit #6, #7, #8, #9, #10)

### The problem (verified)
`FeeDiscount.approvedById` and `FeeAdjustment.approvedById` columns **exist but are never set**. Discounts/refunds/expenses have no approval; cash variance is recorded but never escalated; staff leave can be self-approved.

### 1. The Fix — one reusable approval engine, many request types

Rather than bolt approval onto each module, build a small generic workflow service:

```java
// approval/ApprovalRequest.java
@Entity @Audited
public class ApprovalRequest extends BaseEntity {
    private ApprovalType type;        // FEE_DISCOUNT, FEE_REFUND, EXPENSE, LEAVE, RESULT_REOPEN, CASH_VARIANCE...
    private UUID subjectId;           // the FeeDiscount/Expense/Leave id
    private String payloadJson;       // proposed change (applied only on final approval)
    private ApprovalStatus status;    // PENDING_L1 → PENDING_L2 → APPROVED | REJECTED | CANCELLED
    private UUID requestedById;
    private BigInteger amountPaise;   // drives threshold-based routing
}
```

**Threshold-driven routing** (config per tenant): e.g. discount ≤ ₹2,000 → ADMIN approves; ≤ ₹20,000 → PRINCIPAL; above → OWNER. Multi-level for leave (L1 reporting manager → L2 principal).

**Apply-on-approve:** the proposed change is *staged in `payloadJson`* and only mutates the domain row when the final approver approves — so an unapproved discount never affects an invoice.

**Self-approval ban (fixes #10):** engine rejects if `approverId == requestedById` for any level; enforced centrally, not per module.

### 2. Why needed
Money and academic-integrity actions need segregation of duties. The columns already anticipate this; the workflow is the missing half.

### 3. Business value
Eliminates the #1 internal-fraud vector (unapproved concessions/refunds). Gives owners spend visibility and a real control framework — a board-level requirement.

### 4–8. Impact
- **Backend:** `approval` module (engine, threshold config, listeners). Fee/expense/leave/result services create an `ApprovalRequest` instead of mutating directly; an `ApprovalDecidedEvent` listener applies the staged change.
- **Frontend:** an **Approvals inbox** per approver role (count badge), with approve/reject + reason; "pending approval" state on the underlying record.
- **DB:** `approval_request`, `approval_step`, `approval_policy` (tenant-configurable thresholds). Indexed `(school_id, status, type)`.
- **API:** `POST /tenants/{t}/approvals/{id}/approve|reject`, `GET …/approvals?status=PENDING&assignee=me`.
- **RBAC:** `*_APPROVE` permissions; level routing via policy.

### 9. Testing
Maker cannot approve own request (negative); discount not applied until final approval (state); threshold routes to correct level (boundary: exactly at threshold); concurrent double-approve → only one wins (optimistic lock / idempotent decision).

---

# PART B — MODULE REDESIGNS

Each module below follows the mandated 9-point structure. Foundations (A1–A4) are assumed.

---

## B1. Tenant Management

**Scope:** registration · onboarding · subscription plans · isolation · configuration · branding.

1. **Fix.**
   - *Registration/onboarding:* keep the existing public `POST /tenants` (creates school + owner + academic year). Add an **onboarding state machine** (`CREATED → PROFILE → ACADEMIC_SETUP → STAFF_IMPORTED → FEE_SETUP → LIVE`) surfaced by the existing `onboarding-status` endpoint, each step idempotent and resumable.
   - *Isolation:* A1 (RLS + `@TenantId`).
   - *Subscription:* the existing trial→past_due→grace→suspended machine and `SubscriptionGuardInterceptor` are good — harden by caching `effectiveStatus` in Redis (TTL 60s) to avoid a status query per write.
   - *Config/branding:* keep `tenantconfig` (masked provider secrets) and `branding`; add a per-tenant `tenant_settings` JSONB for school-tunable policy (min attendance %, grading scheme, late-fee rules, quiet hours) read through a typed `SchoolSettings` facade.
2. **Why.** Onboarding is where pilots succeed or die; resumable steps reduce drop-off. Cached subscription status removes a hot per-request query at 5k RPS.
3. **Business value.** Faster time-to-live per school = lower CAC; clean trial/grace lifecycle drives conversion.
4. **Backend.** `OnboardingService` state machine; Redis-cached `SubscriptionService.effectiveStatus`; `SchoolSettings` typed accessor over JSONB.
5. **Frontend.** Onboarding wizard with progress; "X days left in trial" banner; branding upload (logo, colors) preview.
6. **DB.** `school`, `subscription`, `subscription_event`, `plan`, `plan_limit`, `tenant_settings(jsonb)`. Index `subscription(school_id)` unique.
7. **API.** `POST /tenants`, `GET /tenants/{t}/onboarding-status`, `PUT /tenants/{t}` (branding/settings), platform `/platform/tenants/*` (SUPER_ADMIN).
8. **RBAC.** Tenant CRUD: `TENANT_MANAGE` (OWNER/PRINCIPAL/ADMIN minus pricing); plan/pricing: SUPER_ADMIN only.
9. **Testing.** Onboarding resumes from any step; suspended tenant blocks writes but allows reads; trial→grace→suspended scheduler transitions (time-travel clock); branding asset stored under tenant-scoped path.

---

## B2. Authentication & Authorization (enterprise-grade)

**Scope:** login · JWT · refresh · password reset · sessions · device management · MFA · RBAC · permission matrix.

1. **Fix.**
   - *Tokens:* short-lived access JWT (10–15 min) carrying `tenantId, staffId, role(s), perms[], jti`; **rotating refresh tokens** (already have `RefreshTokenService`) stored hashed, single-use, with reuse-detection (if an already-rotated refresh token is presented → revoke the whole family = stolen-token response).
   - *Sessions/devices:* a `user_session` row per (staff, device) with `device_id, user_agent, ip, last_seen, revoked_at`; "log out everywhere" revokes all. Access tokens stay stateless but checked against a Redis **revocation set** keyed by `jti`/family on logout/role-change.
   - *Password reset:* OTP→reset token (single-use, 15-min, hashed) → set password (BCrypt/Argon2id). Invalidate all sessions on reset.
   - *MFA:* TOTP (RFC 6238) for OWNER/PRINCIPAL/ADMIN/ACCOUNTANT; recovery codes; enforce on privileged roles via tenant policy.
   - *RBAC:* A2.
2. **Why.** Refresh-token theft and shared logins are the realistic attack vectors for school staff. Reuse-detection + device list + MFA on money/admin roles close them.
3. **Business value.** "Enterprise security" is a sales gate for larger/private-board schools; reduces account-takeover support load.
4. **Backend.** `auth` module: `JwtService` (add `perms`, `jti`, `kid` for key rotation), `RefreshTokenService` (rotation + family revoke), `SessionService`, `MfaService`, Redis revocation. Keep `JwtAuthFilter` shape; add `jti` revocation check.
5. **Frontend.** Login + optional OTP/TOTP step; "Active devices" screen; forced re-auth on sensitive actions; silent refresh before access-token expiry.
6. **DB.** `refresh_token(hash, family_id, rotated_at, revoked_at)`, `user_session`, `mfa_secret`, `password_reset_token`. All indexed by `(school_id, staff_id)`.
7. **API.** `POST /auth/otp/send|verify`, `/auth/token/refresh`, `/auth/logout`, `/auth/logout-all`, `/auth/mfa/enroll|verify`, `/auth/password/reset`. `GET /me` → role(s)+perms.
8. **RBAC.** MFA-required permissions flagged; `SESSION_MANAGE_OWN` for all; `SESSION_REVOKE_ANY` for admins.
9. **Testing.** Refresh rotation single-use; reuse → family revoked (security); expired access → 401 `TOKEN_INVALID`; MFA bypass attempt; concurrent refresh race (only one new token issued); logout revokes `jti` immediately.

---

## B3. Staff Management & Employee Lifecycle

**Scope:** staff/teacher/accountant/librarian/receptionist registration · multiple roles · lifecycle.

1. **Fix.**
   - *Multiple roles:* replace single `Staff.role` with a `staff_role` join (a teacher who is also the librarian). JWT carries all roles; perms = union.
   - *Lifecycle state machine:* `INVITED → ACTIVE → ON_LEAVE → SUSPENDED → RESIGNED/TERMINATED → ARCHIVED`. **Deactivation is a guarded transition** (fixes audit #19): on `RESIGNED`, the system *blocks* if the staff is a current class teacher / has future timetable / open approvals, and forces reassignment first.
   - *Onboarding:* document checklist (ID, certificates, contract), verification flag before they can be assigned teaching duties.
2. **Why.** Real schools have multi-hat staff and need clean exits; dangling references (deactivated teacher still on timetable) cause operational chaos and payroll errors.
3. **Business value.** Accurate staff records feed payroll, RBAC, and compliance; clean offboarding prevents ex-staff access.
4. **Backend.** `staff_role` join; `StaffLifecycleService` with guarded transitions + reassignment checks; document checklist service; deactivation publishes `StaffDeactivatedEvent` → consumers revoke sessions, flag timetable/assignment gaps.
5. **Frontend.** Staff profile with roles (multi-select), lifecycle status, document checklist, "cannot deactivate — resolve 3 dependencies" blocker UI.
6. **DB.** `staff`, `staff_role(staff_id, role)`, `staff_document`, `staff_lifecycle_event`. Index `(school_id, status)`, `(school_id, role)`.
7. **API.** `POST /staff`, `POST /staff/{id}/roles`, `POST /staff/{id}/status` (transition), `GET /staff?role=&status=`.
8. **RBAC.** `STAFF_WRITE` (OWNER/PRINCIPAL/ADMIN); `STAFF_DELETE` OWNER/PRINCIPAL only (matches current narrow guard).
9. **Testing.** Deactivate blocked while class-teacher (negative); multi-role perms union; offboarding revokes sessions; re-hire re-activates without duplicate.

---

## B4. Teacher Allocation

**Scope:** class-teacher · subject-teacher · multi-subject · multi-class · academic-year · historical tracking.

1. **Fix.**
   - All allocations become **academic-year-scoped and temporal** (`valid_from/valid_to`), never hard-deleted → full history.
   - *Conflict detection:* on assign, validate (a) section has exactly one active class teacher, (b) no teacher timetable clash, (c) teacher is `ACTIVE` and not on long leave, (d) subject coverage gap report.
   - The verified strength — `MarksService`/`AttendanceService` checking assignment — is preserved and now reads from the temporal table for the *current* year.
2. **Why.** Audit found no conflict checks beyond class-teacher uniqueness; results/attendance authority derive from these rows, so they must be correct and historical (for re-issuing last year's report card).
3. **Business value.** Correct allocation = correct authority = correct marks/attendance; history enables audits and year-rollover.
4. **Backend.** `TeacherSubjectAssignment` + `class_teacher_assignment` gain `academic_year_id, valid_from, valid_to`; `AllocationConflictService`; year-rollover copies/prompts re-allocation.
5. **Frontend.** Allocation grid (section × subject → teacher), conflict warnings inline, "coverage gaps" panel.
6. **DB.** Unique partial index `(school_id, section_id) WHERE role=CLASS_TEACHER AND valid_to IS NULL`; `(staff_id, subject_id, section_id, academic_year_id)` unique active.
7. **API.** `POST /teacher-assignments`, `GET /teacher-assignments?academicYearId=&sectionId=`, `GET /sections/{id}/coverage-gaps`.
8. **RBAC.** `ALLOCATION_MANAGE` (OWNER/PRINCIPAL/ADMIN).
9. **Testing.** Double class-teacher rejected; timetable clash rejected; historical query returns prior-year allocation; deactivated teacher cannot be assigned.

---

## B5. Student Management

**Scope:** admission · promotion · section transfer · leaving · alumni · re-admission (with validations + approvals).

1. **Fix.**
   - *Admission* (fixes #14): enquiry→application→test→offer→**capacity-checked enrollment**; add seat capacity per section, duplicate-applicant detection (phone/email/name+DOB fuzzy), application fee, document verification gate, and a waitlist state.
   - *Promotion:* keep idempotent `PromotionService`; add "result-finalized" precondition + bulk preview.
   - *Section transfer:* explicit workflow with reason + audit; carries attendance/fee context.
   - *Leaving* (fixes #13): `WITHDRAWAL_REQUESTED → FEE_CLEARANCE → TC_ISSUED → LEFT`; **TC issuance blocked if dues outstanding**; sets `Student.status = LEFT` (add status to `Student`, not just enrollment).
   - *Alumni & re-admission:* `GRADUATED`/`LEFT` → alumni record; re-admission reactivates with new enrollment, preserving history.
2. **Why.** Audit found over-enrollment risk, no dues gate on exit, no alumni/status on the student itself.
3. **Business value.** Stops uncollectible dues walking out the door; accurate headcount for board/UDISE; alumni enables fundraising/engagement.
4. **Backend.** `AdmissionService` + capacity/dup/doc validators; `StudentLifecycleService`; `WithdrawalService` with fee-clearance check; alumni projection.
5. **Frontend.** Admissions pipeline (kanban), capacity meter per section, withdrawal wizard with live dues, alumni directory.
6. **DB.** `Student.status` enum; `admission`, `admission_document`, `section_capacity`, `withdrawal`, `alumni`. Index `(school_id, status)`, unique active enrollment per (student, year).
7. **API.** `POST /admissions/*`, `POST /students/{id}/transfer`, `POST /students/{id}/withdraw`, `GET /admissions?status=`.
8. **RBAC.** `ADMISSION_MANAGE`, `STUDENT_WRITE`; withdrawal final approval `STUDENT_WITHDRAW_APPROVE`.
9. **Testing.** Enroll beyond capacity rejected; duplicate applicant flagged; TC blocked with dues (negative); promotion idempotent; re-admission preserves prior history.

---

## B6. Attendance Management

**Scope:** student (daily, period-wise, corrections, locking, approval) · staff (self, biometric, check-in/out, late, early exit).

1. **Fix.**
   - *Student:* keep reverse-marking + section-scoped class-teacher write (verified strength). Add **period-wise** mode (subject-teacher marks their period) configurable per school. Add a **correction workflow**: after the daily lock (cron at, e.g., 16:00), edits require an `ATTENDANCE_CORRECT` request → approval (via A4) → audited change (Envers). Daily auto-lock + principal override.
   - *Staff:* self check-in/out endpoints; **biometric/RFID integration** via a device webhook ingest (`POST /devices/attendance` HMAC-signed) mapping device punches to `StaffAttendance`; late-arrival/early-exit derived from shift policy; feeds payroll **only after attendance is approved/locked** (fixes #11).
2. **Why.** Audit: attendance not audited and feeds payroll unverified; no correction workflow; staff biometrics absent.
3. **Business value.** Trustworthy attendance underpins payroll, fee-eligibility, and at-risk alerts; biometric removes manual staff marking.
4. **Backend.** `AttendanceService` (+period mode), `AttendanceCorrectionService` (approval-gated), `BiometricIngestService` (idempotent by device punch id), shift/late policy.
5. **Frontend.** Class grid (present-by-default), period tabs, "locked — request correction" state, staff self check-in button + map/time, biometric dashboard.
6. **DB.** `attendance_record` **partitioned by month** (`RANGE (date)`); composite index `(school_id, section_id, date)` and `(school_id, student_id, date)`; `staff_attendance`, `attendance_correction`. Envers on both.
7. **API.** `POST /sections/{id}/attendance`, `POST /sections/{id}/attendance/period/{periodId}`, `POST /attendance/{id}/correction`, `POST /staff-attendance/check-in|out`, `POST /devices/attendance`.
8. **RBAC.** `ATTENDANCE_WRITE` (class/subject teacher, scoped), `ATTENDANCE_CORRECT`/`_APPROVE`.
9. **Testing.** Non-assigned teacher 403; post-lock edit requires approval; biometric double-punch idempotent; payroll ignores unapproved attendance; partition pruning verified on date-range query.

---

## B7. Leave Management

**Scope:** request · approval · multi-level · balance · carry-forward · reports.

1. **Fix.** Built on A4 engine. **Multi-level approval** (L1 reporting manager → L2 principal) via `approval_policy`; **self-approval banned**; balance checked *before* approval (no silent auto-seed — fixes #10); **carry-forward** job at year-end with per-type caps; approved leave optionally auto-creates `StaffAttendance(LEAVE)` and triggers substitute suggestion (links to B8).
2. **Why.** Audit found flat approval, self-approval, infinite auto-seed, no carry-forward, no linkage.
3. **Business value.** Correct leave → correct payroll; transparent balances reduce HR disputes.
4. **Backend.** `LeaveService` delegates decisions to approval engine; `LeaveBalanceService` (no auto-seed at decision; seeded at onboarding/year-start); `CarryForwardScheduler`.
5. **Frontend.** Leave apply form with live balance; approver inbox; balance + history report; calendar of who's out.
6. **DB.** `leave_application`, `leave_balance(entitled, consumed, carried_forward)`, `leave_type`, `approval_request`. Unique `(staff, type, year)`.
7. **API.** `POST /leave`, `POST /approvals/{id}/approve`, `GET /leave/balance`, `GET /leave/reports`.
8. **RBAC.** `LEAVE_REQUEST` (all staff), `LEAVE_APPROVE_L1/L2`.
9. **Testing.** Self-approve rejected; over-balance rejected at apply; multi-level routing; carry-forward cap; overlap rejection (already present) retained.

---

## B8. Timetable Management

**Scope:** teacher/student timetable · room allocation · conflict detection · auto-validation.

1. **Fix.** Keep verified teacher double-booking check; **add room as a first-class resource** with room-clash detection; add **per-teacher load caps** (max periods/day, /week), period **time-overlap** validation, and **deactivated/on-leave teacher** guard. Unify the two substitution systems (audit #20) into one `timetable_substitution` and link approved leave → substitute suggestion.
2. **Why.** Audit: no room entity, no load caps, no time-overlap check, dual substitution sources.
3. **Business value.** A clash-free, room-aware timetable is a daily operational necessity; substitution automation saves the front office every morning.
4. **Backend.** `TimetableService` + `Room` + `ConflictService` (teacher, room, load, availability); leave-event listener proposes substitutes.
5. **Frontend.** Drag-drop grid with live clash highlighting; room view; teacher view; "today's substitutions" board.
6. **DB.** `timetable_period`, `timetable_entry(section, day, period, subject, teacher, room)`, `room`, `timetable_substitution`. Unique `(section, day, period)` and `(teacher, day, period)` partial active; `(room, day, period)`.
7. **API.** `POST /timetable/entries`, `GET /timetable/teacher/{id}`, `GET /timetable/section/{id}`, `POST /timetable/substitutions`.
8. **RBAC.** `TIMETABLE_MANAGE`; teachers read own.
9. **Testing.** Teacher clash, room clash, load-cap, time-overlap all rejected (boundary at exact cap); on-leave teacher rejected; substitution single source.

---

## B9 & B10. Examination & Result Management

**Scope:** exam creation/scheduling · theory/practical/internal marks · grade calc · result processing · marks entry · **verification → approval → lock → reopen → audit**.

1. **Fix.**
   - *Exam:* component model — `theory + practical + internal` per subject with weightages and independent pass criteria; scheduling with datesheet + clash check.
   - *Result workflow* (fixes #12): formal state machine **`DRAFT → ENTERED → VERIFIED → APPROVED → PUBLISHED`** with `REOPENED` as an audited side-state. Marks entered by subject teacher → **verified by a second teacher/exam-incharge** (`MARKS_VERIFY`) → **approved by principal** (`RESULT_PUBLISH`) → published to parents. Reopen requires `RESULT_REOPEN` + reason and **versions the report card** (no silent overwrite).
   - Grade/division/rank: keep `GradeCalculator` (board-specific) + dense rank; add stream/division logic; recompute on any mark change with full Envers history.
2. **Why.** Audit: no moderation gate before publish; revisions overwrite without versioning.
3. **Business value.** Result accuracy is reputation-critical and board-mandated; the verify/approve gate prevents the classic "wrong marks went to 400 parents" incident.
4. **Backend.** `ExamService` (components/schedule), `MarksService` (keeps assignment-scope check), `ResultWorkflowService` (state machine + approval), `ReportCardService` (versioned PDFs).
5. **Frontend.** Marks grid with per-component columns + validation; verifier and approver queues; "result locked/published" badges; report-card version history.
6. **DB.** `exam`, `exam_subject_config(theory_max, practical_max, internal_max, pass_*)`, `exam_mark` `@Audited`, `report_card(version)`, result-status columns. Index `(school_id, exam_id, section_id)`.
7. **API.** `POST /exams`, `POST /exams/{id}/marks`, `POST /exams/{id}/verify/{section}`, `POST /exams/{id}/publish`, `POST /exams/{id}/reopen` (reason).
8. **RBAC.** `MARKS_WRITE` (scoped subject teacher), `MARKS_VERIFY`, `RESULT_PUBLISH`, `RESULT_REOPEN`.
9. **Testing.** Subject teacher cannot publish; publish blocked until verified; reopen creates new report-card version + audit; mark edit after publish requires reopen; grade boundaries (e.g., 32.9 vs 33 pass).

---

## B11. Fee Management

**Scope:** structure · installments · discounts · scholarships · concessions · fine · refunds · gateway/UPI/online (with auditability).

1. **Fix.**
   - *Structure/installments:* keep versioned `FeeStructure` matrix; add term/installment plans with due dates.
   - *Discounts/scholarships/concessions* (fixes #6): all created as **approval requests** (A4); `approvedById` populated by the engine; audited (Envers); per-role caps.
   - *Refunds/reversals* (fixes #7): approval-gated + audited; add receipt void (audited, never hard delete).
   - *Fines:* keep idempotent late-fee scheduler; expose policy in `tenant_settings`.
   - *Gateway/UPI* (fixes #24): make webhook→`FeePayment` **durable** — webhook writes to `inbox_event` table (transactional) then a poller/worker creates the payment with retry + DLQ, replacing fire-and-forget; add cash-payment idempotency key.
   - *Cash recon* (fixes #9): variance over threshold auto-creates a `CASH_VARIANCE` approval/escalation.
2. **Why.** Money module had the most missing controls; durability gap means paid-but-unrecorded payments.
3. **Business value.** Revenue integrity, fraud prevention, and "no parent wrongly marked defaulter" — directly protects cash and reputation.
4. **Backend.** `FeeDiscountService`/`FeeRefundService` → approval engine; `WebhookInboxService` + `PaymentReconciliationWorker`; idempotency on `quickCollect`.
5. **Frontend.** Collect screen (idempotent), discount-request flow with approval status, refund request, daily collection register, reconciliation with variance escalation.
6. **DB.** `fee_structure*`, `fee_invoice` `@Audited`, `fee_payment` (immutable receipt no., unique `(school_id, receipt_number)`), `fee_discount`/`fee_adjustment` `@Audited` (+`approved_by_id`), `webhook_inbox_event`. Partition `fee_payment` by academic year.
7. **API.** `POST /fees/payments` (Idempotency-Key header), `POST /fees/discounts` (→approval), `POST /fees/refunds` (→approval), `GET /fees/collection-register?date=`.
8. **RBAC.** `FEE_COLLECT`, `FEE_DISCOUNT_REQUEST`/`_APPROVE`, `FEE_REFUND_APPROVE`, `CASH_RECONCILE`.
9. **Testing.** Discount not applied pre-approval; refund requires approval + audit; double quick-collect with same Idempotency-Key → one payment; dropped webhook retried from inbox; cash variance escalates; receipt numbers gap-free under concurrency.

---

## B12. Library Management

**Scope:** inventory · issue/return · reservations · fines · reports.

1. **Fix.** Add per-student/class **issue limits**, **renewals**, **reservations/holds** queue, **lost/damaged** workflow, and **post library fines to the fee ledger** (one source of money — fixes #18). Enforce `LIBRARY_ISSUE` permission in the service.
2. **Why.** Audit: library was issue/return only; fines disconnected from fees.
3. **Business value.** Complete library ops + fines actually collected via the fee system.
4. **Backend.** `LibraryService` (+limits/renew/reserve/lost), `LibraryFineService` → creates a `fee_adjustment`/invoice line.
5. **Frontend.** Catalogue search, issue/return, holds queue, per-student history, overdue report.
6. **DB.** `book`, `book_copy`, `book_issue` `@Audited`, `book_reservation`, `library_fine`. Index `(school_id, student_id)`.
7. **API.** `POST /library/issue|return|renew|reserve`, `GET /library/overdue`.
8. **RBAC.** `LIBRARY_ISSUE`/`LIBRARY_MANAGE` (LIBRARIAN + admins).
9. **Testing.** Over-limit issue rejected; renewal cap; reservation fulfilled in order; lost book → fee adjustment created; fine appears on student fee summary.

---

## B13. Communication — Trigger-Based Notification Engine

**Scope:** email · SMS · WhatsApp · push, trigger-based.

1. **Fix.** Formalize a **notification engine**: domain events → `NotificationRule` (per tenant, per event type, per channel) → `NotificationService` resolves recipients + template + channel preference → dispatch via existing provider abstractions → `notification_log`. Add (fixes #22): **per-parent preferences/opt-out**, **quiet hours** (suppress/queue outside window), **delivery retry + DLQ**, **failure visibility** to school, and **bulk throttling/batching**. All sends remain on the **transactional outbox** (fix the multi-instance race — A-infra below).
2. **Why.** Audit: notifications had no opt-out, quiet hours, retry visibility, or throttling; outbox not multi-instance safe.
3. **Business value.** Compliance (consent/DLT), parent satisfaction, deliverability, and cost control on paid channels.
4. **Backend.** `notification` module: `NotificationRule`, `NotificationService`, `ChannelPreferenceService`, `QuietHoursPolicy`, `RetryWorker` + DLQ. **Outbox poller uses `FOR UPDATE SKIP LOCKED`** (fixes #5) for safe multi-instance.
5. **Frontend.** Parent preference center; school-side notification log with status/failure + resend; rule config UI.
6. **DB.** `notification_log` (status: PENDING/SENT/DELIVERED/FAILED/READ), `notification_rule`, `channel_preference`, `outbox_event`. Index `(school_id, status, created_at)`.
7. **API.** `GET /notifications/log`, `POST /notifications/{id}/resend`, `PUT /parents/{id}/preferences`.
8. **RBAC.** `COMMS_SEND`, `COMMS_CONFIG`; parents manage own preferences.
9. **Testing.** Quiet-hours suppression/queue; opt-out respected; failed send retried then DLQ'd; **two app instances do not double-send** (SKIP LOCKED); throttle caps per-minute volume.

---

# PART C — SYSTEM ARCHITECTURE

## C1. Backend Architecture (Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Redis · Docker)

**Modular-monolith package structure** (keep the proven `in.schoolapp.<module>`; each module is a vertical slice):

```
in.schoolapp
├── common/            BaseEntity, TenantContext, ApiResponse, ErrorCode, AppException, GlobalExceptionHandler
│   ├── tenant/        CurrentTenantIdentifierResolver, TenantGuard, RLS session configurer
│   ├── outbox/        OutboxEvent, OutboxPoller (FOR UPDATE SKIP LOCKED), OutboxService
│   └── idempotency/   IdempotencyFilter (tenant-scoped key)
├── auth/              JWT, refresh rotation, sessions, MFA, Permission, PermissionEvaluator
├── approval/          generic maker-checker engine (A4)
├── audit/             AuditLogger, @Auditable aspect, Envers config
├── school/ student/ attendance/ academics/ fee/ hr/ timetable/ admissions/
├── library/ communication/ notification/ analytics/ transport/ hostel/ ...
└── platform/          SUPER_ADMIN cross-tenant ops (isolated path, no TenantInterceptor)
```

**Layered within each module** (the existing, good pattern — keep it):
`Controller (@PreAuthorize, DTO in/out) → Service (@Transactional, business rules, events) → Repository (Spring Data, tenant-scoped) → Entity (@Audited where mutable)`. DTOs are Java records; never expose entities. Validation via Bean Validation (`@Valid`) at controller + invariant checks in service. Events via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` (proven pattern) — but **side-effects that must not be lost go through the outbox**, not in-memory async.

**Security layer:** `SecurityConfig` (stateless, JWT filter), method security (`@EnableMethodSecurity`), custom permission expression, RLS at DB. **Event layer:** in-process events for decoupling; outbox for durability; future option to externalize to Kafka if a module needs it (no change to domain code).

## C2. Database Architecture

- **Multi-tenancy:** shared schema, `school_id` on every tenant table (`BaseEntity`), RLS enforced (A1). PK = UUID (`GenerationType.UUID`).
- **Indexing rule:** every tenant-scoped index **leads with `school_id`**; add the selective column next (`(school_id, student_id, date)`). This both satisfies RLS predicate and the query.
- **Partitioning:** range-partition the high-volume tables by time — `attendance_record` (monthly), `fee_payment`/`exam_mark` (by academic year), `audit_log`/Envers `_aud` (monthly). Enables cheap archival + partition pruning.
- **Constraints:** FKs within tenant; unique constraints always include `school_id` (e.g. `(school_id, receipt_number)`, `(school_id, admission_no)`); check constraints for enums/amounts ≥ 0; money as `BIGINT` paise (no floats — already the convention).
- **Audit tables:** append-only `audit_log` + Envers `_aud` + `revinfo`; `REVOKE UPDATE/DELETE` from app role.
- **Migrations:** Flyway, forward-only, one logical change per file; RLS policy migrations paired with each new tenant table (CI check enforces it).
- **Connections:** app connects as a **non-superuser** role (RLS applies); HikariCP sized per replica; read replicas for reports/exports via a `@Transactional(readOnly=true)` routing datasource.

## C3. API Design

- **REST conventions:** `/api/v1/tenants/{tenantId}/<resource>` (kebab/plural), nouns not verbs; sub-resources nested one level; actions as `POST .../{id}/<action>`.
- **Envelope:** the existing `ApiResponse<T>` (`success/data/error/meta`) — keep it everywhere.
- **Validation:** Bean Validation + a consistent `400 VALIDATION_ERROR` with field `details`.
- **Errors:** `ErrorCode` enum → HTTP status, via `GlobalExceptionHandler`; never leak stack traces (already done).
- **Pagination:** **cursor-based** for large/append-only lists (attendance, audit, payments) using `meta.nextCursor`; offset paging only for small admin lists. Default `limit=25`, max `100`.
- **Filtering/sorting:** whitelisted query params (`?status=&from=&to=&sort=-createdAt`); reject unknown sort fields (avoid full scans).
- **Idempotency:** `Idempotency-Key` header on all money-moving POSTs (tenant-scoped — fixes #5).
- **Versioning:** URI `/v1`; additive changes only within a version.

## C4. Frontend Architecture (Mobile-first, responsive, role-aware)

- **Stack:** Next.js (existing `web/`), TypeScript, shared `Permission`/`roleGroups` mirror of backend catalog; `RequirePermission` component + `useHasPermission` hook (replacing `RequireRole`).
- **Mobile-first:** parents/teachers are phone-primary; PWA + offline cache for attendance/marks entry; responsive breakpoints; bottom-nav on mobile, sidebar on desktop.
- **Navigation:** role-based menu generated from `perms[]` (hide, don't disable — existing guidance kept).
- **Per-role home (dashboard · widgets · reports · quick actions):**

| Role | Dashboard focus | Key widgets | Quick actions |
|---|---|---|---|
| **Principal/Owner** | School pulse | Attendance %, fee collection MTD, at-risk students, pending approvals | Approve, broadcast circular, view audit |
| **Admin** | Operations | Onboarding gaps, unmarked sections, staff on leave, admissions pipeline | Add student/staff, allocate teacher |
| **Class Teacher** | My class | My section attendance, today's timetable, my marks tasks, class fee defaulters | Mark attendance, enter marks, message parents |
| **Subject Teacher** | My subjects | Marks-entry queue (assigned only), today's periods, homework to grade | Enter/verify marks, set homework |
| **Accountant** | Money | Today's collection, defaulters aging, pending fee/refund approvals, cash drawer | Collect fee, request discount, reconcile |
| **Librarian** | Library | Issued/overdue, holds queue, low-stock | Issue/return, send overdue reminder |
| **Receptionist** *(new)* | Front desk | Today's visitors, enquiries, pickup authorizations | Log visitor, create enquiry |
| **Parent** | My child | Attendance, fees due (pay now), report card, circulars, PTM | Pay fee, apply leave, book PTM |
| **Student** | My day | Timetable, homework, marks, attendance | View/submit homework |

---

# PART D — RUN IT IN PRODUCTION

## D1. Security Hardening
TLS everywhere; secrets in a manager (not env files) — the platform already encrypts provider creds; field-level encryption (AES-GCM) for sensitive PII (Aadhaar, medical) and vault docs at rest; signed URLs with short expiry for files (replace "unguessable UUID path" with signed access); HMAC + replay window on **all** webhooks (close the Razorpay gap); strict CORS; security headers; dependency scanning (OWASP). Rate limiting (D4).

## D2. Backup & Disaster Recovery
PITR via continuous WAL archiving (RPO ≤ 5 min); automated daily base backups + nightly restore-test into a scratch instance (a backup you haven't restored is a hope, not a backup); multi-AZ primary with sync standby; documented failover runbook (RTO ≤ 1 h); per-tenant logical export for DPDP portability/deletion.

## D3. Monitoring & Logging
Structured JSON logs with `request_id` + `school_id` (never log PII/secrets); Micrometer → Prometheus → Grafana; RED metrics per endpoint + per-tenant; alerting on error-rate, p95, queue/DLQ depth, outbox lag, failed-notification rate, subscription-scheduler health; distributed tracing (OpenTelemetry); audit log is separate from app logs.

## D4. Rate Limiting & Resilience
Redis token-bucket per (tenant, user, endpoint-class); stricter on auth (OTP/login) to stop brute force; bulkheads/timeouts on external providers (WhatsApp/gateway/LLM/OCR); circuit breakers (Resilience4j); graceful degradation (notification failure never blocks the business tx — outbox handles it).

## D5. Data Encryption
In transit TLS 1.2+; at rest disk encryption + column-level AES-GCM for defined PII; tenant-isolated file storage with signed URLs; key rotation policy; JWT signing key rotation via `kid`.

## D6. Testing Strategy (positive · negative · boundary · concurrency · security)

| Layer | What | Examples |
|---|---|---|
| **Unit** | Services, calculators, state machines | grade boundaries (32.9/33), fee FIFO, leave balance math, approval threshold routing |
| **Integration** | Repo + DB + RLS + tx | RLS blocks cross-tenant; Envers writes diffs; outbox SKIP LOCKED; idempotent payment |
| **API** | Controller + security + validation | envelope shape, pagination cursors, validation errors, idempotency-key dedupe |
| **RBAC** | Data-driven matrix | generated test per (endpoint × role) from `RBAC_MATRIX.md`; PII masking for VIEWER |
| **E2E** | Critical journeys | admission→enroll→fee→attendance→exam→result→report card; withdrawal w/ dues block |
| **Security** | Abuse cases | cross-tenant IDOR (must 404), token reuse/replay, webhook signature/replay, brute-force throttling |
| **Concurrency** | Races | double fee submit, concurrent approval, receipt-number gap-free under load, refresh-token race |
| **Performance** | Scale | 22M-row attendance partition pruning; report export streaming; 5k RPS attendance window soak |

**Gates:** PR requires green unit+integration+RBAC matrix; ArchUnit (no unscoped finders, money entities `@Audited`); coverage floor on services; security suite in CI; load test before each release.

## D7. Deployment Architecture
Docker images, rolling deploys behind a load balancer (≥2 stateless app replicas across AZs — now safe because outbox uses SKIP LOCKED and sessions/rate-limit live in Redis); managed PostgreSQL (multi-AZ + read replica) and Redis; Flyway runs on startup (single-flight/leader); blue-green or canary for risky releases; health/readiness probes gate traffic; per-environment config via the existing `application.yml` + env overrides; CDN for static/branding assets.

## D8. SaaS Multi-Tenant Strategy (summary)
Shared-schema + RLS + `@TenantId` (A1) — chosen over schema-per-tenant for 1,000+ tenants (migration sanity, connection economy, pooling). Per-tenant: subscription/plan limits (`UsageService`), feature flags, provider config, branding, settings. Tenant lifecycle: trial→active→past_due→grace→suspended→cancelled with read-preserving suspension and DPDP-compliant deletion/export. Noisy-neighbor protection via per-tenant rate limits + usage metering.

---

## D9. Go-Live Readiness Checklist

**Security (blocker)**
- [ ] RLS enabled + `FORCE` on every tenant table; app connects as non-superuser
- [ ] Cross-tenant IDOR suite green (every by-id/by-student endpoint 404s for foreign tenant)
- [ ] `@TenantId` + tenant resolver active; ArchUnit bans unscoped finders
- [ ] Permission-based RBAC live; VIEWER PII masking verified; Receptionist role seeded
- [ ] No write endpoint without `@PreAuthorize` (homework submission fixed)
- [ ] MFA enforced for OWNER/PRINCIPAL/ADMIN/ACCOUNTANT; refresh rotation + reuse detection
- [ ] All webhooks HMAC + replay-protected

**Financial integrity (blocker)**
- [ ] Discounts/refunds/expenses approval-gated (`approvedById` populated) + audited
- [ ] Cash variance escalation; receipt numbers gap-free under concurrency
- [ ] Webhook→payment durable (inbox + retry + DLQ); cash idempotency key

**Academic integrity**
- [ ] Result verify→approve→publish gate; reopen versions report cards + audits
- [ ] Marks/attendance Envers-audited; payroll consumes only approved attendance

**Data & audit**
- [ ] Envers on all mutable financial/academic entities; `audit_log` insert-only
- [ ] High-volume tables partitioned; tenant-leading composite indexes

**Reliability**
- [ ] Outbox uses `FOR UPDATE SKIP LOCKED`; verified no double-send with 2 replicas
- [ ] PITR + nightly restore test passing; multi-AZ failover runbook rehearsed
- [ ] Monitoring/alerting on error rate, p95, DLQ, outbox lag, subscription schedulers
- [ ] Rate limiting on auth + per-tenant; circuit breakers on external providers

**Operations & compliance**
- [ ] Student withdrawal blocks on outstanding dues; TC gated on clearance
- [ ] Admission capacity + duplicate detection live
- [ ] Notification opt-out + quiet hours; failure visibility to school
- [ ] DPDP: data export + deletion per tenant; PII encryption at rest
- [ ] Statutory exports (UDISE+, GST, collection register, board result format) available
- [ ] Load test at target scale passed; rollback plan documented

---

*End of blueprint. Companion: [`RBAC_MATRIX.md`](RBAC_MATRIX.md).*
