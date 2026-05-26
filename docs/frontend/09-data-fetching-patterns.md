# 09 — Data Fetching Patterns

The React Query conventions every feature folder must follow. Get these right and the app is cacheable, invalidable, and debuggable. Get them wrong and you'll fight stale data for the rest of the project.

**Stack** (from [00-stack-and-conventions.md](00-stack-and-conventions.md)): `@tanstack/react-query` v5, `axios` with a shared interceptor, `TypeScript` strict mode.

**Cross-refs**: [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md) (the axios interceptor), [02-api-reference.md](02-api-reference.md) (the endpoints), [03-error-handling.md](03-error-handling.md) (`ApiError`), [07-feature-flows.md](07-feature-flows.md) (flow-level invalidations), [08-typescript-dto-reference.md](08-typescript-dto-reference.md) (the response types).

---

## 1. Query-key conventions

Every query key is an array that follows this shape:

```
[resource, tenantId, ...scopingArgs, ...filterArgs]
```

- **Element 0** — a stable string naming the resource (`'students'`, `'attendance'`, `'fee-dashboard'`).
- **Element 1** — **always** the tenantId. Non-negotiable.
- **Elements 2+** — scoping (e.g. `studentId`, `sectionId`, `date`) before filters (pagination, search).
- **Last element (if any)** — a plain object holding the filter snapshot.

### Why tenantId is always element 1

