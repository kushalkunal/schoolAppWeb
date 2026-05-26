# 02 — API Reference

Complete catalogue of every HTTP endpoint exposed by the backend. Derived from a scan of every `*Controller.java` file under [backend/src/main/java/in/schoolapp/](../../backend/src/main/java/in/schoolapp/) on 2026-04-23.

- All paths are relative to the server base URL (see `VITE_API_BASE_URL` in [00-stack-and-conventions.md](00-stack-and-conventions.md)).
- Tenant-scoped paths begin with `/api/v1/tenants/{tenantId}/…`; the `{tenantId}` path param MUST match the `tenantId` claim in the JWT (enforced by `TenantInterceptor`).
- All responses use the envelope shape `{ success, data, error, meta }` (see `ApiResponse<T>` in [08-typescript-dto-reference.md](08-typescript-dto-reference.md)). The shared axios client unwraps `data` before your component sees it.
- All mutating endpoints are guarded by `@PreAuthorize(...)`. Who can call what is summarised in [05-role-matrix.md](05-role-matrix.md).
- Error codes are documented in [03-error-handling.md](03-error-handling.md); a short list of the ones each endpoint throws is noted inline.
- Curl examples use placeholders — substitute `$TOKEN`, `$TENANT`, `$STUDENT`, etc. before running.

---

## Table of contents

