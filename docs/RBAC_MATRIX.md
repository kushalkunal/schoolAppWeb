# RBAC Architecture & Permission Matrix

> Companion to [`PRODUCTION_GRADE_REDESIGN.md`](PRODUCTION_GRADE_REDESIGN.md) §A2.
> **Model:** permission-based. Roles are named bundles of permissions (rows in `role_permission`, seeded per tenant, customizable). The JWT carries the *effective permission set* (`perms[]`), and `@PreAuthorize("hasPermission('X')")` is the only authorization primitive in controllers. Object-scope (own-section / own-subject) is a service-layer guard, **not** a permission.
> This file is also the **fixture for the data-driven RBAC test suite** — keep it in sync with controllers.

---

## 1. Authorization layers (order of evaluation)

```
1. Tenant boundary      RLS + TenantInterceptor   → 404/403 if {tenantId} ≠ JWT tenant
2. Authentication       JwtAuthFilter             → 401 if no/invalid token
3. Permission           @PreAuthorize hasPermission('X')  → 403 if perm absent
4. Object ownership      TenantGuard / scope check → 404 if row not in tenant; 403 if not own section/subject
5. Approval gate         maker-checker engine      → some writes create a request, not a mutation
```

---

## 2. Permission catalog (`auth/Permission.java`)

Naming: `RESOURCE_VERB`. `_PII` gates sensitive fields. `_REQUEST` vs `_APPROVE` enforces maker-checker.

| Domain | Permissions |
|---|---|
| **Tenant** | `TENANT_READ`, `TENANT_MANAGE`, `BRANDING_MANAGE`, `SETTINGS_MANAGE`, `RBAC_MANAGE` |
| **Platform** *(SUPER_ADMIN)* | `PLATFORM_TENANT_MANAGE`, `PLATFORM_PLAN_MANAGE`, `PLATFORM_SECRET_MANAGE` |
| **Auth/Session** | `SESSION_MANAGE_OWN`, `SESSION_REVOKE_ANY`, `MFA_MANAGE_OWN` |
| **Staff** | `STAFF_READ`, `STAFF_READ_PII`, `STAFF_WRITE`, `STAFF_DELETE`, `STAFF_LIFECYCLE` |
| **Allocation** | `ALLOCATION_MANAGE`, `ALLOCATION_READ` |
| **Student** | `STUDENT_READ`, `STUDENT_READ_PII`, `STUDENT_WRITE`, `STUDENT_DELETE`, `ADMISSION_MANAGE`, `STUDENT_WITHDRAW_APPROVE` |
| **Attendance** | `ATTENDANCE_READ`, `ATTENDANCE_WRITE`, `ATTENDANCE_CORRECT`, `ATTENDANCE_APPROVE`, `STAFF_ATTENDANCE_SELF` |
| **Leave** | `LEAVE_REQUEST`, `LEAVE_APPROVE_L1`, `LEAVE_APPROVE_L2`, `LEAVE_REPORT_READ` |
| **Timetable** | `TIMETABLE_READ`, `TIMETABLE_MANAGE` |
| **Exam/Result** | `EXAM_MANAGE`, `MARKS_READ`, `MARKS_WRITE`, `MARKS_VERIFY`, `RESULT_PUBLISH`, `RESULT_REOPEN` |
| **Fee** | `FEE_READ`, `FEE_COLLECT`, `FEE_INVOICE_WRITE`, `FEE_DISCOUNT_REQUEST`, `FEE_DISCOUNT_APPROVE`, `FEE_REFUND_REQUEST`, `FEE_REFUND_APPROVE`, `FEE_CONFIG` |
| **Expense/Cash** | `EXPENSE_RECORD`, `EXPENSE_APPROVE`, `CASH_RECONCILE`, `CASH_VARIANCE_APPROVE` |
| **Library** | `LIBRARY_READ`, `LIBRARY_ISSUE`, `LIBRARY_MANAGE` |
| **Comms** | `COMMS_SEND`, `COMMS_CONFIG`, `INBOX_READ` |
| **Approvals** | `APPROVAL_READ`, `APPROVAL_DECIDE` |
| **Ops modules** | `VISITOR_MANAGE`, `INCIDENT_READ`, `INCIDENT_WRITE`, `TRANSPORT_MANAGE`, `HOSTEL_MANAGE`, `INFIRMARY_MANAGE`, `PTM_MANAGE`, `VAULT_READ`, `VAULT_WRITE` |
| **Audit/Export** | `AUDIT_READ`, `EXPORT_DATA` |
| **Self-service (parent/student)** | `SELF_CHILD_READ`, `SELF_FEE_PAY`, `SELF_LEAVE_REQUEST`, `SELF_HOMEWORK`, `SELF_PREFERENCES` |

---

## 3. Roles (default bundles)

`SUPER_ADMIN` is platform-only and never appears in tenant tokens. New roles (`RECEPTIONIST`, `COUNSELOR`) resolve audit gap #15.