1. **Tenant-safe invalidation** — `queryClient.invalidateQueries({ queryKey: ['students', tenantId] })` wipes all student queries for one tenant without touching another (not that you'd have two in one session, but if a future feature ever allows impersonation, this holds up).
2. **Cache namespace** — after a logout, you can `queryClient.removeQueries({ queryKey: [_, tenantId] })` in a single pass (actually, just `queryClient.clear()` — but the model still works if we ever prefer selective).
3. **Prefix-match invalidation stays intuitive** — `['students']` still invalidates every tenant's students on logout.

### Full key catalogue

| Resource | Key | Invalidated by |
|---|---|---|
| Tenant profile | `['tenant', tenantId]` | `PUT /tenants/{tenantId}`, logo upload |
| Onboarding status | `['onboarding-status', tenantId]` | classes/bulk, subjects/bulk, staff create, teacher-assignments create |
| Classes | `['classes', tenantId]` | classes/bulk |
| Staff list | `['staff', tenantId]` | staff create/delete |
| Staff detail (via list filter) | `['staff', tenantId, staffId]` | staff create/delete |
| Substitutes for a date | `['substitutes', tenantId, date]` | substitutes create/delete |
| Students list | `['students', tenantId, { page, size, search }]` | student create/update/delete |
| Student detail | `['student', tenantId, studentId]` | student update, photo upload |
| Student profile (incl. parents) | `['student-profile', tenantId, studentId]` | student update |
| Student timeline | `['student-timeline', tenantId, studentId]` | report-card generation |
| Student documents | `['student-documents', tenantId, studentId]` | document upload/delete |
| Student fee summary | `['student-fee-summary', tenantId, studentId]` | fee payment, invoice create, opening balance |
| Attendance for a section + date | `['attendance', tenantId, sectionId, date]` | attendance submit (that section+date) |
| Attendance summary | `['attendance-summary', tenantId, date]` | attendance submit |
| Unmarked sections | `['attendance-unmarked', tenantId, date]` | attendance submit |
| Chronic absentees | `['attendance-chronic', tenantId, { windowDays, minAbsences }]` | attendance submit |
| Student attendance history | `['student-attendance', tenantId, studentId, { from, to }]` | attendance submit (that student's section) |
| Fee dashboard | `['fee-dashboard', tenantId]` | fee payment, invoice create, reminder send |
| Fee defaulters | `['fee-defaulters', tenantId, { page, size }]` | fee payment |
| Payment detail | `['payment', tenantId, paymentId]` | — (payments are immutable once created) |
| Fee heads | `['fee-heads', tenantId]` | fee-head create/update/delete |
| Reminder schedules | `['fee-reminder-schedules', tenantId]` | schedule CRUD |
| Subjects | `['subjects', tenantId]` | subjects/bulk |
| Exams | `['exams', tenantId]` | exam create, publish |
| Exam detail | `['exam', tenantId, examId]` | exam publish |
| Marks sheet | `['marks-sheet', tenantId, examId, sectionId]` | marks bulk-upsert |
| Exam completion | `['exam-completion', tenantId, examId, sectionId]` | marks bulk-upsert |
| Report cards (per section) | `['report-cards', tenantId, examId, sectionId]` | report-cards/generate |
| Student report card | `['report-card', tenantId, studentId, examId]` | report-cards/generate |
| Exam eligibility | `['exam-eligibility', tenantId, sectionId, { windowDays }]` | attendance submit |
| Teacher assignments | `['teacher-assignments', tenantId, { staffId? }]` | teacher-assignment create/delete |
| Dashboard bundle | `['dashboard', tenantId]` | many — see §2 |
| Alerts | `['alerts', tenantId, { severity }]` | alert dismiss |
| Inbox (mine) | `['inbox-mine', tenantId, { page, size }]` | read/resolve/reassign |
| Inbox unread count | `['inbox-unread-count', tenantId]` | read |
| Inbox (unrouted) | `['inbox-unrouted', tenantId, { page, size }]` | reassign |
| Inbox (all) | `['inbox-all', tenantId, { page, size }]` | read/resolve/reassign |
| Circulars | `['circulars', tenantId, { page, size }]` | circular create |
| Circular detail | `['circular', tenantId, circularId]` | — (poll-only) |
| Migration jobs | `['migration-jobs', tenantId]` | migration upload, commit |
| Migration job detail | `['migration-job', tenantId, jobId]` | commit |
| Audit log (firehose) | `['audit', tenantId, { entityType?, page, size }]` | — |
| Audit log (entity) | `['audit', tenantId, entityType, entityId]` | — |

### Key factory helpers

One helper per feature folder — no string keys scattered across components:

```ts
// src/features/students/queryKeys.ts
export const studentsKeys = {
  all: (tenantId: string) => ['students', tenantId] as const,
  list: (tenantId: string, params: { page?: number; size?: number; search?: string }) =>
    ['students', tenantId, params] as const,
  detail: (tenantId: string, studentId: string) =>
    ['student', tenantId, studentId] as const,
  profile: (tenantId: string, studentId: string) =>
    ['student-profile', tenantId, studentId] as const,
  timeline: (tenantId: string, studentId: string) =>
    ['student-timeline', tenantId, studentId] as const,
  documents: (tenantId: string, studentId: string) =>
    ['student-documents', tenantId, studentId] as const,
  feeSummary: (tenantId: string, studentId: string) =>
    ['student-fee-summary', tenantId, studentId] as const,
};
```

Using `as const` ensures TypeScript infers literal tuples — `useQuery` can then narrow the types.

---

## 2. Invalidation table (the source of truth)

After every mutation, the caller must invalidate **exactly** these keys. More is wasteful, less is stale.

| Mutation | Endpoint | Invalidate |
|---|---|---|
| `useCreateStudentMutation` | `POST /students` | `['students', tenantId]` (prefix — all pagination pages), `['onboarding-status', tenantId]` |
| `useUpdateStudentMutation` | `PUT /students/{id}` | `['students', tenantId]`, `['student', tenantId, studentId]`, `['student-profile', tenantId, studentId]` |
| `useDeleteStudentMutation` | `DELETE /students/{id}` | `['students', tenantId]`, `['dashboard', tenantId]` |
| `useUploadStudentPhotoMutation` | `POST /students/{id}/photo` | `['student', tenantId, studentId]`, `['students', tenantId]` |
| `useUploadStudentDocumentMutation` | `POST /students/{id}/documents` | `['student-documents', tenantId, studentId]` |
| `useDeleteStudentDocumentMutation` | `DELETE /students/documents/{id}` | `['student-documents', tenantId, studentId]` |
| `useCreateStaffMutation` | `POST /staff` | `['staff', tenantId]`, `['onboarding-status', tenantId]` |
| `useDeleteStaffMutation` | `DELETE /staff/{id}` | `['staff', tenantId]` |
| `useCreateClassesMutation` | `POST /classes/bulk` | `['classes', tenantId]`, `['onboarding-status', tenantId]` |
| `useCreateSubjectsMutation` | `POST /subjects/bulk` | `['subjects', tenantId]`, `['onboarding-status', tenantId]` |
| `useCreateSubstituteMutation` | `POST /substitutes` | `['substitutes', tenantId, date]` |
| `useDeleteSubstituteMutation` | `DELETE /substitutes/{id}` | `['substitutes', tenantId, date]` |
| `useSubmitAttendanceMutation` | `POST /sections/{id}/attendance` | `['attendance', tenantId, sectionId, date]`, `['attendance-summary', tenantId, date]`, `['attendance-unmarked', tenantId, date]`, `['attendance-chronic', tenantId]`, `['dashboard', tenantId]` |
| `useQuickCollectMutation` | `POST /fees/payments` | `['student-fee-summary', tenantId, studentId]`, `['fee-dashboard', tenantId]`, `['fee-defaulters', tenantId]`, `['dashboard', tenantId]` |
| `useCreateInvoiceMutation` | `POST /fees/invoices` | `['student-fee-summary', tenantId, studentId]`, `['fee-dashboard', tenantId]`, `['fee-defaulters', tenantId]` |
| `useOpeningBalancesMutation` | `POST /fees/opening-balances` | `['fee-dashboard', tenantId]`, `['fee-defaulters', tenantId]`, `['student-fee-summary', tenantId]` (prefix) |
| `useBulkReminderMutation` | `POST /fees/reminders` | — (fire-and-forget) |
| `useCreateFeeHeadMutation` | `POST /fee-heads` | `['fee-heads', tenantId]` |
| `useRenameFeeHeadMutation` | `PUT /fee-heads/{id}` | `['fee-heads', tenantId]` |
| `useDeleteFeeHeadMutation` | `DELETE /fee-heads/{id}` | `['fee-heads', tenantId]` |
| `useCreateReminderScheduleMutation` / Update / Delete / RunNow | `/fee-reminder-schedules/*` | `['fee-reminder-schedules', tenantId]` |
| `useCreateExamMutation` | `POST /exams` | `['exams', tenantId]` |
| `usePublishExamMutation` | `POST /exams/{id}/publish` | `['exams', tenantId]`, `['exam', tenantId, examId]`, `['marks-sheet', tenantId, examId]` (prefix) |
| `useBulkMarksMutation` | `POST /exams/{id}/marks` | `['marks-sheet', tenantId, examId, sectionId]`, `['exam-completion', tenantId, examId, sectionId]` |
| `useGenerateReportCardsMutation` | `POST /exams/{id}/report-cards/generate/{sectionId}` | `['report-cards', tenantId, examId, sectionId]`, `['report-card', tenantId]` (prefix), `['student-timeline', tenantId]` (prefix) |
| `useCreateTeacherAssignmentMutation` | `POST /teacher-assignments` | `['teacher-assignments', tenantId]` |
| `useDeleteTeacherAssignmentMutation` | `DELETE /teacher-assignments/{id}` | `['teacher-assignments', tenantId]` |
| `useDismissAlertMutation` | `POST /alerts/{id}/dismiss` | `['alerts', tenantId]`, `['dashboard', tenantId]` (**optimistic** — see §6) |
| `useMarkInboxReadMutation` | `POST /inbox/{id}/read` | `['inbox-mine', tenantId]` (prefix), `['inbox-unread-count', tenantId]` (**optimistic**) |
| `useResolveInboxMutation` | `POST /inbox/{id}/resolve` | `['inbox-mine', tenantId]` (prefix) |
| `useReassignInboxMutation` | `POST /inbox/{id}/reassign` | `['inbox-unrouted', tenantId]` (prefix), `['inbox-mine', tenantId]` (prefix), `['inbox-all', tenantId]` (prefix) |
| `useCreateCircularMutation` | `POST /circulars` | `['circulars', tenantId]` |
| `useUploadMigrationMutation` | `POST /migration` | `['migration-jobs', tenantId]` |
| `useCommitMigrationMutation` | `POST /migration/{id}/commit` | `['migration-jobs', tenantId]`, `['migration-job', tenantId, jobId]`, plus the keys of whatever the commit created (e.g. `['students', tenantId]` for ADMISSION_FORM commits) |
| `useCreateDeletionRequestMutation` | `POST /data-deletion-requests` | `['audit', tenantId]` |
| `useUpdateSchoolMutation` | `PUT /tenants/{id}` | `['tenant', tenantId]` |
| `useUploadSchoolLogoMutation` | `POST /tenants/{id}/logo` | `['tenant', tenantId]` |

**Prefix invalidation rule**: passing `['students', tenantId]` matches every key that **starts with** that tuple, so all pagination/filter variants get re-fetched. This is the default and almost always what you want.

---

## 3. Stale-time defaults

One dial, three buckets. Set on the `useQuery` itself; fall back to the global default for anything unspecified.

### 3.1 Global defaults

```ts
// src/queryClient.ts
import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api/errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,                 // 30s — safe default for everything
      gcTime:    5  * 60 * 1000,            // 5 min
      retry: (count, err) => {
        if (count >= 2) return false;
        if (err instanceof ApiError && isAuthError(err)) return false;
        if (err instanceof ApiError && err.code === 'RATE_LIMIT_EXCEEDED') return count < 3;
        return true;
      },
      retryDelay: (count) => Math.min(1000 * 2 ** count, 15_000),
      refetchOnWindowFocus: import.meta.env.PROD,
      refetchOnReconnect: true,
      refetchOnMount: true,
    },
    mutations: {
      retry: false,   // mutations never auto-retry — a POST side-effect might already have happened
    },
  },
});

function isAuthError(err: ApiError) {
  return err.code === 'TOKEN_EXPIRED'
      || err.code === 'TOKEN_INVALID'
      || err.code === 'UNAUTHORIZED'
      || err.code === 'FORBIDDEN';
}
```

### 3.2 Per-query overrides

| Bucket | `staleTime` | `gcTime` | Applies to |
|---|---|---|---|
| **Reference data** (rarely changes) | `5 * 60 * 1000` (5 min) | `30 * 60 * 1000` | `['classes', …]`, `['subjects', …]`, `['fee-heads', …]`, `['fee-reminder-schedules', …]`, `['tenant', …]`, `['academic-years', …]` |
| **Operational** (changes occasionally) | `60 * 1000` (1 min) | `5 * 60 * 1000` | `['students', …]`, `['staff', …]`, `['exams', …]`, `['dashboard', …]`, `['fee-dashboard', …]`, `['fee-defaulters', …]` |
| **Hot** (changes constantly) | `30 * 1000` (30s) | `2 * 60 * 1000` | `['attendance-summary', …]`, `['attendance-unmarked', …]`, `['inbox-unread-count', …]`, `['inbox-mine', …]`, `['alerts', …]` |
| **Mutable by single user** (instantly stale after user action) | `0` | `2 * 60 * 1000` | `['marks-sheet', …]` (during entry session), `['exam-completion', …]` |
| **Immutable** (write-once) | `Infinity` | `30 * 60 * 1000` | `['payment', …]`, `['circular', …]`, `['audit', …, entityId]` |

### 3.3 Polling intervals (refetchInterval)

Use sparingly — the 60s `staleTime` plus `refetchOnWindowFocus` is almost always enough.

| Query | `refetchInterval` | Condition |
|---|---|---|
| `['inbox-unread-count', tenantId]` | `60_000` | always (whenever the shell is mounted) |
| `['circular', tenantId, circularId]` | `30_000` | only while `sentCount < queuedCount` (`enabled`-gated, not polled forever) |
| `['migration-job', tenantId, jobId]` | `5_000` | only while `status` ∈ `UPLOADED, EXTRACTING` (gated) |

```ts
useQuery({
  queryKey: ['circular', tenantId, circularId],
  queryFn: () => circularsApi.get(tenantId, circularId),
  refetchInterval: (data) =>
    data && data.sentCount < data.queuedCount ? 30_000 : false,
});
```

---

## 4. Pagination

### 4.1 Numeric pagination (default)

Use for listable resources with stable order. Server returns `meta.total` / `meta.page` / `meta.limit`.

```ts
// src/features/students/useStudentsQuery.ts
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { studentsKeys } from './queryKeys';
import { studentsApi } from '../../api/endpoints/students';
import type { StudentResponse, Meta, ApiError } from '../../api/types';

interface Params {
  tenantId: string;
  page: number;
  size: number;
  search?: string;
}

export function useStudentsQuery(params: Params) {
  return useQuery<{ rows: StudentResponse[]; meta: Meta }, ApiError>({
    queryKey: studentsKeys.list(params.tenantId, {
      page: params.page, size: params.size, search: params.search,
    }),
    queryFn: () => studentsApi.list(params.tenantId, {
      page: params.page, size: params.size, search: params.search,
    }),
    placeholderData: keepPreviousData,     // v5: the renamed `keepPreviousData: true`
    staleTime: 60 * 1000,
  });
}
```

`placeholderData: keepPreviousData` is v5's name for the v4 `keepPreviousData: true`. The effect: when the user pages, the old page stays on screen while the new page loads, so pagination feels instant.

**Expose the stale flag subtly**:

```tsx
const { data, isPlaceholderData, isFetching } = useStudentsQuery(params);
const isStale = isPlaceholderData || isFetching;

return (
  <table aria-busy={isStale}>
    {isStale && <LoadingBar className="h-0.5 animate-pulse" />}
    {/* rows */}
  </table>
);
```

### 4.2 Infinite scroll (inbox only)

Use when the order is chronological and the user is "scrolling back through history." Inbox messages fit.

```ts
// src/features/communication/useInfiniteInboxQuery.ts
import { useInfiniteQuery } from '@tanstack/react-query';
import { inboxApi } from '../../api/endpoints/inbox';
import type { InboxMessageResponse, Meta, ApiError } from '../../api/types';

export function useMyInboxInfiniteQuery(tenantId: string, pageSize = 50) {
  return useInfiniteQuery<
    { rows: InboxMessageResponse[]; meta: Meta },
    ApiError,
    { rows: InboxMessageResponse[]; meta: Meta },
    readonly unknown[],
    number
  >({
    queryKey: ['inbox-mine-infinite', tenantId, { pageSize }],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => inboxApi.listMine(tenantId, { page: pageParam, size: pageSize }),
    getNextPageParam: (last) => {
      const nextPage = (last.meta.page ?? 0) + 1;
      const reachedEnd = nextPage * pageSize >= (last.meta.total ?? 0);
      return reachedEnd ? undefined : nextPage;
    },
    staleTime: 30 * 1000,
  });
}
```

Render with an `IntersectionObserver` sentinel that calls `fetchNextPage` when visible. Don't mix infinite + numeric for the same resource.

**Rule of thumb**:

- Numeric pagination — students, payments (no list endpoint but if one comes), defaulters, circulars list, audit log, staff.
- Infinite scroll — inbox-mine, inbox-unrouted, inbox-all.
- Neither — fixed-size lists (classes, subjects, fee heads, reminder schedules, alerts) — fetch once, virtualise if > 100 rows.

---

## 5. Parallel vs dependent queries

### 5.1 `useQueries` for the dashboard

The `GET /dashboard` endpoint returns one bundle, so we don't strictly *need* `useQueries` — but the dashboard also wants HIGH+CRITICAL alerts and the recent payments list side-by-side. Fire them in parallel:

```ts
// src/features/dashboard/useDashboardData.ts
import { useQueries } from '@tanstack/react-query';
import { dashboardApi } from '../../api/endpoints/dashboard';
import { alertsApi } from '../../api/endpoints/alerts';
import type { ApiError } from '../../api/types';

export function useDashboardData(tenantId: string) {
  return useQueries({
    queries: [
      {
        queryKey: ['dashboard', tenantId] as const,
        queryFn: () => dashboardApi.get(tenantId),
        staleTime: 60 * 1000,
      },
      {
        queryKey: ['alerts', tenantId, { severity: ['HIGH', 'CRITICAL'] as const }] as const,
        queryFn: () => alertsApi.list(tenantId, { severity: ['HIGH', 'CRITICAL'] }),
        staleTime: 30 * 1000,
      },
    ],
  }) as readonly [
    ReturnType<typeof useDashboardQuery>,
    ReturnType<typeof useAlertsQuery>,
  ];
}
```

The wrapper `as` cast keeps types precise for destructuring.

### 5.2 Dependent queries (`enabled`)

`useStudentTimelineQuery` depends on knowing the student's id — which we already have from the URL — but sometimes a feature depends on a previous query's *data* (e.g. we load the student, then fetch their enrollment's exam eligibility). Use `enabled`:

```ts
// src/features/students/useStudentExamEligibilityQuery.ts
import { useQuery } from '@tanstack/react-query';
import { useStudentProfileQuery } from './useStudentProfileQuery';
import { academicsApi } from '../../api/endpoints/academics';

export function useStudentExamEligibilityQuery(tenantId: string, studentId: string) {
  const profile = useStudentProfileQuery(tenantId, studentId);
  const sectionId = profile.data?.currentEnrollment?.sectionId;

  return useQuery({
    queryKey: ['exam-eligibility', tenantId, sectionId] as const,
    queryFn: () => academicsApi.examEligibility(tenantId, sectionId!),
    enabled: !!sectionId,
    staleTime: 60 * 1000,
  });
}
```

**Prefer deriving from already-loaded data** over firing a second query. Only fall back to `enabled` when the second query genuinely needs an id the first one produced.

---

## 6. Optimistic updates

Three mutations use optimistic patterns: dismiss alert, mark inbox read, upload student photo. The pattern is the same across all three.

### 6.1 Dismiss alert

```ts
// src/features/alerts/useDismissAlertMutation.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { alertsApi } from '../../api/endpoints/alerts';
import type { AlertResponse, ApiError } from '../../api/types';

interface Ctx {
  previous: AlertResponse[] | undefined;
  previousDashboard: unknown;
}

export function useDismissAlertMutation(tenantId: string) {
  const qc = useQueryClient();
  return useMutation<AlertResponse, ApiError, string, Ctx>({
    mutationFn: (alertId) => alertsApi.dismiss(tenantId, alertId),

    onMutate: async (alertId) => {
      await qc.cancelQueries({ queryKey: ['alerts', tenantId] });
      const previous = qc.getQueryData<AlertResponse[]>(['alerts', tenantId]);
      const previousDashboard = qc.getQueryData(['dashboard', tenantId]);

      // Optimistically remove the alert from every ['alerts', tenantId, …] cache
      qc.setQueriesData<AlertResponse[]>(
        { queryKey: ['alerts', tenantId] },
        (old) => old?.filter((a) => a.id !== alertId),
      );

      return { previous, previousDashboard };
    },

    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(['alerts', tenantId], ctx.previous);
      if (ctx?.previousDashboard) qc.setQueryData(['dashboard', tenantId], ctx.previousDashboard);
    },

    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['alerts', tenantId] });
      qc.invalidateQueries({ queryKey: ['dashboard', tenantId] });
    },
  });
}
```

**Always `cancelQueries` in `onMutate`** — otherwise a refetch in-flight can overwrite your optimistic state.

### 6.2 Mark inbox read

```ts
// src/features/communication/useMarkInboxReadMutation.ts
export function useMarkInboxReadMutation(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => inboxApi.markRead(tenantId, messageId),

    onMutate: async (messageId) => {
      await qc.cancelQueries({ queryKey: ['inbox-mine', tenantId] });
      await qc.cancelQueries({ queryKey: ['inbox-unread-count', tenantId] });

      const prevUnread = qc.getQueryData<{ unread: number }>(['inbox-unread-count', tenantId]);

      qc.setQueriesData<{ rows: InboxMessageResponse[]; meta: Meta }>(
        { queryKey: ['inbox-mine', tenantId] },
        (old) => old && {
          ...old,
          rows: old.rows.map((m) => (m.id === messageId ? { ...m, read: true } : m)),
        },
      );
      qc.setQueryData<{ unread: number }>(
        ['inbox-unread-count', tenantId],
        (old) => old && { unread: Math.max(0, old.unread - 1) },
      );

      return { prevUnread };
    },

    onError: (_e, _v, ctx) => {
      if (ctx?.prevUnread) qc.setQueryData(['inbox-unread-count', tenantId], ctx.prevUnread);
      qc.invalidateQueries({ queryKey: ['inbox-mine', tenantId] });
    },

    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['inbox-mine', tenantId] });
      qc.invalidateQueries({ queryKey: ['inbox-unread-count', tenantId] });
    },
  });
}
```

### 6.3 Upload student photo

Here the optimistic step is showing the *local* blob URL as the `photoUrl` until the server returns the real one.

```ts
export function useUploadStudentPhotoMutation(tenantId: string, studentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => studentsApi.uploadPhoto(tenantId, studentId, file),

    onMutate: async (file) => {
      await qc.cancelQueries({ queryKey: ['student', tenantId, studentId] });
      const previous = qc.getQueryData<StudentResponse>(['student', tenantId, studentId]);
      const blobUrl = URL.createObjectURL(file);

      qc.setQueryData<StudentResponse>(
        ['student', tenantId, studentId],
        (old) => old && { ...old, photoUrl: blobUrl },
      );

      return { previous, blobUrl };
    },

    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(['student', tenantId, studentId], ctx.previous);
      if (ctx?.blobUrl) URL.revokeObjectURL(ctx.blobUrl);
    },

    onSuccess: (_data, _v, ctx) => {
      if (ctx?.blobUrl) URL.revokeObjectURL(ctx.blobUrl);
    },

    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['student', tenantId, studentId] });
      qc.invalidateQueries({ queryKey: ['students', tenantId] });
    },
  });
}
```

**Don't over-optimise**. Creates (new student, new exam) are not good candidates for optimistic updates — the server generates the id and we need to know it before the UI can do anything else.

---

## 7. Mutation helper

Every mutation funnels through one wrapper that:

- Unwraps `ApiError`
- Triggers a failure toast (except for errors the caller wants to render inline)
- Handles 401 by delegating to the interceptor (already does this via `apiClient`, but a safety net)

```ts
// src/api/useApiMutation.ts
import { useMutation, UseMutationOptions } from '@tanstack/react-query';
import { toast } from '../lib/toast';
import { ApiError } from './errors';

type ExtraOptions = {
  /** True = don't show a toast on error; the caller will render it inline. */
  silentError?: boolean;
  /** Success toast message; default: no toast. */
  successToast?: string | ((data: unknown) => string);
};

export function useApiMutation<TInput, TOutput, TCtx = unknown>(
  opts: UseMutationOptions<TOutput, ApiError, TInput, TCtx> & ExtraOptions,
) {
  return useMutation<TOutput, ApiError, TInput, TCtx>({
    ...opts,
    onSuccess: (data, variables, context) => {
      opts.onSuccess?.(data, variables, context);
      if (opts.successToast) {
        const msg = typeof opts.successToast === 'function'
          ? opts.successToast(data)
          : opts.successToast;
        toast.success(msg);
      }
    },
    onError: (err, variables, context) => {
      opts.onError?.(err, variables, context);
      if (!opts.silentError) {
        toast.error(err.message, { code: err.code });
      }
    },
  });
}
```

Usage:

```ts
export function useCreateStudentMutation(tenantId: string) {
  const qc = useQueryClient();
  return useApiMutation<CreateStudentRequest, StudentResponse>({
    mutationFn: (req) => studentsApi.create(tenantId, req),
    successToast: (s) => `Student ${(s as StudentResponse).displayName} created`,
    silentError: true,   // the dialog renders field errors inline
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['students', tenantId] });
      qc.invalidateQueries({ queryKey: ['onboarding-status', tenantId] });
    },
  });
}
```

**Never re-throw inside `onError`** — react-query already surfaces it through the returned `error` state.

---

## 8. File uploads (multipart)

Five endpoints take `multipart/form-data`: student photo, student documents, school logo, migration upload. Build the `FormData` manually; do **not** let axios stringify JSON.

```ts
// src/api/endpoints/students.ts
export const studentsApi = {
  uploadPhoto: async (
    tenantId: string,
    studentId: string,
    file: File,
    onProgress?: (pct: number) => void,
  ): Promise<StudentResponse> => {
    const form = new FormData();
    form.append('file', file);
    const res = await apiClient.post(
      `/api/v1/tenants/${tenantId}/students/${studentId}/photo`,
      form,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      },
    );
    return res.data.data as StudentResponse;
  },

  uploadDocument: async (
    tenantId: string,
    studentId: string,
    file: File,
    type: StudentDocumentType,
  ): Promise<StudentDocumentResponse> => {
    const form = new FormData();
    form.append('file', file);
    form.append('type', type);
    const res = await apiClient.post(
      `/api/v1/tenants/${tenantId}/students/${studentId}/documents`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return res.data.data as StudentDocumentResponse;
  },
};
```

### 8.1 Progress-tracking hook

```ts
// src/api/useUploadWithProgress.ts
import { useCallback, useState } from 'react';

export function useUploadWithProgress<TInput extends File, TOutput>(
  upload: (file: TInput, onProgress: (pct: number) => void) => Promise<TOutput>,
) {
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError]       = useState<ApiError | null>(null);
  const [result, setResult]     = useState<TOutput | null>(null);

  const run = useCallback(async (file: TInput) => {
    setProgress(0); setError(null); setResult(null);
    try {
      const out = await upload(file, setProgress);
      setResult(out);
      return out;
    } catch (e) {
      setError(e as ApiError);
      throw e;
    } finally {
      setProgress(null);
    }
  }, [upload]);

  return { run, progress, error, result };
}
```

Wrap this inside a mutation if you need cache invalidation — the mutation fires on success.

**Client-side file validation** (before hitting the server):

```ts
const MAX_BYTES = 5 * 1024 * 1024;   // 5 MB, matches backend default
const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

export function validateImage(file: File): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return 'File must be PNG, JPEG, or WebP.';
  if (file.size > MAX_BYTES)            return 'File must be under 5 MB.';
  return null;
}
```

The server also validates (returns `INVALID_FILE_TYPE` / `FILE_TOO_LARGE`); the client check is for UX.

---

## 9. Streaming downloads

The three `/export/*` endpoints return binary with `Content-Disposition: attachment` — **not** wrapped in the JSON envelope. Don't use react-query (would try to parse as JSON); use `fetch` with a `Blob` response.

```ts
// src/api/downloads.ts
import { tokenStorage } from '../auth/tokenStorage';
import { ApiError } from './errors';

export async function streamDownload(
  path: string,
  suggestedFilename: string,
  onProgress?: (receivedBytes: number, totalBytes: number | null) => void,
): Promise<void> {
  const auth = tokenStorage.read();
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}${path}`, {
    headers: auth ? { Authorization: `Bearer ${auth.accessToken}` } : {},
  });

  if (!res.ok) {
    // Error responses from exports are still JSON envelopes
    const body = await res.json().catch(() => null);
    throw ApiError.from(body?.error, res.status);
  }

  // Stream with progress if a Content-Length is present
  const totalHeader = res.headers.get('Content-Length');
  const total = totalHeader ? parseInt(totalHeader, 10) : null;

  if (!res.body || !onProgress) {
    const blob = await res.blob();
    return saveAs(blob, filenameFrom(res) ?? suggestedFilename);
  }

  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.length;
    onProgress(received, total);
  }
  const blob = new Blob(chunks, { type: res.headers.get('Content-Type') ?? '' });
  saveAs(blob, filenameFrom(res) ?? suggestedFilename);
}