- [Public (no JWT)](#public-no-jwt)
- [Auth](#auth)
- [School, classes, staff, onboarding](#school-classes-staff-onboarding)
- [Substitute teachers](#substitute-teachers)
- [Students (incl. profile, timeline, photo, documents)](#students)
- [Attendance (incl. analytics)](#attendance)
- [Fees (payments, invoices, dashboard, reminders)](#fees)
- [Fee heads](#fee-heads)
- [Fee reminder schedules](#fee-reminder-schedules)
- [Academics (subjects, exams, marks, report cards, eligibility)](#academics)
- [Teacher assignments](#teacher-assignments)
- [Analytics — dashboard + alerts](#analytics)
- [Communication — inbox](#communication-inbox)
- [Communication — circulars](#communication-circulars)
- [Payment webhooks](#payment-webhooks)
- [WhatsApp webhook](#whatsapp-webhook)
- [Migration (OCR + LLM paper-register import)](#migration)
- [Storage / file serving](#storage--file-serving)
- [Mobile sync (teacher app)](#mobile-sync)
- [Exports](#exports)
- [Audit log](#audit-log)
- [DPDP data-deletion requests](#data-deletion-requests)

---

## Public (no JWT)

Endpoints in this table are reachable without any bearer token. They are allowlisted in [SecurityConfig](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Liveness probe |
| `GET` | `/actuator/info` | Build info |
| `GET` | `/actuator/prometheus` | Prometheus scrape |
| `GET` | `/v3/api-docs` | OpenAPI spec (JSON) |
| `GET` | `/swagger-ui/index.html`, `/swagger-ui/**` | Swagger UI |
| `GET` | `/api/v1/ping` | Smoke test |
| `POST` | `/api/v1/auth/otp/send` | Send OTP |
| `POST` | `/api/v1/auth/otp/verify` | Verify OTP (returns JWT pair) |
| `POST` | `/api/v1/auth/token/refresh` | Refresh access token |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token |
| `POST` | `/api/v1/tenants` | Public tenant signup |
| `POST` | `/webhooks/whatsapp` | WhatsApp BSP webhook (HMAC verified) |
| `POST` | `/webhooks/stripe` | Stripe webhook (HMAC verified) |
| `POST` | `/webhooks/razorpay` | Razorpay webhook (HMAC verified) |
| `GET` | `/files/**` | Locally-stored files (only when `app.storage.provider=LOCAL`) |

### `GET /api/v1/ping`

Liveness smoke-test. Source: [PingController](../../backend/src/main/java/in/schoolapp/common/PingController.java).

**Auth**: none.

**Response 200**:
```json
{
  "success": true,
  "data": { "service": "school-management", "status": "ok", "timestamp": "2026-04-23T10:15:30Z" }
}
```

```bash
curl http://localhost:8080/api/v1/ping
```

---

## Auth

Source: [AuthController](../../backend/src/main/java/in/schoolapp/auth/AuthController.java). Prefix: `/api/v1/auth`. See [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md) for the full lifecycle.

### `POST /api/v1/auth/otp/send`

Dispatch an OTP to the caller's phone (WhatsApp/SMS) or email. Exactly one of `phone` / `email` must be present.

**Auth**: public.

**Request body** (`SendOtpRequest` — see 08):
```json
{ "phone": "9876543210" }
```
…or:
```json
{ "email": "principal@example.com" }
```

**Response 200**:
```json
{ "success": true, "data": { "message": "OTP sent" } }
```

**Errors**: `VALIDATION_ERROR`, `PHONE_NOT_FOUND`, `OTP_RATE_LIMITED`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210"}'
```

### `POST /api/v1/auth/otp/verify`

Exchange a valid OTP for an access + refresh token pair.

**Auth**: public.

**Request body** (`VerifyOtpRequest`):
```json
{ "phone": "9876543210", "otp": "123456" }
```

**Response 200** (`AuthResponse`):
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "rt_7f3a...",
    "expiresInSeconds": 900,
    "user": { /* StaffResponse */ }
  }
}
```

**Errors**: `OTP_INVALID`, `OTP_EXPIRED`, `PHONE_NOT_FOUND`, `VALIDATION_ERROR`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210","otp":"123456"}'
```

### `POST /api/v1/auth/token/refresh`

Rotate the refresh token and mint a fresh access token. Old refresh token is invalidated.

**Auth**: public (the refresh token in the body is the credential).

**Request body** (`RefreshTokenRequest`):
```json
{ "refreshToken": "rt_7f3a..." }
```

**Response 200**: same `AuthResponse` shape as above.

**Errors**: `TOKEN_INVALID`, `TOKEN_EXPIRED`.

### `POST /api/v1/auth/logout`

Revoke the supplied refresh token (idempotent — missing/expired tokens silently succeed).

**Auth**: public.

**Request body** (optional): `RefreshTokenRequest`.

**Response 200**:
```json
{ "success": true, "data": { "message": "Logged out" } }
```

---

## School, classes, staff, onboarding

Source: [SchoolController](../../backend/src/main/java/in/schoolapp/school/SchoolController.java). Prefix: `/api/v1/tenants`.

### `POST /api/v1/tenants`

Public tenant signup — creates the school, principal staff record, and initial academic year in one transaction.

**Auth**: public.

**Request body** (`CreateSchoolRequest`):
```json
{
  "schoolName": "Sunrise Public School",
  "principalName": "Mrs. R. Mehta",
  "phone": "9876543210",
  "email": "principal@sunrise.edu.in",
  "state": "Maharashtra",
  "city": "Pune",
  "board": "CBSE"
}
```
Per the service layer, at least one of `phone`/`email` is required depending on `app.signup.channel` (`PHONE` | `EMAIL` | `BOTH`).

**Response 201** (`SchoolSignupResponse`):
```json
{
  "success": true,
  "data": {
    "school": { /* SchoolResponse */ },
    "principal": { /* StaffResponse */ },
    "academicYear": { /* AcademicYearResponse */ },
    "nextStep": "SEND_OTP_PHONE"
  }
}
```

**Errors**: `VALIDATION_ERROR`.

### `GET /api/v1/tenants/{tenantId}`

Returns the tenant's school profile.

**Auth**: any authenticated user (tenant must match JWT claim).

**Response 200**: `SchoolResponse` (see 08).

**Errors**: `SCHOOL_NOT_FOUND`.

### `PUT /api/v1/tenants/{tenantId}`

Update mutable school fields. Identifiers (phone, email, board) are NOT updatable here.

**Auth**: `SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN` (`OWNER_OR_ADMIN`).

**Request body** (`UpdateSchoolRequest`): all fields optional; null = unchanged.

**Response 200**: `SchoolResponse`.

### `POST /api/v1/tenants/{tenantId}/logo` `multipart/form-data`

Upload the school logo (stored in the configured `FileStorageService`).

**Auth**: `OWNER_OR_ADMIN`.

**Request**: `multipart/form-data` with field `file`.

**Response 200**: `SchoolResponse` (with updated logo URL in subsequent GETs).

**Errors**: `VALIDATION_ERROR`, `INVALID_FILE_TYPE`, `FILE_TOO_LARGE`.

```bash
curl -X POST "$BASE/api/v1/tenants/$TENANT/logo" \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@./logo.png
```

### `GET /api/v1/tenants/{tenantId}/onboarding-status`

Drives the onboarding wizard — percentage complete + per-step completion flags.

**Auth**: any authenticated user.

**Response 200**: `OnboardingStatusResponse` (see 08).

### `POST /api/v1/tenants/{tenantId}/classes/bulk`

Create many classes and sections in one transaction (onboarding step 2).

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateClassesRequest`):
```json
{
  "classes": [
    { "name": "Class 1", "sections": ["A","B"], "sortOrder": 1 },
    { "name": "Class 2", "sections": ["A"], "sortOrder": 2 }
  ]
}
```

**Response 201**: `ClassResponse[]` (each with its nested sections).

**Errors**: `VALIDATION_ERROR`.

### `GET /api/v1/tenants/{tenantId}/classes`

List all classes + sections for the tenant.

**Auth**: any authenticated user.

**Response 200**: `ClassResponse[]`.

### `POST /api/v1/tenants/{tenantId}/staff`

Add a staff member (teacher, accountant, admin, principal, etc.). An OTP account is provisioned automatically.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateStaffRequest`):
```json
{ "firstName": "Asha", "lastName": "Kulkarni", "phone": "9998887770", "role": "CLASS_TEACHER" }
```

**Response 201**: `StaffResponse`.

**Errors**: `VALIDATION_ERROR`, `INVALID_PHONE`.

### `GET /api/v1/tenants/{tenantId}/staff`

List all staff (active + inactive).

**Auth**: any authenticated user.

**Response 200**: `StaffResponse[]`.

### `DELETE /api/v1/tenants/{tenantId}/staff/{staffId}`

Deactivate a staff member (soft delete). Requires a higher role than `OWNER_OR_ADMIN`.

**Auth**: `SCHOOL_OWNER`, `PRINCIPAL` (ADMIN NOT allowed).

**Response 200**: `{ "success": true }` (no data).

**Errors**: `RESOURCE_NOT_FOUND`.

---

## Substitute teachers

Source: [SubstituteTeacherController](../../backend/src/main/java/in/schoolapp/school/SubstituteTeacherController.java). Prefix: `/api/v1/tenants/{tenantId}/substitutes`.

### `POST /api/v1/tenants/{tenantId}/substitutes`

Assign a substitute teacher for an absent teacher, on a specific section and date.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateSubstituteRequest`):
```json
{
  "absentTeacherId": "…",
  "substituteId": "…",
  "sectionId": "…",
  "assignedDate": "2026-04-23",
  "note": "Sick leave"
}
```

**Response 201**: `SubstituteResponse`.

**Errors**: `VALIDATION_ERROR`, `SECTION_NOT_FOUND`.

### `GET /api/v1/tenants/{tenantId}/substitutes?date=YYYY-MM-DD`

List substitute assignments for a date (defaults to today).

**Auth**: `ANY_TEACHER` (all roles except `VIEWER`/`ACCOUNTANT`).

**Query**: `date` — `LocalDate`, optional, defaults to today.

**Response 200**: `SubstituteResponse[]`.

### `DELETE /api/v1/tenants/{tenantId}/substitutes/{assignmentId}`

Cancel a substitute assignment.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `{ "success": true }`.

---

## Students

Source: [StudentController](../../backend/src/main/java/in/schoolapp/student/StudentController.java). Prefix: `/api/v1/tenants/{tenantId}/students`.

### `POST /api/v1/tenants/{tenantId}/students`

Create a student with minimum-viable fields. Sibling detection: if a parent with the same `parentPhone` exists in the tenant, the student is linked to that parent.

**Auth**: `OWNER_OR_ADMIN`.

**Request body**: `CreateStudentRequest` (see 08).

**Response 201**: `StudentResponse`.

**Errors**: `VALIDATION_ERROR`, `SECTION_NOT_FOUND`, `DUPLICATE_ADMISSION_NUMBER`, `INVALID_PHONE`.

```bash
curl -X POST "$BASE/api/v1/tenants/$TENANT/students" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Aarav","sectionId":"…","parentPhone":"9998887770","parentName":"Mr. Sharma","parentRelation":"FATHER"}'
```

### `GET /api/v1/tenants/{tenantId}/students`

Paginated roster with optional full-text search.

**Auth**: any authenticated user.

**Query**:
| Name | Type | Required | Default |
|---|---|---|---|
| `search` | string | no | — |
| `page` | int | no | `0` |
| `size` | int | no | `50` |

**Response 200**: `StudentResponse[]` with `meta: { total, page, limit }`.

### `GET /api/v1/tenants/{tenantId}/students/{studentId}`

Full profile: base student data, current enrollment, parents, siblings.

**Auth**: any authenticated user.

**Response 200**: `StudentProfileResponse`.

**Errors**: `STUDENT_NOT_FOUND`.

### `PUT /api/v1/tenants/{tenantId}/students/{studentId}`

Patch-style update — only non-null fields are applied.

**Auth**: `OWNER_OR_ADMIN`.

**Request body**: `UpdateStudentRequest`.

**Response 200**: `StudentResponse`.

**Errors**: `STUDENT_NOT_FOUND`, `VALIDATION_ERROR`.

### `DELETE /api/v1/tenants/{tenantId}/students/{studentId}`

Soft-deactivate a student.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `{ "success": true }`.

### `GET /api/v1/tenants/{tenantId}/students/{studentId}/timeline`

Cross-year record: enrollments, report cards, attendance year-rollup.

**Auth**: any authenticated user.

**Response 200**: `StudentTimelineResponse`.

**Errors**: `STUDENT_NOT_FOUND`.

### `POST /api/v1/tenants/{tenantId}/students/{studentId}/photo` `multipart/form-data`

Upload student photo.

**Auth**: `OWNER_OR_ADMIN`.

**Request**: `multipart/form-data` with `file`.

**Response 200**: `StudentResponse` (now carries `photoUrl`).

**Errors**: `VALIDATION_ERROR`, `INVALID_FILE_TYPE`, `FILE_TOO_LARGE`.

### `POST /api/v1/tenants/{tenantId}/students/{studentId}/documents` `multipart/form-data`

Upload a student document (admission form, birth certificate, TC, medical, etc.).

**Auth**: `OWNER_OR_ADMIN`.

**Form fields**:
- `type` — `StudentDocumentType` (query/form param, required)
- `file` — required

**Response 201**: `StudentDocumentResponse`.

### `GET /api/v1/tenants/{tenantId}/students/{studentId}/documents`

List documents attached to a student.

**Auth**: any authenticated user.

**Response 200**: `StudentDocumentResponse[]`.

### `DELETE /api/v1/tenants/{tenantId}/students/documents/{documentId}`

Delete a document (note: tenant-scoped path but `studentId` not in the URL — the service validates tenant ownership of the document).

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `{ "success": true }`.

**Errors**: `RESOURCE_NOT_FOUND`.

---

## Attendance

Source: [AttendanceController](../../backend/src/main/java/in/schoolapp/attendance/AttendanceController.java). Prefix: `/api/v1/tenants/{tenantId}`.

### `POST /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance`

Submit a day's attendance for a section. Follows "reverse marking" — only non-PRESENT entries need listing; omitted students are implicitly PRESENT.

**Auth**: `ATTENDANCE_WRITER` (`SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `CLASS_TEACHER`).

**Request body** (`SubmitAttendanceRequest`):
```json
{
  "date": "2026-04-23",
  "entries": [
    { "studentId": "…", "status": "ABSENT", "note": "flu" },
    { "studentId": "…", "status": "LATE", "arrivalTime": "2026-04-23T09:15:00Z" }
  ]
}
```

**Response 201**: `AttendanceSubmitResponse` (totals + records + notifications queued).

**Errors**: `ATTENDANCE_ALREADY_SUBMITTED`, `VALIDATION_ERROR`, `SECTION_NOT_ASSIGNED`.

### `GET /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance?date=YYYY-MM-DD`

Read back a section's attendance for a date.

**Auth**: any authenticated user.

**Query**: `date` — required.

**Response 200**: `AttendanceRecordResponse[]`.

### `GET /api/v1/tenants/{tenantId}/students/{studentId}/attendance?from=…&to=…`

Per-student attendance history over a date range.

**Auth**: any authenticated user.

**Query**: `from`, `to` — required `LocalDate`s.

**Response 200**: `AttendanceRecordResponse[]`.

### `GET /api/v1/tenants/{tenantId}/attendance/summary?date=YYYY-MM-DD`

School-wide counts for a single day — drives dashboard tiles + daily digest WA.

**Auth**: any authenticated user.

**Query**: `date` — optional, defaults to today.

**Response 200**: `AttendanceSummaryResponse`.

### `GET /api/v1/tenants/{tenantId}/attendance/unmarked?date=YYYY-MM-DD`

Sections that haven't submitted attendance yet for the given date.

**Auth**: any authenticated user.

**Query**: `date` — optional, defaults to today.

**Response 200**: `UnmarkedSectionResponse[]`.

### `GET /api/v1/tenants/{tenantId}/attendance/chronic?windowDays=30&minAbsences=5`

Chronic-absentee list for principal review.

**Auth**: any authenticated user.

**Query**: `windowDays` (default 30), `minAbsences` (default 5) — both int.

**Response 200**: `ChronicAbsenteeResponse[]`.

---

## Fees

Source: [FeeController](../../backend/src/main/java/in/schoolapp/fee/FeeController.java). Prefix: `/api/v1/tenants/{tenantId}`.

### `POST /api/v1/tenants/{tenantId}/fees/payments`

Zero-config "quick collect" — accountant types a student, amount, mode and the receipt is generated. Partial payments supported; `invoiceId` optional (FIFO applied to pending invoices otherwise).

**Auth**: `FEE_WRITER` (`SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `ACCOUNTANT`).

**Request body** (`QuickCollectRequest`):
```json
{
  "studentId": "…",
  "amountPaise": 50000,
  "paymentMode": "CASH",
  "paymentDate": "2026-04-23",
  "notes": "April installment"
}
```

**Response 201**: `PaymentResponse` (includes receipt number, PDF URL, outstanding balance).

**Errors**: `STUDENT_NOT_FOUND`, `INVOICE_NOT_FOUND`, `PAYMENT_AMOUNT_EXCEEDS_DUE`, `VALIDATION_ERROR`.

```bash
curl -X POST "$BASE/api/v1/tenants/$TENANT/fees/payments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"studentId":"'$STU'","amountPaise":50000,"paymentMode":"CASH"}'
```

### `GET /api/v1/tenants/{tenantId}/fees/payments/{paymentId}`

Fetch a payment by id (principal/admin drill-down or parent receipt re-render).

**Auth**: any authenticated user.

**Response 200**: `PaymentResponse`.

**Errors**: `RECEIPT_NOT_FOUND`.

### `POST /api/v1/tenants/{tenantId}/fees/invoices`

Create a single invoice for a student.

**Auth**: `FEE_WRITER`.

**Request body** (`CreateInvoiceRequest`):
```json
{ "studentId": "…", "feeHeadId": "…", "amountDuePaise": 1500000, "dueDate": "2026-05-15", "description": "Term 1 Tuition" }
```

**Response 201**: `InvoiceResponse`.

**Errors**: `STUDENT_NOT_FOUND`, `VALIDATION_ERROR`.

### `POST /api/v1/tenants/{tenantId}/fees/opening-balances`

Bulk record opening balances from paper registers (gap §7). Single transaction.

**Auth**: `FEE_WRITER`.

**Request body** (`OpeningBalanceRequest`):
```json
{ "balances": [{ "studentId": "…", "amountPaise": 250000, "note": "Prev year carryover" }] }
```

**Response 201**: `InvoiceResponse[]`.

### `GET /api/v1/tenants/{tenantId}/students/{studentId}/fee-summary`

All invoices + recent payments + total outstanding for one student.

**Auth**: any authenticated user.

**Response 200**: `StudentFeeSummaryResponse`.

**Errors**: `STUDENT_NOT_FOUND`.

### `GET /api/v1/tenants/{tenantId}/fees/dashboard`

KPI tile bundle — today's collection, MTD, total outstanding/overdue, defaulter count, payment count today.

**Auth**: any authenticated user.

**Response 200**: `FeeDashboardResponse`.

### `GET /api/v1/tenants/{tenantId}/fees/defaulters?page=0&size=50`

Paginated defaulter list (oldest-due first).

**Auth**: any authenticated user.

**Query**: `page` (default 0), `size` (default 50).

**Response 200**: `DefaulterResponse[]`.

### `POST /api/v1/tenants/{tenantId}/fees/reminders`

Queue reminder WhatsApps for a list of students (optionally with an override message).

**Auth**: `FEE_WRITER`.

**Request body** (`BulkReminderRequest`):
```json
{ "studentIds": ["…","…"], "messageOverride": "Reminder: please pay by Friday." }
```

**Response 200**:
```json
{ "success": true, "data": { "queued": 12 } }
```

**Errors**: `VALIDATION_ERROR`.

---

## Fee heads

Source: [FeeHeadController](../../backend/src/main/java/in/schoolapp/fee/FeeHeadController.java). Prefix: `/api/v1/tenants/{tenantId}/fee-heads`.

### `POST …/fee-heads`

Create a fee head (tuition, transport, lab, etc.).

**Auth**: `FEE_WRITER`.

**Request body** (`CreateFeeHeadRequest`): `{ "name": "Tuition" }`.

**Response 201**: `FeeHeadResponse`.

**Errors**: `VALIDATION_ERROR`.

### `GET …/fee-heads`

List fee heads.

**Auth**: any authenticated user.

**Response 200**: `FeeHeadResponse[]`.

### `PUT …/fee-heads/{id}`

Rename a fee head (request body is `CreateFeeHeadRequest`).

**Auth**: `FEE_WRITER`.

**Response 200**: `FeeHeadResponse`.

**Errors**: `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`.

### `DELETE …/fee-heads/{id}`

Deactivate a fee head (soft).

**Auth**: `FEE_WRITER`.

**Response 200**: `{ "success": true }`.

---

## Fee reminder schedules

Source: [FeeReminderScheduleController](../../backend/src/main/java/in/schoolapp/fee/FeeReminderScheduleController.java). Prefix: `/api/v1/tenants/{tenantId}/fee-reminder-schedules`.

### `GET …/fee-reminder-schedules`

List schedule ladder entries (e.g. "5 days before due", "3 days after").

**Auth**: any authenticated user.

**Response 200**: `FeeReminderScheduleDto[]`.

### `POST …/fee-reminder-schedules`

Create a schedule entry.

**Auth**: `FEE_WRITER`.

**Request body** (`FeeReminderScheduleDto`): `name`, `triggerType` (`BEFORE_DUE`|`ON_DUE`|`AFTER_DUE`), `daysOffset`, `includeUpiLink`, `active`.

**Response 201**: `FeeReminderScheduleDto`.

### `PUT …/fee-reminder-schedules/{id}`

Update a schedule entry.

**Auth**: `FEE_WRITER`.

**Request body**: `FeeReminderScheduleDto`.

**Response 200**: `FeeReminderScheduleDto`.

**Errors**: `RESOURCE_NOT_FOUND`.

### `DELETE …/fee-reminder-schedules/{id}`

Remove a schedule entry.

**Auth**: `FEE_WRITER`.

**Response 200**: `{ "success": true }`.

### `POST …/fee-reminder-schedules/run-now`

Trigger the reminder scan immediately for this tenant (admin "run now" button / test tool).

**Auth**: `FEE_WRITER`.

**Response 200**:
```json
{ "success": true, "data": { "queued": 23 } }
```

---

## Academics

Source: [AcademicsController](../../backend/src/main/java/in/schoolapp/academics/AcademicsController.java). Prefix: `/api/v1/tenants/{tenantId}`.

### `POST …/subjects/bulk`

Bulk-create subjects at onboarding (idempotent — existing names skipped).

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateSubjectsRequest`):
```json
{ "subjects": [{ "name": "Mathematics", "code": "MATH" }] }
```

**Response 201**: `SubjectResponse[]`.

### `GET …/subjects`

List all subjects.

**Auth**: any authenticated user.

**Response 200**: `SubjectResponse[]`.

### `POST …/exams`

Create an exam (unit test, term exam, etc.) for the current academic year.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateExamRequest`): `name`, `examType` (`UNIT_TEST`|`TERM`|…), `startDate`, `endDate`.

**Response 201**: `ExamResponse`.

### `GET …/exams`

List exams for the current academic year.

**Auth**: any authenticated user.

**Response 200**: `ExamResponse[]`.

### `POST …/exams/{examId}/publish`

Publish an exam — generates report cards + makes marks read-only.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `ExamResponse` (now `published=true`).

**Errors**: `EXAM_NOT_FOUND`, `MARKS_ALREADY_FINALIZED`.

### `GET …/exams/{examId}/marks/{sectionId}`

Grid-style sheet: student roster × subjects, pre-populated with existing marks.

**Auth**: any authenticated user.

**Response 200**: `MarksEntrySheetResponse`.

**Errors**: `EXAM_NOT_FOUND`.

### `POST …/exams/{examId}/marks`

Bulk upsert marks. Set `submitFinal=true` to freeze the draft.

**Auth**: `MARKS_WRITER` (`SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `CLASS_TEACHER`, `SUBJECT_TEACHER`).

**Request body** (`BulkMarksRequest`): `sectionId`, `entries: MarkEntryDto[]`, `submitFinal: boolean`.

**Response 201**: `MarkResponse[]`.

**Errors**: `EXAM_NOT_FOUND`, `MAX_MARKS_EXCEEDED`, `MARKS_ALREADY_FINALIZED`, `SECTION_NOT_ASSIGNED`.

### `GET …/exams/{examId}/completion/{sectionId}`

Per-subject "marks entered vs pending" tally for a section.

**Auth**: any authenticated user.

**Response 200**: `ExamCompletionStatusResponse`.

### `POST …/exams/{examId}/report-cards/generate/{sectionId}`

Generate report-card PDFs for every student in the section.

**Auth**: `OWNER_OR_ADMIN`.

**Response 201**: `ReportCardResponse[]`.

**Errors**: `EXAM_NOT_FOUND`, `REPORT_CARD_NOT_READY`.

### `GET …/students/{studentId}/report-card/{examId}`

Fetch one student's report card for one exam.

**Auth**: any authenticated user.

**Response 200**: `ReportCardResponse`.

**Errors**: `REPORT_CARD_NOT_READY`, `EXAM_NOT_FOUND`, `STUDENT_NOT_FOUND`.

### `GET …/sections/{sectionId}/exam-eligibility?windowDays=…`

Per-student attendance-% eligibility flag (pass/fail on `minAttendancePct`).

**Auth**: any authenticated user.

**Query**: `windowDays` — optional, overrides school default window.

**Response 200**: `ExamEligibilityResponse`.

---

## Teacher assignments

Source: [TeacherAssignmentController](../../backend/src/main/java/in/schoolapp/academics/TeacherAssignmentController.java). Prefix: `/api/v1/tenants/{tenantId}/teacher-assignments`.

### `POST …/teacher-assignments`

Assign a staff member to teach a subject in a section.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateTeacherAssignmentRequest`): `staffId`, `subjectId`, `sectionId`.

**Response 201**: `TeacherAssignmentResponse`.

### `GET …/teacher-assignments?staffId=…`

List assignments (optionally filter by one staff member).

**Auth**: any authenticated user.

**Query**: `staffId` — optional UUID.

**Response 200**: `TeacherAssignmentResponse[]`.

### `DELETE …/teacher-assignments/{id}`

Unassign.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `{ "success": true }`.

---

## Analytics

### Dashboard

Source: [DashboardController](../../backend/src/main/java/in/schoolapp/analytics/DashboardController.java).

#### `GET /api/v1/tenants/{tenantId}/dashboard`

Single-call bundle for the principal's landing page: attendance snapshot, alert counts, fee MTD, unmarked sections, top at-risk students.

**Auth**: any authenticated user.

**Response 200**: `DashboardResponse`.

### Alerts

Source: [AlertController](../../backend/src/main/java/in/schoolapp/analytics/AlertController.java). Prefix: `/api/v1/tenants/{tenantId}/alerts`.

#### `GET …/alerts?severity=HIGH&severity=CRITICAL`

List active alerts, optionally filtered by severity.

**Auth**: any authenticated user.

**Query**: `severity` — repeated `AlertSeverity` list; omit to get all.

**Response 200**: `AlertResponse[]`.

#### `POST …/alerts/{alertId}/dismiss`

Dismiss an alert (hides it from the dashboard; still visible in audit).

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `AlertResponse` (with `dismissed=true`).

---

## Communication — inbox

Source: [InboxController](../../backend/src/main/java/in/schoolapp/communication/InboxController.java). Prefix: `/api/v1/tenants/{tenantId}/inbox`.

Inbox views over parent WhatsApp replies. Phone numbers are always returned masked (`98765****10`).

### `GET …/inbox/mine?page=&size=`

Messages routed to the current logged-in teacher.

**Auth**: `ANY_TEACHER`.

**Query**: `page` (0), `size` (50).

**Response 200**: `InboxMessageResponse[]` paginated.

### `GET …/inbox/mine/unread-count`

Unread count for the current teacher — powers the navbar badge.

**Auth**: `ANY_TEACHER`.

**Response 200**:
```json
{ "success": true, "data": { "unread": 3 } }
```

### `GET …/inbox/unrouted?page=&size=`

Principal triage queue — messages from phones the system couldn't match to a parent.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `InboxMessageResponse[]` paginated.

### `GET …/inbox?page=&size=`

Principal read-only firehose of all inbox messages in the tenant.

**Auth**: `OWNER_OR_ADMIN`.

**Response 200**: `InboxMessageResponse[]` paginated.

### `POST …/inbox/{messageId}/read`

Mark a message read.

**Auth**: `ANY_TEACHER`.

**Response 200**: `InboxMessageResponse`.

### `POST …/inbox/{messageId}/resolve`

Mark a message resolved (removed from active teacher view).

**Auth**: `ANY_TEACHER`.

**Response 200**: `InboxMessageResponse`.

### `POST …/inbox/{messageId}/reassign?toTeacherId=…`

Principal reassigns a message to a different teacher.

**Auth**: `OWNER_OR_ADMIN`.

**Query**: `toTeacherId` — required UUID.

**Response 200**: `InboxMessageResponse`.

---

## Communication — circulars

Source: [CircularController](../../backend/src/main/java/in/schoolapp/communication/CircularController.java). Prefix: `/api/v1/tenants/{tenantId}/circulars`.

### `POST …/circulars`

Create and dispatch a circular to targeted parents.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CreateCircularRequest`): `title`, `body`, `targetType` (`ALL_PARENTS`|`CLASSES`|`SECTIONS`|`STUDENTS`), `targetIds?`, `language?`.

**Response 201**: `CircularResponse`.

**Errors**: `VALIDATION_ERROR`, `TEMPLATE_NOT_APPROVED`, `WHATSAPP_SEND_FAILED`.

### `GET …/circulars?page=&size=`

Paginated list of circulars, newest first.

**Auth**: any authenticated user.

**Query**: `page` (0), `size` (20).

**Response 200**: `CircularResponse[]` paginated.

### `GET …/circulars/{id}`

One circular's full record (including delivery counters).

**Auth**: any authenticated user.

**Response 200**: `CircularResponse`.

**Errors**: `RESOURCE_NOT_FOUND`.

---

## Payment webhooks

Sources: [StripeWebhookController](../../backend/src/main/java/in/schoolapp/payment/webhook/StripeWebhookController.java), [RazorpayWebhookController](../../backend/src/main/java/in/schoolapp/payment/webhook/RazorpayWebhookController.java).

Only one of these is active at a time, controlled by `app.payment.provider`. Both are public (HMAC-signature verified), **not for the frontend to call** — listed here for completeness.

### `POST /webhooks/stripe`

Stripe Checkout Session webhook. Header `Stripe-Signature` required. Replay window: 5 minutes.

Handled event types: `checkout.session.completed`, `checkout.session.expired`, `charge.refunded`.

### `POST /webhooks/razorpay`

Razorpay payment-link webhook. Header `X-Razorpay-Signature` required.

Handled event types: `payment_link.paid`, `payment_link.expired`, `payment.refunded`, `refund.created`.

Both publish a provider-neutral `PaymentEvent` (see 08) onto Spring's application event bus for downstream fee-record creation.

---

## WhatsApp webhook

Source: [WhatsAppWebhookController](../../backend/src/main/java/in/schoolapp/communication/webhook/WhatsAppWebhookController.java).

### `POST /webhooks/whatsapp`

BSP (Meta Cloud API / WATI / Interakt) webhook: delivery-status updates + inbound parent messages. Header `X-WA-Signature` HMAC-SHA256 required. Public — **not for the frontend**.

---

## Migration

Source: [MigrationController](../../backend/src/main/java/in/schoolapp/migration/MigrationController.java). Prefix: `/api/v1/tenants/{tenantId}/migration`.

OCR + LLM paper-register ingest. Upload → async extract → human review → commit.

### `POST …/migration` `multipart/form-data`

Upload a scanned image/PDF. Returns immediately with `status=UPLOADED`; pipeline runs async.

**Auth**: `OWNER_OR_ADMIN`.

**Form fields**:
- `type` — `MigrationJobType` (`FEE_RECEIPT`|`ATTENDANCE`|`MARKS`|`ADMISSION_FORM`)
- `file` — required (JPEG/PNG/PDF)

**Response 201**: `MigrationJobResponse` (summary — no `reviewableRecords` yet).

**Errors**: `VALIDATION_ERROR`, `INVALID_FILE_TYPE`.

### `GET …/migration`

List migration jobs for the tenant.

**Auth**: any authenticated user.

**Response 200**: `MigrationJobResponse[]` (summaries).

### `GET …/migration/{jobId}`

Full job detail — includes `reviewableRecords` when `status=REVIEW`.

**Auth**: any authenticated user.

**Response 200**: `MigrationJobResponse`.

**Errors**: `MIGRATION_JOB_NOT_FOUND`.

### `POST …/migration/{jobId}/commit`

Commit human-confirmed rows as historical entities.

**Auth**: `OWNER_OR_ADMIN`.

**Request body** (`CommitMigrationRequest`): `rows: ConfirmedRow[]` — per-type fields described in 08.

**Response 200**: `MigrationJobResponse` (now `status=COMMITTED`).

**Errors**: `MIGRATION_JOB_NOT_FOUND`, `MIGRATION_JOB_IN_WRONG_STATE`, `VALIDATION_ERROR`.

---

## Storage / file serving

Source: [FileController](../../backend/src/main/java/in/schoolapp/storage/FileController.java). Active only when `app.storage.provider=LOCAL` (default in dev).

### `GET /files/{*key}`

Serve a locally-stored file by its storage key. Unauthenticated by design (path segments contain unguessable UUIDs — receipts + report cards are embedded in outbound WhatsApp links). In S3 mode this controller is absent and files are served via presigned URLs.

**Auth**: public.

**Response 200**: raw file bytes with `Content-Type: …; Content-Disposition: inline; filename="…"`.

**Errors**: `RESOURCE_NOT_FOUND` (JSON envelope).

---

## Mobile sync

Source: [SyncController](../../backend/src/main/java/in/schoolapp/sync/SyncController.java). Prefix: `/api/v1/tenants/{tenantId}/sync`.

Used by the React Native teacher app, not the web admin. Documented here for completeness.

### `GET …/sync/pull?since=ISO-8601`

Delta snapshot of students + sections + current academic year.

**Auth**: `ANY_TEACHER`.

**Query**: `since` — optional `OffsetDateTime`. Missing = full snapshot.

**Response 200**: `SyncPullResponse`.

### `POST …/sync/push`

Batch upload of attendance rows queued offline.

**Auth**: `ATTENDANCE_WRITER`.

**Request body**: `SyncPushRequest` (`entries: AttendanceEntry[]`, max 1000).

**Response 200**: `SyncPushResponse` (per-entry result).

---

## Exports

Source: [ExportController](../../backend/src/main/java/in/schoolapp/export_/ExportController.java). Prefix: `/api/v1/tenants/{tenantId}/export`.

Streaming XLSX/CSV exports. Responses are **not** wrapped in the JSON envelope — they return raw file bytes with `Content-Disposition: attachment`.

### `GET …/export/students?format=XLSX`

Full student roster export.

**Auth**: `OWNER_OR_ADMIN`.

**Query**: `format` — `CSV` | `XLSX` (default `XLSX`).

**Response 200**: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` or `text/csv`.

### `GET …/export/fees?format=XLSX`

All fee payments.

**Auth**: `FEE_WRITER`.

**Response 200**: file bytes.

### `GET …/export/attendance?from=…&to=…&format=XLSX`

Attendance records in a date range.

**Auth**: `OWNER_OR_ADMIN`.

**Query**: `from`, `to` — required `LocalDate`s; `format` optional.

**Response 200**: file bytes.

**Errors**: `VALIDATION_ERROR` (when `from > to`).

```bash
curl "$BASE/api/v1/tenants/$TENANT/export/attendance?from=2026-01-01&to=2026-03-31&format=CSV" \
  -H "Authorization: Bearer $TOKEN" -o attendance.csv
```

---

## Audit log

Source: [AuditLogController](../../backend/src/main/java/in/schoolapp/audit/AuditLogController.java). Prefix: `/api/v1/tenants/{tenantId}/audit`.

### `GET …/audit?entityType=&entityId=&page=&size=`

Read-only audit log stream. Two shapes:

- With `entityType + entityId`: per-entity history, newest-first, no paging.
- Without: tenant-wide paginated firehose.

**Auth**: `SCHOOL_OWNER`, `PRINCIPAL` (ADMIN NOT allowed).

**Query**:
| Name | Type | Required |
|---|---|---|
| `entityType` | string | no |
| `entityId` | UUID | no |
| `page` | int | no (default 0) |
| `size` | int | no (default 50, max 200) |

**Response 200**: `AuditLogResponse[]` (paginated when no entity filter).

---

## Data deletion requests

Source: [DataDeletionController](../../backend/src/main/java/in/schoolapp/audit/DataDeletionController.java). Prefix: `/api/v1/tenants/{tenantId}/data-deletion-requests`.

### `POST …/data-deletion-requests`

File a DPDP Act deletion request for a subject. Does NOT delete data inline — logs the intent to `audit_log` and returns a tracking id; the actual purge is a separate compliance workflow.

**Auth**: `SCHOOL_OWNER`, `PRINCIPAL`.

**Request body**:
```json
{ "subjectType": "STUDENT", "subjectId": "…", "reason": "Parent-initiated deletion" }
```
`subjectType` must be one of `STUDENT` | `PARENT` | `STAFF`.

**Response 202 (Accepted)**:
```json
{
  "success": true,
  "data": { "requestId": "…", "status": "RECORDED", "message": "Request logged. …" }
}
```

**Errors**: `VALIDATION_ERROR`.

---

## Cross-links

- Role expressions (`OWNER_OR_ADMIN` etc.) → [05-role-matrix.md](05-role-matrix.md)
- Every DTO referenced here → [08-typescript-dto-reference.md](08-typescript-dto-reference.md)
- Error envelope + code list → [03-error-handling.md](03-error-handling.md)
- Auth + token refresh → [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md)
