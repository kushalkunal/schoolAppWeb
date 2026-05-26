# 06 — Information Architecture

The complete map of pages, routes, and navigation for the web admin app. Every URL the frontend needs, the backend endpoint(s) each consumes, the role(s) allowed, and the surface (sidebar item, breadcrumb, dialog, or full page) it lives on.

If it isn't here, it isn't in the app. If the backend doesn't support it (cross-check [02-api-reference.md](02-api-reference.md)), it won't appear here either.

**Cross-refs:** [00-stack-and-conventions.md](00-stack-and-conventions.md) · [02-api-reference.md](02-api-reference.md) · [04-multi-tenant-model.md](04-multi-tenant-model.md) · [05-role-matrix.md](05-role-matrix.md).

---

## 1. Navigation shell

Every authenticated screen sits inside a single **AppShell** layout composed of:

- **Topbar** — school logo + name, global search (students), inbox unread badge, user menu (logout), role chip
- **Sidebar** — 9 top-level nav items (below). Collapsible on narrow viewports.
- **Main pane** — the routed page

The shell is a `TenantLayout` that mounts at `/tenants/:tenantId/*`. It enforces three invariants on mount:

1. `tokenStorage.read()` is non-null — otherwise redirect to `/login?redirect=<here>`.
2. The decoded JWT's `tenantId` claim equals `useParams().tenantId` — otherwise redirect to `/login`.
3. Fetches [`GET /api/v1/tenants/{tenantId}`](02-api-reference.md#get-apiv1tenantstenantid) and [`GET /api/v1/tenants/{tenantId}/onboarding-status`](02-api-reference.md#get-apiv1tenantstenantidonboarding-status) once; feeds both into the `TenantContext` that every descendant can read.

### 1.1 Sidebar items

| Order | Label | Icon | Route | Visible to |
|---|---|---|---|---|
| 1 | Dashboard | `LayoutDashboard` | `/tenants/:tenantId/dashboard` | all authenticated |
| 2 | Students | `Users` | `/tenants/:tenantId/students` | all authenticated |
| 3 | Attendance | `ClipboardCheck` | `/tenants/:tenantId/attendance` | all authenticated |
| 4 | Fees | `CreditCard` | `/tenants/:tenantId/fees/dashboard` | all **except** `CLASS_TEACHER`, `SUBJECT_TEACHER`, `VIEWER`-for-writes |
| 5 | Academics | `GraduationCap` | `/tenants/:tenantId/academics/exams` | all authenticated |
| 6 | Communication | `MessageSquare` | `/tenants/:tenantId/inbox/mine` | all **except** `ACCOUNTANT`, `VIEWER` |
| 7 | Analytics | `TrendingUp` | `/tenants/:tenantId/alerts` | all authenticated |
| 8 | Staff | `Briefcase` | `/tenants/:tenantId/settings/staff` | `OWNER_OR_ADMIN` only |
| 9 | Settings | `Settings` | `/tenants/:tenantId/settings/school` | `OWNER_OR_ADMIN` only |

The **current tenant logo** + **school name** are read from `SchoolResponse` loaded by the shell. Switching tenants is not a Phase-1 feature — one JWT carries one tenant.

### 1.2 Role-based sidebar visibility matrix

Rows are sidebar items, columns are the 7 tenant-scoped roles (`SUPER_ADMIN` is platform-only, not shown). Cells are ✅ / ❌.

Abbreviations: **OWN** = `SCHOOL_OWNER`, **PRI** = `PRINCIPAL`, **ADM** = `ADMIN`, **CT** = `CLASS_TEACHER`, **ST** = `SUBJECT_TEACHER`, **ACC** = `ACCOUNTANT`, **VW** = `VIEWER`.

| Sidebar item | OWN | PRI | ADM | CT | ST | ACC | VW |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Dashboard | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Students | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Attendance | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fees | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Academics | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Communication | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| Analytics | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Staff | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Settings | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

Rationale per the backend's `@PreAuthorize` rules (see [05-role-matrix.md](05-role-matrix.md)):

- **Fees** is hidden for teachers + viewers because `FEE_WRITER` excludes them and read-only fee browsing isn't a role they'd use. If a principal hands a teacher a URL, the routes still render (reads are open) — the hide is purely a sidebar decision.
- **Communication** is hidden for `ACCOUNTANT` (`ANY_TEACHER` excludes them) and `VIEWER` (phones masked is already the policy, but surfacing parent messages is noise).
- **Academics** is hidden for `ACCOUNTANT` because their workflow is fee-only; every other role can browse exams / marks / report cards.
- **Staff** is a sub-page of Settings, promoted to a first-class sidebar item for `OWNER_OR_ADMIN` because it's a high-frequency action during onboarding.
- **Settings** hosts school profile + fee heads + reminder schedules — all `OWNER_OR_ADMIN`-gated writes.

### 1.3 `VIEWER` caveat

`VIEWER` is the phone-masked read-only role. Because there is no write it can do outside "no roles", its UI looks like the full app but every mutating button is hidden via `RequireRole`. Tables still render; detail pages still render; `ParentDto.phone` comes back masked by the server for inbox rows.

---

## 2. Public routes

These are the only routes outside `/tenants/:tenantId/*`. No `TenantLayout`, no JWT required.

| Route | Component | Backend endpoint(s) | Notes |
|---|---|---|---|
| `/login` | `LoginPage` | [`POST /api/v1/auth/otp/send`](02-api-reference.md#post-apiv1authotpsend), [`POST /api/v1/auth/otp/verify`](02-api-reference.md#post-apiv1authotpverify) | OTP-based; branches on `VITE_SIGNUP_CHANNEL`. See [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md). |
| `/signup` | `SignupPage` | [`POST /api/v1/tenants`](02-api-reference.md#post-apiv1tenants) | Public tenant signup. On success, stashes `tenantId` + identifier in `sessionStorage` and navigates to `/signup/verify`. |
| `/signup/verify` | `SignupVerifyPage` | [`POST /api/v1/auth/otp/send`](02-api-reference.md#post-apiv1authotpsend), [`POST /api/v1/auth/otp/verify`](02-api-reference.md#post-apiv1authotpverify) | Immediate OTP verify after signup — same screen the `/login` flow uses but pre-filled. |
| `/access-denied` | `AccessDeniedPage` | — | Landing page when a `403 FORBIDDEN` bubbles up (wrong role or tenant mismatch). "Go back" + "Log out" buttons; no retry. |
| `/404` (`*`) | `NotFoundPage` | — | Catch-all for unknown routes. |

There is **no `/forgot-password`**. The backend is passwordless — every login is an OTP. If the user can't receive the OTP, support is a channel-switch (phone↔email) which is only possible on `app.signup.channel=BOTH`. On `PHONE` or `EMAIL` the login screen exposes only that channel.

---

## 3. Protected route tree

Every route below mounts under `/tenants/:tenantId/*` inside `TenantLayout`. Layouts are nested — the middle levels (`FeesLayout`, `AcademicsLayout`, `SettingsLayout`) own their own sub-navigation.

### 3.1 Dashboard

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/dashboard` | `DashboardPage` | [`GET …/dashboard`](02-api-reference.md#get-apiv1tenantstenantiddashboard) + [`GET …/alerts?severity=HIGH&severity=CRITICAL`](02-api-reference.md#get-alerts) | any authenticated |

Breadcrumb: *Dashboard*.

Onboarding banner: if `OnboardingStatusResponse.percentComplete < 100` and the role is `OWNER_OR_ADMIN`, the page renders a top-of-page checklist card showing `steps[]` with per-step CTA buttons — clicking one routes to the corresponding page (e.g. "Add classes" → `/tenants/:tenantId/settings/school/classes`).

---

### 3.2 Students

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/students` | `StudentsListPage` | [`GET …/students?search=…&page=&size=`](02-api-reference.md#get-apiv1tenantstenantidstudents) | any authenticated |
| `/tenants/:tenantId/students/:studentId` | `StudentDetailPage` | [`GET …/students/{studentId}`](02-api-reference.md#get-apiv1tenantstenantidstudentsstudentid), [`GET …/students/{studentId}/documents`](02-api-reference.md#get-apiv1tenantstenantidstudentsstudentiddocuments) | any authenticated |
| `/tenants/:tenantId/students/:studentId/timeline` | `StudentTimelinePage` | [`GET …/students/{studentId}/timeline`](02-api-reference.md#get-apiv1tenantstenantidstudentsstudentidtimeline) | any authenticated |
| `/tenants/:tenantId/students/:studentId/fees` | `StudentFeeSummaryPage` | [`GET …/students/{studentId}/fee-summary`](02-api-reference.md#get-apiv1tenantstenantidstudentsstudentidfee-summary) | any authenticated |
| `/tenants/:tenantId/students/:studentId/attendance` | `StudentAttendanceHistoryPage` | [`GET …/students/{studentId}/attendance?from=&to=`](02-api-reference.md#get-apiv1tenantstenantidstudentsstudentidattendance) | any authenticated |

Breadcrumbs:

- `Students`
- `Students > Aarav Kumar`
- `Students > Aarav Kumar > Timeline`
- `Students > Aarav Kumar > Fees`
- `Students > Aarav Kumar > Attendance`

Student name is loaded via the `StudentProfileResponse.student.displayName` read by the parent detail page; nested pages read from `queryClient.getQueryData(['students', tenantId, studentId])` to avoid a second fetch just for the crumb.

**Empty state (list)**:
> **No students yet.** Create your first student to start tracking attendance, fees, and exam results.
> [+ New student]

The CTA opens a dialog (see §4).

**Tabs on detail page** (each is an anchor, not a route): *Profile · Documents · Attendance · Fees · Timeline · Report cards*. Profile + Documents are anchored panels within the detail page; Attendance, Fees, Timeline, Report cards are nested routes per the table.

---

### 3.3 Attendance

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/attendance` | `AttendanceHomePage` | [`GET …/attendance/summary?date=`](02-api-reference.md#get-apiv1tenantstenantidattendancesummary), [`GET …/attendance/unmarked?date=`](02-api-reference.md#get-apiv1tenantstenantidattendanceunmarked) | any authenticated |
| `/tenants/:tenantId/attendance/section/:sectionId` | `AttendanceGridPage` | [`GET …/sections/{sectionId}/attendance?date=`](02-api-reference.md#get-apiv1tenantstenantidsectionssectionidattendance), [`POST …/sections/{sectionId}/attendance`](02-api-reference.md#post-apiv1tenantstenantidsectionssectionidattendance) | read: any; submit: `ATTENDANCE_WRITER` |
| `/tenants/:tenantId/attendance/chronic` | `ChronicAbsenteesPage` | [`GET …/attendance/chronic?windowDays=&minAbsences=`](02-api-reference.md#get-apiv1tenantstenantidattendancechronic) | any authenticated |

Breadcrumbs:

- `Attendance`
- `Attendance > Class 5-A · 23 Apr 2026`
- `Attendance > Chronic absentees`

**Empty state (unmarked list)**:
> **All sections have marked attendance for today.** Nice. Pick any section from the sidebar to review the day.

**Empty state (chronic list)**:
> **No chronic absentees in the last 30 days.** Widen the window or lower the threshold to see more.

The attendance grid is **a full page**, not a dialog — it's a large table with one row per student, and the submit is the single most-used action in the app.

---

### 3.4 Fees

Hosted inside a `FeesLayout` at `/tenants/:tenantId/fees/*` that renders a sub-tab strip: *Dashboard · Defaulters · Receipts · Invoices · Opening balances*.

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/fees/dashboard` | `FeeDashboardPage` | [`GET …/fees/dashboard`](02-api-reference.md#get-apiv1tenantstenantidfeesdashboard) | any authenticated |
| `/tenants/:tenantId/fees/defaulters` | `DefaultersListPage` | [`GET …/fees/defaulters?page=&size=`](02-api-reference.md#get-apiv1tenantstenantidfeesdefaulters) | any authenticated |
| `/tenants/:tenantId/fees/payments/:paymentId` | `ReceiptDetailPage` | [`GET …/fees/payments/{paymentId}`](02-api-reference.md#get-apiv1tenantstenantidfeespaymentspaymentid) | any authenticated |
| `/tenants/:tenantId/fees/collect` | `QuickCollectPage` | [`POST …/fees/payments`](02-api-reference.md#post-apiv1tenantstenantidfeespayments), [`GET …/students?search=`](02-api-reference.md#get-apiv1tenantstenantidstudents) | `FEE_WRITER` |

Breadcrumbs:

- `Fees > Dashboard`
- `Fees > Defaulters`
- `Fees > Quick collect`
- `Fees > Receipts > R-2026-0412`

**Route guard**: the parent `/fees` layout wraps its `<Outlet />` in `<RequireRole roles={FEE_WRITER} fallback={<Navigate to='../dashboard' replace />}>` — but only for `/fees/collect` (the write-gated page). Dashboard, defaulters, and receipt detail are read-only, so everyone with a tenant JWT can hit them (the sidebar still hides "Fees" for teachers per §1.2).

**Quick collect** is a **full page**, not a dialog, because:
- It hosts a live student search that populates the outstanding-balance panel beside the form
- On success it shows a large "Receipt sent to WhatsApp" confirmation with the PDF preview

**Empty state (defaulters)**:
> **No outstanding fees. You're fully collected.** Check back after the next invoice cycle.

**Empty state (receipts list)** — receipts don't have a dedicated list; they live on the student's fee-summary page and the dashboard's "today's payments" tile. There is no `GET …/fees/payments` endpoint.

---

### 3.5 Academics

`AcademicsLayout` sub-tabs: *Exams · Subjects · Report cards · Eligibility*.

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/academics/exams` | `ExamsListPage` | [`GET …/exams`](02-api-reference.md#get-apiv1tenantstenantidexams) | any authenticated |
| `/tenants/:tenantId/academics/exams/:examId` | `ExamDetailPage` | [`GET …/exams`](02-api-reference.md#get-apiv1tenantstenantidexams) (scoped), [`GET …/exams/{examId}/completion/{sectionId}`](02-api-reference.md#get-apiv1tenantstenantidexamsexamidcompletionsectionid) (per section) | any authenticated |
| `/tenants/:tenantId/academics/exams/:examId/marks/:sectionId` | `MarksEntryPage` | [`GET …/exams/{examId}/marks/{sectionId}`](02-api-reference.md#get-apiv1tenantstenantidexamsexamidmarkssectionid), [`POST …/exams/{examId}/marks`](02-api-reference.md#post-apiv1tenantstenantidexamsexamidmarks) | read: any; write: `MARKS_WRITER` |
| `/tenants/:tenantId/academics/exams/:examId/report-cards/:sectionId` | `ReportCardGeneratePage` | [`POST …/exams/{examId}/report-cards/generate/{sectionId}`](02-api-reference.md#post-apiv1tenantstenantidexamsexamidreport-cardsgeneratesectionid) | `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/academics/subjects` | `SubjectsPage` | [`GET …/subjects`](02-api-reference.md#get-apiv1tenantstenantidsubjects), [`POST …/subjects/bulk`](02-api-reference.md#post-apiv1tenantstenantidsubjectsbulk) | read: any; write: `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/academics/eligibility/:sectionId` | `ExamEligibilityPage` | [`GET …/sections/{sectionId}/exam-eligibility?windowDays=`](02-api-reference.md#get-apiv1tenantstenantidsectionssectionidexam-eligibility) | any authenticated |
| `/tenants/:tenantId/academics/teacher-assignments` | `TeacherAssignmentsPage` | [`GET …/teacher-assignments`](02-api-reference.md#get-apiv1tenantstenantidteacher-assignments), [`POST …/teacher-assignments`](02-api-reference.md#post-apiv1tenantstenantidteacher-assignments), [`DELETE …/teacher-assignments/{id}`](02-api-reference.md#delete-apiv1tenantstenantidteacher-assignmentsid) | read: any; write: `OWNER_OR_ADMIN` |

Breadcrumbs:

- `Academics > Exams`
- `Academics > Exams > Term 1 (Oct 2026)`
- `Academics > Exams > Term 1 (Oct 2026) > Class 5-A · Marks entry`
- `Academics > Exams > Term 1 (Oct 2026) > Class 5-A · Report cards`
- `Academics > Subjects`
- `Academics > Eligibility > Class 10-B`
- `Academics > Teacher assignments`

**Empty state (exams)**:
> **No exams yet.** Create an exam to start tracking marks and generating report cards.
> [+ New exam]  *(only shown to `OWNER_OR_ADMIN`)*

**Empty state (subjects)**:
> **Subjects are the columns of the marks sheet.** Add them once per academic year.
> [+ Add subjects]  *(only shown to `OWNER_OR_ADMIN`)*

Marks entry is **a full page** — it's a grid of students × subjects, with a sticky submit bar. Don't try to stuff it into a dialog.

Report-card generate is **a full page** that shows per-student progress as the async task completes (polls `GET …/students/{studentId}/report-card/{examId}` per row).

---

### 3.6 Communication

`CommunicationLayout` sub-tabs: *Inbox · Unrouted · All · Circulars*.

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/inbox/mine` | `MyInboxPage` | [`GET …/inbox/mine?page=&size=`](02-api-reference.md#get-apiv1tenantstenantidinboxmine), [`GET …/inbox/mine/unread-count`](02-api-reference.md#get-apiv1tenantstenantidinboxmineunread-count) | `ANY_TEACHER` |
| `/tenants/:tenantId/inbox/unrouted` | `UnroutedInboxPage` | [`GET …/inbox/unrouted?page=&size=`](02-api-reference.md#get-apiv1tenantstenantidinboxunrouted) | `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/inbox/all` | `AllInboxPage` | [`GET …/inbox?page=&size=`](02-api-reference.md#get-apiv1tenantstenantidinbox) | `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/circulars` | `CircularsListPage` | [`GET …/circulars?page=&size=`](02-api-reference.md#get-apiv1tenantstenantidcirculars) | any authenticated |
| `/tenants/:tenantId/circulars/:id` | `CircularDetailPage` | [`GET …/circulars/{id}`](02-api-reference.md#get-apiv1tenantstenantidcircularsid) | any authenticated |

Breadcrumbs:

- `Communication > Inbox`
- `Communication > Unrouted inbox`
- `Communication > All messages`
- `Communication > Circulars`
- `Communication > Circulars > Diwali holiday notice`

**Empty state (my inbox)**:
> **Your inbox is clear.** Parent messages routed to your sections will show up here — go focus on teaching.

**Empty state (unrouted)**:
> **Every incoming message was matched to a parent.** No triage needed today.

**Empty state (circulars)**:
> **No circulars sent yet.** Write one to reach parents over WhatsApp.
> [+ New circular]  *(only shown to `OWNER_OR_ADMIN`)*

Marking a message read/resolved happens **in-place** via dropdown action on the row — no dialog, no navigation. Reassigning a message opens a small "Pick a teacher" dialog.

---

### 3.7 Analytics

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/alerts` | `AlertsListPage` | [`GET …/alerts?severity=`](02-api-reference.md#get-alerts) | any authenticated |
| `/tenants/:tenantId/alerts/:alertId` | `AlertDetailPage` | (detail derived from list row; no separate GET) | any authenticated |

Breadcrumbs:

- `Analytics > Alerts`
- `Analytics > Alerts > Class 9-B attendance not submitted`

**Empty state (alerts)**:
> **Nothing to action.** The system will surface issues here as they come up.

Dismissing an alert ([`POST …/alerts/{alertId}/dismiss`](02-api-reference.md#post-alertsalertiddismiss)) is an **in-place** action (optimistic — see [09-data-fetching-patterns.md](09-data-fetching-patterns.md)). No dedicated page needed.

---

### 3.8 Staff (a.k.a. "People" in settings land)

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/settings/staff` | `StaffListPage` | [`GET …/staff`](02-api-reference.md#get-apiv1tenantstenantidstaff) | read: any; writes: `OWNER_OR_ADMIN`, delete: `SCHOOL_OWNER`+`PRINCIPAL` only |
| `/tenants/:tenantId/settings/staff/:staffId` | `StaffDetailPage` | [`GET …/staff`](02-api-reference.md#get-apiv1tenantstenantidstaff) (filter client-side), [`GET …/teacher-assignments?staffId=`](02-api-reference.md#get-apiv1tenantstenantidteacher-assignments) | any authenticated |
| `/tenants/:tenantId/settings/substitutes` | `SubstitutesPage` | [`GET …/substitutes?date=`](02-api-reference.md#get-apiv1tenantstenantidsubstitutes), [`POST …/substitutes`](02-api-reference.md#post-apiv1tenantstenantidsubstitutes), [`DELETE …/substitutes/{id}`](02-api-reference.md#delete-apiv1tenantstenantidsubstitutesid) | read: `ANY_TEACHER`; writes: `OWNER_OR_ADMIN` |

Breadcrumbs:

- `Staff`
- `Staff > Asha Kulkarni`
- `Staff > Substitute teachers`

**Empty state (staff list)**:
> **No staff yet — except you.** Add your teachers, admins, and accountants so they can log in.
> [+ Add staff]  *(only shown to `OWNER_OR_ADMIN`)*

**Empty state (substitutes)**:
> **No substitutes for today.** Assign one when a teacher calls in sick.
> [+ Assign substitute]  *(only shown to `OWNER_OR_ADMIN`)*

---

### 3.9 Settings

`SettingsLayout` hosts a left rail with: *School profile · Classes · Fee heads · Reminder schedules · Data & privacy · Migration · Audit*.

| Route | Component | Endpoint(s) | Role |
|---|---|---|---|
| `/tenants/:tenantId/settings/school` | `SchoolProfilePage` | [`GET …/tenants/{tenantId}`](02-api-reference.md#get-apiv1tenantstenantid), [`PUT …/tenants/{tenantId}`](02-api-reference.md#put-apiv1tenantstenantid), [`POST …/tenants/{tenantId}/logo`](02-api-reference.md#post-apiv1tenantstenantidlogo) | read: any; writes: `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/settings/school/classes` | `ClassesPage` | [`GET …/classes`](02-api-reference.md#get-apiv1tenantstenantidclasses), [`POST …/classes/bulk`](02-api-reference.md#post-apiv1tenantstenantidclassesbulk) | read: any; writes: `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/settings/fee-heads` | `FeeHeadsPage` | [`GET …/fee-heads`](02-api-reference.md#get-fee-heads), [`POST …/fee-heads`](02-api-reference.md#post-fee-heads), [`PUT …/fee-heads/{id}`](02-api-reference.md#put-fee-headsid), [`DELETE …/fee-heads/{id}`](02-api-reference.md#delete-fee-headsid) | read: any; writes: `FEE_WRITER` |
| `/tenants/:tenantId/settings/reminder-schedules` | `ReminderSchedulesPage` | [`GET …/fee-reminder-schedules`](02-api-reference.md#get-fee-reminder-schedules), [`POST …/fee-reminder-schedules`](02-api-reference.md#post-fee-reminder-schedules), [`PUT …/fee-reminder-schedules/{id}`](02-api-reference.md#put-fee-reminder-schedulesid), [`DELETE …/fee-reminder-schedules/{id}`](02-api-reference.md#delete-fee-reminder-schedulesid), [`POST …/fee-reminder-schedules/run-now`](02-api-reference.md#post-fee-reminder-schedulesrun-now) | read: any; writes: `FEE_WRITER` |
| `/tenants/:tenantId/settings/migration` | `MigrationJobsPage` | [`GET …/migration`](02-api-reference.md#get-apiv1tenantstenantidmigration), [`POST …/migration`](02-api-reference.md#post-apiv1tenantstenantidmigration) | read: any; write: `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/settings/migration/:jobId` | `MigrationReviewPage` | [`GET …/migration/{jobId}`](02-api-reference.md#get-apiv1tenantstenantidmigrationjobid), [`POST …/migration/{jobId}/commit`](02-api-reference.md#post-apiv1tenantstenantidmigrationjobidcommit) | read: any; commit: `OWNER_OR_ADMIN` |
| `/tenants/:tenantId/settings/exports` | `ExportsPage` | [`GET …/export/students`](02-api-reference.md#get-apiv1tenantstenantidexportstudents), [`GET …/export/fees`](02-api-reference.md#get-apiv1tenantstenantidexportfees), [`GET …/export/attendance`](02-api-reference.md#get-apiv1tenantstenantidexportattendance) | students + attendance: `OWNER_OR_ADMIN`; fees: `FEE_WRITER` |
| `/tenants/:tenantId/settings/audit` | `AuditLogPage` | [`GET …/audit?entityType=&entityId=&page=&size=`](02-api-reference.md#get-audit) | `SCHOOL_OWNER` or `PRINCIPAL` only (ADMIN excluded) |
| `/tenants/:tenantId/settings/data-privacy` | `DataPrivacyPage` | [`POST …/data-deletion-requests`](02-api-reference.md#post-data-deletion-requests) | `SCHOOL_OWNER` or `PRINCIPAL` only |

Breadcrumbs:

- `Settings > School profile`
- `Settings > School profile > Classes & sections`
- `Settings > Fee heads`
- `Settings > Fee reminder schedules`
- `Settings > Migration > Job #1234`
- `Settings > Exports`
- `Settings > Audit log`
- `Settings > Data & privacy`

**Empty state (classes)**:
> **No classes yet.** Define the classes you teach and their sections in one step.
> [+ Add classes]  *(only shown to `OWNER_OR_ADMIN`)*

**Empty state (fee heads)**:
> **No fee heads yet.** Create heads like "Tuition", "Transport", "Lab" so invoices can categorise amounts.
> [+ Add fee head]  *(only shown to `FEE_WRITER`)*

**Empty state (migration)**:
> **No migration jobs.** Upload a scanned fee register or attendance sheet to import paper records.
> [+ Upload scan]  *(only shown to `OWNER_OR_ADMIN`)*

**Empty state (audit)**:
> **No audit entries in this window.** Broaden the filter or pick a specific entity to see its history.

**Empty state (data & privacy)**:
> **No deletion requests filed.** File a DPDP deletion request for a student, parent, or staff member.
> [+ New deletion request]  *(only shown to `SCHOOL_OWNER` / `PRINCIPAL`)*

---

## 4. Dialogs vs full pages — the rule

Stated once, applied everywhere:

> **Small records → dialog. Multi-step or grid-heavy flows → full page.**

A dialog is modal, has a single form, and commits in one button. A full page gets its own URL so the user can share it, refresh it, and navigate back to it.

### 4.1 Dialog-based creates / edits

| Feature | Trigger | Endpoint | Why dialog |
|---|---|---|---|
| Create student | `[+ New student]` on `StudentsListPage` | [`POST …/students`](02-api-reference.md#post-apiv1tenantstenantidstudents) | Short form; single-screen `CreateStudentRequest` fits |
| Edit student (basic) | "Edit" on `StudentDetailPage` > Profile tab | [`PUT …/students/{studentId}`](02-api-reference.md#put-apiv1tenantstenantidstudentsstudentid) | Patch-style — keep in-place |
| Upload student photo | "Replace photo" button | [`POST …/students/{studentId}/photo`](02-api-reference.md#post-apiv1tenantstenantidstudentsstudentidphoto) | File picker + preview + submit |
| Upload student document | `[+ Add document]` on Documents tab | [`POST …/students/{studentId}/documents`](02-api-reference.md#post-apiv1tenantstenantidstudentsstudentiddocuments) | File + type dropdown |
| Create exam | `[+ New exam]` on `ExamsListPage` | [`POST …/exams`](02-api-reference.md#post-apiv1tenantstenantidexams) | 4 fields |
| Publish exam | "Publish" button on `ExamDetailPage` | [`POST …/exams/{examId}/publish`](02-api-reference.md#post-apiv1tenantstenantidexamsexamidpublish) | Confirmation dialog |
| Create fee head | `[+ Add fee head]` on `FeeHeadsPage` | [`POST …/fee-heads`](02-api-reference.md#post-fee-heads) | Single name field |
| Rename fee head | "Rename" action | [`PUT …/fee-heads/{id}`](02-api-reference.md#put-fee-headsid) | Single name field |
| Create invoice | `[+ New invoice]` on student fee summary | [`POST …/fees/invoices`](02-api-reference.md#post-apiv1tenantstenantidfeesinvoices) | Tight form, FIFO logic on server |
| Create reminder schedule | `[+ Add schedule]` | [`POST …/fee-reminder-schedules`](02-api-reference.md#post-fee-reminder-schedules) | 5 fields |
| Edit reminder schedule | "Edit" row action | [`PUT …/fee-reminder-schedules/{id}`](02-api-reference.md#put-fee-reminder-schedulesid) | Same form as create |
| Add fee reminder for defaulters | "Send reminders" on `DefaultersListPage` selection | [`POST …/fees/reminders`](02-api-reference.md#post-apiv1tenantstenantidfeesreminders) | Message-override textarea |
| Create staff | `[+ Add staff]` on `StaffListPage` | [`POST …/staff`](02-api-reference.md#post-apiv1tenantstenantidstaff) | 4 fields + role dropdown |
| Deactivate staff | "Deactivate" row action | [`DELETE …/staff/{staffId}`](02-api-reference.md#delete-apiv1tenantstenantidstaffstaffid) | Confirmation dialog |
| Assign substitute | `[+ Assign substitute]` | [`POST …/substitutes`](02-api-reference.md#post-apiv1tenantstenantidsubstitutes) | 4-field form |
| Create teacher assignment | `[+ New assignment]` | [`POST …/teacher-assignments`](02-api-reference.md#post-apiv1tenantstenantidteacher-assignments) | 3 dropdowns |
| Create subjects (bulk) | `[+ Add subjects]` | [`POST …/subjects/bulk`](02-api-reference.md#post-apiv1tenantstenantidsubjectsbulk) | Multi-row inline add, dialog-sized |
| Dismiss alert | "Dismiss" row action | [`POST …/alerts/{alertId}/dismiss`](02-api-reference.md#post-alertsalertiddismiss) | Optional confirmation (skip if the dismiss is trivial) |
| Mark inbox read / resolved | Row dropdown | [`POST …/inbox/{messageId}/read`](02-api-reference.md#post-apiv1tenantstenantidinboxmessageidread) / [`POST …/inbox/{messageId}/resolve`](02-api-reference.md#post-apiv1tenantstenantidinboxmessageidresolve) | No dialog — direct action |
| Reassign inbox message | "Reassign" row action | [`POST …/inbox/{messageId}/reassign`](02-api-reference.md#post-apiv1tenantstenantidinboxmessageidreassign) | Pick-a-teacher dialog |
| Create circular | `[+ New circular]` on `CircularsListPage` | [`POST …/circulars`](02-api-reference.md#post-apiv1tenantstenantidcirculars) | Title + body + target picker — fits a wide dialog |
| File deletion request | `[+ New deletion request]` on `DataPrivacyPage` | [`POST …/data-deletion-requests`](02-api-reference.md#post-data-deletion-requests) | 3-field form + confirmation |
| Record opening balances | `[+ Import opening balances]` on `FeesLayout` | [`POST …/fees/opening-balances`](02-api-reference.md#post-apiv1tenantstenantidfeesopening-balances) | CSV paste textarea → preview table inside the dialog |
| Upload school logo | "Upload logo" on `SchoolProfilePage` | [`POST …/tenants/{tenantId}/logo`](02-api-reference.md#post-apiv1tenantstenantidlogo) | Single file picker |

### 4.2 Full-page flows

| Feature | Route | Why full page |
|---|---|---|
| Daily attendance submission | `/tenants/:tenantId/attendance/section/:sectionId` | Grid of students, sticky submit bar, needs horizontal space |
| Marks entry | `/tenants/:tenantId/academics/exams/:examId/marks/:sectionId` | Students × subjects grid; single dialog can't host it |
| Report-card generation | `/tenants/:tenantId/academics/exams/:examId/report-cards/:sectionId` | Polls per-student progress; long-running |
| Quick-collect fee payment | `/tenants/:tenantId/fees/collect` | Side-by-side student search + outstanding-balance panel + form |
| Migration review | `/tenants/:tenantId/settings/migration/:jobId` | Reviewable rows table, accept/edit/reject per row, multi-step commit |
| Bulk classes creation | `/tenants/:tenantId/settings/school/classes` | Array-of-classes editor with per-class sections; onboarding step |

### 4.3 Exception cases

- **Logo upload** — a dialog in Phase 1, despite being a file-only form; we don't need a dedicated page. Promote to a page if we add cropping.
- **Opening balances** — a dialog with a paste-CSV input; promote to a page when we add per-row review.
- **Circular compose** — a wide dialog for Phase 1 (title + body + target). Promote to `/tenants/:tenantId/circulars/compose` if we add templates or attachments.

---

## 5. Breadcrumb rules

The app breadcrumb is a single component `<Breadcrumbs items={…} />` rendered in the shell. Every route contributes one or more crumbs, from outermost → innermost. Convention:

1. **Top crumb** is the sidebar section name (e.g. "Students", "Academics > Exams"). No URL back to the sidebar itself.
2. **Detail crumb** shows the record's display name (not its UUID). Use cached `queryData` if available; fall back to a skeleton.
3. **Leaf crumb** is the action name (e.g. "Marks entry", "Timeline"). It's unclickable; it's where the user already is.
4. **Academic year** is not a crumb — it's ambient in the shell (shown in the topbar). Cross-year views (like the student timeline) explicitly say so in the page title.

Worked examples:

```
Students > Aarav Kumar > Timeline
Academics > Exams > Term 1 (Oct 2026) > Class 5-A · Marks entry
Fees > Defaulters
Fees > Receipts > R-2026-0412
Communication > Circulars > Diwali holiday notice
Settings > Migration > Job #1234
```

---

## 6. Search, filters, and URL state

Per [00-stack-and-conventions.md §State](00-stack-and-conventions.md), **URL state** (query params) owns every filter, page index, and date range. Rationale: browser-back works, deep-links work, copy-paste works.

| Page | URL params | Default |
|---|---|---|
| `StudentsListPage` | `?search=&page=&size=` | page=0, size=50 |
| `DefaultersListPage` | `?page=&size=` | page=0, size=50 |
| `AttendanceGridPage` | `?date=YYYY-MM-DD` | today |
| `ChronicAbsenteesPage` | `?windowDays=&minAbsences=` | 30, 5 |
| `MyInboxPage` / `UnroutedInboxPage` / `AllInboxPage` | `?page=&size=` | page=0, size=50 (mine) or 20 (all) |
| `CircularsListPage` | `?page=&size=` | page=0, size=20 |
| `AlertsListPage` | `?severity=HIGH&severity=CRITICAL` (repeated) | none → all |
| `ExamEligibilityPage` | `?windowDays=` | server default |
| `ExportsPage` (attendance) | `?from=&to=&format=` | — |
| `AuditLogPage` | `?entityType=&entityId=&page=&size=` | page=0, size=50 |

Client UI for date pickers + search inputs uses `useSearchParams` to read + write these — no `useState` mirror.

---

## 7. Global UI affordances

### 7.1 Global student search

A search input in the topbar, always visible. Typing triggers a debounced `GET /students?search=&size=10` — results show as a dropdown with "View student" / "Quick collect" per row (the second action is hidden for non-`FEE_WRITER`). Keyboard shortcut: **Cmd/Ctrl + K**.

### 7.2 Inbox unread badge

The topbar's inbox icon shows a numeric badge from [`GET …/inbox/mine/unread-count`](02-api-reference.md#get-apiv1tenantstenantidinboxmineunread-count), polled every 60s while the page is visible. Hidden entirely for `ACCOUNTANT` and `VIEWER`.

### 7.3 Alerts bell

The topbar's alerts icon shows a dot when `DashboardAlertCounts.total > 0`. Click routes to `/tenants/:tenantId/alerts`.

### 7.4 User menu

- "Your profile" — opens a dialog reading the decoded JWT claims (`name`, `role`). No edit for Phase 1.
- "Sign out" — calls [`POST /api/v1/auth/logout`](02-api-reference.md#post-apiv1authlogout), clears token storage + React Query cache, navigates to `/login`.

### 7.5 Toast region

Mounted at the shell root. Mutation success + failure toasts from [09-data-fetching-patterns.md §Mutation helpers](09-data-fetching-patterns.md) render here.

---

## 8. Error / empty / loading pages

| State | Component | Notes |
|---|---|---|
| Loading | Page skeleton (shadcn `Skeleton`) | Show within 200ms of navigating. No full-screen spinner. |
| Not found (bad record id) | `NotFoundPage` (shell) | Shows "Couldn't find this record. It may have been deactivated." |
| Access denied (403) | `/access-denied` | Reached when the interceptor or a `RequireRole` fallback redirects. |
| Session expired | `/login` | Set by the axios interceptor on any non-retriable 401. |
| Network offline | Toast + page-level `ErrorBanner` | Interceptor throws `ApiError.network(err)`; the shell shows a sticky banner while offline. |
| 500 / 503 | `ServerErrorPage` (full page) | For `INTERNAL_ERROR` / `EXTERNAL_SERVICE_ERROR`. "Try again" button refetches current query. |

Each page defines:

- **Loading** — a skeleton matching the layout.
- **Empty** — copy per §3 sections above.
- **Error** — an `ErrorBanner` at the top of the page with `error.message` + a "Retry" button.

---

## 9. Quick URL cheat-sheet

Handy for the router file (`src/router.tsx`):

```
/login                                                      → LoginPage
/signup                                                     → SignupPage
/signup/verify                                              → SignupVerifyPage
/access-denied                                              → AccessDeniedPage
/*                                                          → NotFoundPage

/tenants/:tenantId                                          → TenantLayout (outer)
  dashboard                                                 → DashboardPage

  students                                                  → StudentsListPage
  students/:studentId                                       → StudentDetailPage
    timeline                                                → StudentTimelinePage
    fees                                                    → StudentFeeSummaryPage
    attendance                                              → StudentAttendanceHistoryPage

  attendance                                                → AttendanceHomePage
  attendance/section/:sectionId                             → AttendanceGridPage
  attendance/chronic                                        → ChronicAbsenteesPage

  fees                                                      → FeesLayout
    dashboard                                               → FeeDashboardPage
    defaulters                                              → DefaultersListPage
    payments/:paymentId                                     → ReceiptDetailPage
    collect                                                 → QuickCollectPage

  academics                                                 → AcademicsLayout
    exams                                                   → ExamsListPage
    exams/:examId                                           → ExamDetailPage
    exams/:examId/marks/:sectionId                          → MarksEntryPage
    exams/:examId/report-cards/:sectionId                   → ReportCardGeneratePage
    subjects                                                → SubjectsPage
    eligibility/:sectionId                                  → ExamEligibilityPage
    teacher-assignments                                     → TeacherAssignmentsPage

  inbox                                                     → CommunicationLayout
    mine                                                    → MyInboxPage
    unrouted                                                → UnroutedInboxPage
    all                                                     → AllInboxPage
  circulars                                                 → CircularsListPage
  circulars/:id                                             → CircularDetailPage

  alerts                                                    → AlertsListPage
  alerts/:alertId                                           → AlertDetailPage

  settings                                                  → SettingsLayout
    school                                                  → SchoolProfilePage
    school/classes                                          → ClassesPage
    staff                                                   → StaffListPage
    staff/:staffId                                          → StaffDetailPage
    substitutes                                             → SubstitutesPage
    fee-heads                                               → FeeHeadsPage
    reminder-schedules                                      → ReminderSchedulesPage
    migration                                               → MigrationJobsPage
    migration/:jobId                                        → MigrationReviewPage
    exports                                                 → ExportsPage
    audit                                                   → AuditLogPage
    data-privacy                                            → DataPrivacyPage
```

---

## 10. What intentionally doesn't exist

- **`/forgot-password`** — passwordless auth (OTP). Not applicable.
- **Password change / reset** — same.
- **User profile editor** — no self-service staff update endpoint in the backend.
- **Parent-facing pages** — parents use WhatsApp only. Never render a parent login.
- **Tenant switcher** — JWT carries one tenant. Switching tenants = logout + log in as another user.
- **Academic-year switcher** — the backend's `AcademicYear.current` flag is server-managed. When the next year rolls, it flips automatically. No UI toggle.
- **Exam delete** — no `DELETE /exams/{id}` endpoint. Exams are soft-finalised via `publish`.
- **Report-card delete** — no endpoint.
- **Invoice delete** — no endpoint (waive is a status change that's not yet exposed — skip in the UI until the backend ships it).
- **Payment refund page** — the payment webhooks handle refunds, but there is no outbound "create refund" endpoint on the web admin. Don't build the button.
- **Inbox reply / outbound WhatsApp compose** — the backend doesn't expose a send-to-parent endpoint outside of circulars. Don't build a "reply" button.

If one of these appears in a PRD, double-check the backend before building — the doc regen step should have caught it.

---

## 11. Regeneration checklist

When the backend adds / removes an endpoint, update this file in lock-step:

- New listable resource → add a row to §3.
- New write → add to §4.1 (dialog) or §4.2 (full page).
- New role constraint → update §1.2 sidebar matrix.
- New filter param → update §6 URL-state table.
- New empty state → add copy to §3.