function filenameFrom(res: Response): string | null {
  const cd = res.headers.get('Content-Disposition');
  if (!cd) return null;
  const m = /filename="?([^"]+)"?/.exec(cd);
  return m?.[1] ?? null;
}

function saveAs(blob: Blob, filename: string): void {
  const href = URL.createObjectURL(blob);
  const a = Object.assign(document.createElement('a'), { href, download: filename });
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}
```

Usage:

```ts
export function useExportStudentsDownload(tenantId: string) {
  return useCallback(async (format: ExportFormat = 'XLSX') => {
    await streamDownload(
      `/api/v1/tenants/${tenantId}/export/students?format=${format}`,
      `students-${new Date().toISOString().slice(0, 10)}.${format.toLowerCase()}`,
    );
  }, [tenantId]);
}
```

`fetch` is used instead of axios because:
- axios + `responseType: 'blob'` wraps the error envelope in a `Blob`, making `ApiError` mapping harder.
- Streaming with progress via `ReadableStream` is cleaner on `fetch`.

Downloads are not cached in react-query. If the user clicks twice, they get two downloads.

---

## 10. Suspense — don't

React Query supports Suspense (`useSuspenseQuery`, `useSuspenseQueries`), and it's good for apps that want a single "error boundary + loading fallback" pair per route.

**We don't use it here.** Reasons:

- Every page already has an opinionated empty / loading / error state (see [06-information-architecture.md §8](06-information-architecture.md)).
- Dependent queries (§5.2) pair with Suspense awkwardly — the fallback renders until *all* queries resolve, so a fast one blocks on a slow one.
- Error boundaries on top of our `ApiError` envelope add a layer of indirection without meaningful UX benefit at this scale.

Stick with classic `{ isLoading, isError, data, error }` destructuring. If a future Phase screams for Suspense (e.g. heavy server-components prefetch), revisit.

---

## 11. Typescript generics — the consistent shape

Every `useQuery` call should type both the data shape and the error:

```ts
useQuery<StudentResponse[], ApiError>({ … });
useQuery<{ rows: StudentResponse[]; meta: Meta }, ApiError>({ … });
useQuery<DashboardResponse, ApiError>({ … });
```

Four generic slots on `useQuery`:

```
useQuery<TQueryFnData, TError, TData, TQueryKey>
```

In 99 % of cases, `TData === TQueryFnData` (no `select` transformation), so passing two generics is fine. When you use `select` (e.g. "only return the current enrollment out of the profile"), pass four:

```ts
useQuery<StudentProfileResponse, ApiError, EnrollmentSummary | null, ReturnType<typeof studentsKeys.profile>>({
  queryKey: studentsKeys.profile(tenantId, studentId),
  queryFn: () => studentsApi.getProfile(tenantId, studentId),
  select: (data) => data.currentEnrollment,
});
```

The `as const` on key factories (§1) makes the `TQueryKey` slot infer cleanly, so you rarely have to write it.

### Mutation generics

```
useMutation<TOutput, TError, TInput, TContext>
```

Always name all four in shared helpers:

```ts
useMutation<StudentResponse, ApiError, CreateStudentRequest, Ctx>({ … });
```

---

## 12. Example hooks (reference implementations)

### 12.1 `useStudentsQuery`

```ts
// src/features/students/useStudentsQuery.ts
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { studentsApi } from '../../api/endpoints/students';
import { studentsKeys } from './queryKeys';
import type { StudentResponse, Meta, ApiError } from '../../api/types';