| Role | Intent |
|---|---|
| `SCHOOL_OWNER` | Everything in the tenant incl. pricing-adjacent + delete |
| `PRINCIPAL` | Everything operational; final academic/financial approver |
| `ADMIN` | Operations; **no** school deletion, **no** highest-tier financial approval |
| `CLASS_TEACHER` | Own section (write), school (read), marks for taught subjects |
| `SUBJECT_TEACHER` | Marks for assigned subject/section only |
| `ACCOUNTANT` | Fee/expense **request + collect**, reconcile — **not** high-value approve |
| `LIBRARIAN` | Library module |
| `RECEPTIONIST` *(new)* | Front desk: visitors, enquiries, non-PII student read |
| `COUNSELOR` *(new, optional)* | Incidents + student read |
| `VIEWER` | Read-only, **no `_PII`**, no writes |
| `PARENT` *(portal)* | Own child only |
| `STUDENT` *(portal)* | Own data only |

---

## 4. Role → Permission matrix

✅ granted · ⬚ not granted · 🔶 granted but creates an **approval request** (maker) · 🔒 **approver** of that request · *scope* = object-level guard applies.

| Permission | OWNER | PRINCIPAL | ADMIN | CLASS_T | SUBJECT_T | ACCT | LIBRN | RECEP | VIEWER | PARENT | STUDENT |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| TENANT_MANAGE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| RBAC_MANAGE | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| STAFF_WRITE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| STAFF_DELETE | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| ALLOCATION_MANAGE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| STUDENT_READ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⬚ | ✅ | ✅ | ⬚ | ⬚ |
| STUDENT_READ_PII | ✅ | ✅ | ✅ | ✅*scope* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| STUDENT_WRITE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| ADMISSION_MANAGE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | 🔶 enquiry | ⬚ | ⬚ | ⬚ |
| STUDENT_WITHDRAW_APPROVE | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| ATTENDANCE_WRITE | ✅ | ✅ | ✅ | ✅*scope* | ✅*period scope* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| ATTENDANCE_CORRECT | ✅ | ✅ | ✅ | 🔶*scope* | 🔶*scope* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| ATTENDANCE_APPROVE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| MARKS_WRITE | ✅ | ✅ | ✅ | ✅*scope* | ✅*scope* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| MARKS_VERIFY | ✅ | ✅ | ✅ | ✅*not own entry* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| RESULT_PUBLISH | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| RESULT_REOPEN | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| FEE_COLLECT | ✅ | ✅ | ✅ | ⬚ | ⬚ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| FEE_DISCOUNT_REQUEST | ✅ | ✅ | ✅ | ⬚ | ⬚ | 🔶 | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| FEE_DISCOUNT_APPROVE | ✅ | 🔒 | 🔒*low tier* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| FEE_REFUND_APPROVE | ✅ | 🔒 | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| EXPENSE_RECORD | ✅ | ✅ | ✅ | ⬚ | ⬚ | 🔶 | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| EXPENSE_APPROVE | ✅ | 🔒 | 🔒*low tier* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| CASH_RECONCILE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| CASH_VARIANCE_APPROVE | ✅ | 🔒 | 🔒 | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| LEAVE_REQUEST | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ |
| LEAVE_APPROVE_L1 | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| LEAVE_APPROVE_L2 | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| TIMETABLE_MANAGE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| LIBRARY_ISSUE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ |
| VISITOR_MANAGE | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ✅ | ⬚ | ⬚ | ⬚ |
| INCIDENT_WRITE | ✅ | ✅ | ✅ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| COMMS_SEND | ✅ | ✅ | ✅ | ✅*own class* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| APPROVAL_DECIDE | ✅ | ✅ | ✅*low tier* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| AUDIT_READ | ✅ | ✅ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| EXPORT_DATA | ✅ | ✅ | ✅ | ⬚ | ⬚ | ✅*fee only* | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ |
| SELF_FEE_PAY | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ✅ | ⬚ |
| SELF_CHILD_READ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ⬚ | ✅*own child* | ✅*self* |

---

## 5. Object-scope guards (service layer — NOT permissions)

| Guard | Where | Rule |
|---|---|---|
| Own-section attendance | `AttendanceService` | `staffId == section.classTeacherId` (or period assignment) |
| Own-subject marks | `MarksService` | assignment row `(staff, subject, section, year)` exists |
| Not-own-entry verify | `ResultWorkflowService` | verifier ≠ entering teacher |
| Self leave only | `LeaveService` | non-admin can only file for self |
| No self-approve | `approval` engine | approver ≠ requester at every level |
| Parent→own child | portal services | `StudentParentLink` ownership |

---

## 6. Test fixture contract

The data-driven RBAC suite reads this file's §4 and asserts, per `(endpoint, role)`:
- ✅ → `2xx`
- ⬚ → `403 FORBIDDEN`
- 🔶 → `2xx` **and** an `ApprovalRequest` row created (no direct domain mutation)
- 🔒 → can decide a pending request of that type; cannot if requester==self
- *scope* → in-scope `2xx`, out-of-scope `403`/`404`

Plus PII tests: roles without `_READ_PII` receive `PhoneNormalizer.mask()`-ed values.