interface Params {
  tenantId: string;
  page?: number;
  size?: number;
  search?: string;
}

export function useStudentsQuery({ tenantId, page = 0, size = 50, search }: Params) {
  return useQuery<{ rows: StudentResponse[]; meta: Meta }, ApiError>({
    queryKey: studentsKeys.list(tenantId, { page, size, search }),
    queryFn: () => studentsApi.list(tenantId, { page, size, search }),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });
}
```

### 12.2 `useCreateStudentMutation`

```ts
import { useApiMutation } from '../../api/useApiMutation';
import { useQueryClient } from '@tanstack/react-query';
import { studentsApi } from '../../api/endpoints/students';
import type { CreateStudentRequest, StudentResponse } from '../../api/types';

export function useCreateStudentMutation(tenantId: string) {
  const qc = useQueryClient();
  return useApiMutation<CreateStudentRequest, StudentResponse>({
    mutationFn: (req) => studentsApi.create(tenantId, req),
    silentError: true,           // dialog renders field errors inline
    successToast: (s) => `${(s as StudentResponse).displayName} added`,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['students', tenantId] });
      qc.invalidateQueries({ queryKey: ['onboarding-status', tenantId] });
    },
  });
}
```

### 12.3 `useSubmitAttendanceMutation`

```ts
export function useSubmitAttendanceMutation(
  tenantId: string,
  sectionId: string,
) {
  const qc = useQueryClient();
  return useApiMutation<SubmitAttendanceRequest, AttendanceSubmitResponse>({
    mutationFn: (body) => attendanceApi.submit(tenantId, sectionId, body),
    successToast: (r) => {
      const out = r as AttendanceSubmitResponse;
      return `Submitted. ${out.notificationsQueued} parent alerts queued.`;
    },
    onSuccess: (data) => {
      const date = (data as AttendanceSubmitResponse).date;
      qc.invalidateQueries({ queryKey: ['attendance', tenantId, sectionId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-summary', tenantId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-unmarked', tenantId, date] });
      qc.invalidateQueries({ queryKey: ['attendance-chronic', tenantId] });
      qc.invalidateQueries({ queryKey: ['dashboard', tenantId] });
    },
  });
}
```

### 12.4 `useDashboardQuery`

```ts
// The single bundle query (the composite `useDashboardData` in §5.1 adds the alerts tile).
export function useDashboardQuery(tenantId: string) {
  return useQuery<DashboardResponse, ApiError>({
    queryKey: ['dashboard', tenantId] as const,
    queryFn: () => dashboardApi.get(tenantId),
    staleTime: 60 * 1000,
    refetchOnWindowFocus: true,
  });
}
```

### 12.5 `useDismissAlertMutation`

(Full implementation in §6.1.)

### 12.6 `useExportStudentsDownload`

```ts
export function useExportStudentsDownload(tenantId: string) {
  return useCallback(
    (format: ExportFormat = 'XLSX', onProgress?: (r: number, t: number | null) => void) =>
      streamDownload(
        `/api/v1/tenants/${tenantId}/export/students?format=${format}`,
        `students-${new Date().toISOString().slice(0, 10)}.${format.toLowerCase()}`,
        onProgress,
      ),
    [tenantId],
  );
}
```

### 12.7 `useUploadStudentPhotoMutation`

(Full implementation in §6.3.)

---

## 13. Global setup checklist

Wire these once in `src/main.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient } from './queryClient';

<QueryClientProvider client={queryClient}>
  <AuthProvider>
    <RouterProvider router={router} />
  </AuthProvider>
  {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
</QueryClientProvider>
```

Logout handling:

```ts
// inside AuthProvider.logout
await apiClient.post('/api/v1/auth/logout', { refreshToken: current?.refreshToken })
  .catch(() => { /* best-effort */ });
tokenStorage.clear();
queryClient.clear();              // drop every cached query
navigate('/login', { replace: true });
```

Tenant-switch handling: not applicable — one JWT = one tenant.

---

## 14. Debugging tips

- Enable the devtools (above) in dev; open it with the bottom-right icon. Every query's key + stale state is visible.
- Filter keys in devtools by typing the resource name — e.g. `students` shows all student queries across pagination variants.
- If a mutation doesn't invalidate the expected cache, check the **exact** tuple: `['students', tenantId]` and `['students', tenantId, {}]` are different keys.
- Stale-time lies when `refetchOnWindowFocus` fires — it's deliberate (the window regained focus, we probably want fresh data). If a test is flaky because of this, pass `refetchOnWindowFocus: false` on the query for that test.
- Use `queryClient.getQueryState(['students', tenantId])` in a debugger to see `fetchStatus`, `data`, `dataUpdatedAt`.

---

## 15. Regeneration checklist

When the backend adds an endpoint:

- Add a query-key entry to §1 — decide the bucket.
- Pick a stale-time bucket from §3.2.
- If it's a write, add the key to §2's invalidation table.
- If it's a binary stream, use the pattern in §9 — do not wrap in react-query.
- If it's a multipart upload, use the pattern in §8.
- Add an example hook to §12 if it's non-trivial.
