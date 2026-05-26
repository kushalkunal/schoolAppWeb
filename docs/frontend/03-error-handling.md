# 03 — Error Handling

The error contract is a **single envelope shape** plus a **closed set of string codes**. Match on the code, not the HTTP status, not the message. The message is for humans; the code is for branching.

**Source of truth**:

- [ApiResponse](../../backend/src/main/java/in/schoolapp/common/ApiResponse.java) — envelope
- [ErrorCode](../../backend/src/main/java/in/schoolapp/common/ErrorCode.java) — every code + HTTP status
- [AppException](../../backend/src/main/java/in/schoolapp/common/AppException.java) — how services throw
- [GlobalExceptionHandler](../../backend/src/main/java/in/schoolapp/common/GlobalExceptionHandler.java) — which throwable becomes which envelope

Cross-refs: [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md), [02-api-reference.md](02-api-reference.md), [08-typescript-dto-reference.md](08-typescript-dto-reference.md).

---

## 1. The envelope

Every JSON response — success or failure — has this exact outer shape:

```ts
interface ApiEnvelope<T> {
  success: boolean;
  data?:   T;                // present on success
  error?:  {                 // present on failure
    code:    string;         // ErrorCode name; stable
    message: string;         // human-readable; may change
    details?: Record<string, unknown>;
  };
  meta?:   {                 // pagination; success only
    total?:      number;     // Long on the wire
    page?:       number;
    limit?:      number;
    nextCursor?: string;
  };
}
```

**Null-omission**: Jackson is configured with `@JsonInclude(NON_NULL)` on the envelope record, so fields that are null simply aren't in the JSON. Client parsing must treat `error`, `data`, `meta`, and `details` as optional. Don't rely on key presence to distinguish states — use `success`.

**Success example**:

```json
{ "success": true, "data": { "id": "…", "name": "Class 5A" } }
```

**Error example (with details)**:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "fieldErrors": {
        "phone": ["must match \"^[6-9]\\d{9}$\""]
      }
    }
  }
}
```

**Paginated list**:

```json
{
  "success": true,
  "data": [ /* items */ ],
  "meta": { "total": 1250, "page": 0, "limit": 50 }
}
```

---

## 2. Full error-code table

Every `ErrorCode` value, its HTTP status, the domains that throw it, and how the UI should render it. Grouped by the same sections as the enum file.

### 2.1 Auth

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `PHONE_NOT_FOUND` | 404 | `AuthService.sendOtp`, `AuthService.verifyOtp` | Login screen — inline error under the identifier input. Message is already user-ready. |
| `OTP_INVALID` | 401 | `OtpService.verifyOtp` | OTP input — inline. After too many attempts the message explicitly says "Request a new OTP"; reset state to the send step. |
| `OTP_EXPIRED` | 401 | `OtpService.verifyOtp` | OTP input — inline. Offer a "Resend OTP" CTA. |
| `OTP_RATE_LIMITED` | 429 | `OtpService.sendOtp` | Toast + disable resend button for ~60s. Do not auto-retry. |
| `TOKEN_EXPIRED` | 401 | `JwtService.parse`, `RefreshTokenService.rotate` | **Never bubbles to UI** — the axios interceptor handles it (refresh + retry). If refresh itself returns this code, redirect to `/login` with toast "Your session expired." |
| `TOKEN_INVALID` | 401 | `JwtService.parse`, `JwtAuthFilter` fallback | Redirect to `/login`. Toast "Please log in again." Clear token storage. |
| `UNAUTHORIZED` | 401 | `TenantContext.validateTenant` (unauth), `GlobalExceptionHandler.handleAuth`, `AuthService.refresh` (deactivated) | Redirect to `/login`. Toast with `error.message` (server may say "Account no longer active"). |
| `FORBIDDEN` | 403 | `TenantContext.validateTenant` (tenant mismatch), `GlobalExceptionHandler.handleAccessDenied` (`@PreAuthorize` reject) | Route to `/access-denied` page. **Never auto-retry**. See [04-multi-tenant-model.md](04-multi-tenant-model.md) for the tenant case. |

### 2.2 Validation

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | `GlobalExceptionHandler.handleValidation` (Bean Validation on `@Valid` DTOs), `handleConstraintViolation`, `handleTypeMismatch`, `handleMethodNotSupported`; many services throw it directly (file checks, signup channel, date ranges, etc.) | If `details.fieldErrors` present → map each field to its form input; else toast the message. |
| `REQUIRED_FIELD_MISSING` | 400 | `GlobalExceptionHandler.handleMissingParam` (missing `@RequestParam`) | Developer error — toast "Missing parameter" and log. End users shouldn't see this. |
| `INVALID_PHONE` | 400 | [PhoneNormalizer](../../backend/src/main/java/in/schoolapp/common/PhoneNormalizer.java) | Inline on the phone field. Use the server message — it's specific. |
| `INVALID_DATE` | 400 | (reserved — not yet thrown in code, but part of the enum) | Inline on the date field. |
| `INVALID_FILE_TYPE` | 400 | (reserved — file upload validators) | Inline under the file picker. |
| `FILE_TOO_LARGE` | 413 | (reserved — Spring multipart limit handler) | Toast "File exceeds the size limit." |

### 2.3 School / config

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `SCHOOL_NOT_FOUND` | 404 | `SchoolService.getSchoolEntity`, widely propagated | Dedicated "School not found" page if the URL's `tenantId` is bad (rare — most such cases are caught by `TenantInterceptor`). |
| `ACADEMIC_YEAR_NOT_FOUND` | 404 | `AcademicYearService` | Inline where the year selector lives. |
| `CLASS_NOT_FOUND` | 404 | — | "Class not found" page or inline in selectors. |
| `SECTION_NOT_FOUND` | 404 | `StudentService` | Inline on section selector. |
| `SECTION_NOT_ASSIGNED` | 403 | (reserved; teacher assignment checks) | Access-denied inline panel on attendance/marks pages — "You are not assigned to this section." |

### 2.4 Student / parent

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `STUDENT_NOT_FOUND` | 404 | `StudentService` | Student-detail page → "Student not found". |
| `DUPLICATE_ADMISSION_NUMBER` | 409 | `StudentService.create` | Inline on the admission-number input. |
| `PARENT_NOT_FOUND` | 404 | `FamilyService` | Inline where the parent is selected. |

### 2.5 Attendance

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `ATTENDANCE_ALREADY_SUBMITTED` | 409 | `AttendanceService` | Toast + refetch. Usually means a teammate submitted in parallel — show the submitted snapshot read-only. |
| `ATTENDANCE_RECORD_NOT_FOUND` | 404 | (reserved) | Inline on attendance detail. |

### 2.6 Fee

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `INVOICE_NOT_FOUND` | 404 | `FeePaymentService` | "Invoice not found" on invoice detail. |
| `PAYMENT_AMOUNT_EXCEEDS_DUE` | 400 | `FeePaymentService.create` | Inline on the amount field with server message. |
| `RECEIPT_NOT_FOUND` | 404 | (reserved) | "Receipt not available" state. |

### 2.7 Academics

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `EXAM_NOT_FOUND` | 404 | `ExamService` | "Exam not found" page. |
| `MARKS_ALREADY_FINALIZED` | 409 | `MarksService.save` | Toast — marks for the section are locked. Show the read-only snapshot and disable the save button. |
| `MAX_MARKS_EXCEEDED` | 400 | `MarksService.save` | Inline on the specific mark input; server message identifies the row. |
| `REPORT_CARD_NOT_READY` | 409 | `ReportCardService.download` | Empty state on the report-card page with a "Generate" CTA if the role allows. |

### 2.8 Communication

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `WHATSAPP_SEND_FAILED` | 502 | (WATI dispatcher error path) | Toast "WhatsApp send failed — retrying in the background." **This is the only communication-layer error the frontend sees synchronously**, since most dispatch is async. |
| `TEMPLATE_NOT_APPROVED` | 424 | (template selector in communication) | Inline on the template-picker — "Template is pending WhatsApp approval." |

### 2.9 Migration / OCR

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `MIGRATION_JOB_NOT_FOUND` | 404 | `MigrationJobService` | "Job not found" on the migration status page. |
| `MIGRATION_JOB_IN_WRONG_STATE` | 409 | `MigrationJobService` | Toast with server message ("cannot approve a PENDING job" etc.). Refetch — the UI is stale. |

### 2.10 General

| Code | HTTP | Thrown by | UI rendering |
|---|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | `GlobalExceptionHandler.handleNotFound` (fallthrough), several services | Generic 404 page. Log to analytics. |
| `RATE_LIMIT_EXCEEDED` | 429 | `RateLimitFilter` | Warning toast + exponential back-off via React Query retry. |
| `EXTERNAL_SERVICE_ERROR` | 502 | Storage (`S3FileStorageService`), OCR, LLM, Razorpay/Stripe gateways | Toast "A downstream service is unavailable. Please try again." Retryable; let React Query retry up to 3× with back-off. |
| `INTERNAL_ERROR` | 500 | `GlobalExceptionHandler.handleGeneral` (catch-all) | Generic "Something went wrong" page with `X-Request-Id` if exposed. Log to Sentry/analytics. **Do not retry** automatically — the user decides. |

---

## 3. Validation error shape

Bean Validation failures from `@Valid` / `@Validated` DTOs hit `GlobalExceptionHandler.handleValidation` and produce a structured `details.fieldErrors`:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "fieldErrors": {
        "phone":     ["must match \"^[6-9]\\d{9}$\""],
        "firstName": ["must not be blank"]
      }
    }
  }
}
```

Each field can have multiple messages. Field names match the DTO property (camelCase). Use this shape to drive react-hook-form's `setError`:

```ts
import { UseFormSetError, FieldValues, Path } from "react-hook-form";
import { ApiError } from "./errors";

export function applyServerFieldErrors<T extends FieldValues>(
  err: unknown,
  setError: UseFormSetError<T>,
): boolean {
  if (!(err instanceof ApiError) || err.code !== "VALIDATION_ERROR") return false;
  const fieldErrors = err.details?.fieldErrors as Record<string, string[]> | undefined;
  if (!fieldErrors) return false;
  for (const [field, messages] of Object.entries(fieldErrors)) {
    setError(field as Path<T>, { type: "server", message: messages[0] });
  }
  return true;
}
```

**Other validation failures** that do **not** populate `fieldErrors` (still `VALIDATION_ERROR` code, 400 status):

- `MethodArgumentTypeMismatchException` — bad path-var type (e.g., non-UUID where UUID expected). Server message: `"Parameter 'foo' has invalid value"`. Treat as a developer error.
- `ConstraintViolationException` — `@Validated` on a `@RequestParam` or `@PathVariable`. Server returns the violation message; render as a toast.
- `HttpRequestMethodNotSupportedException` (405) — also returns `VALIDATION_ERROR`. Treat as a developer error.
- Most service-thrown `VALIDATION_ERROR`s (file empty, invalid date range, signup channel, etc.) have a user-ready `message` and **no details** — show the message as-is in a toast or inline.

**Pattern**: try `applyServerFieldErrors` first; if it returns `false`, fall back to a toast on `error.message`.

---

## 4. The `ApiError` class

Every network failure / envelope-error the client sees is wrapped in an `ApiError`. The axios interceptor (see [01-auth-and-token-lifecycle.md](01-auth-and-token-lifecycle.md) §7) converts raw axios errors and envelope `{ success: false }` responses into `ApiError` instances, so every React Query `onError(err)` receives the same shape.

```ts
// src/api/errors.ts

export type ErrorCodeName =
  // Auth
  | "PHONE_NOT_FOUND" | "OTP_INVALID" | "OTP_EXPIRED" | "OTP_RATE_LIMITED"
  | "TOKEN_EXPIRED"  | "TOKEN_INVALID" | "UNAUTHORIZED" | "FORBIDDEN"
  // Validation
  | "VALIDATION_ERROR" | "REQUIRED_FIELD_MISSING" | "INVALID_PHONE"
  | "INVALID_DATE" | "INVALID_FILE_TYPE" | "FILE_TOO_LARGE"
  // School / config
  | "SCHOOL_NOT_FOUND" | "ACADEMIC_YEAR_NOT_FOUND" | "CLASS_NOT_FOUND"
  | "SECTION_NOT_FOUND" | "SECTION_NOT_ASSIGNED"
  // Student / parent
  | "STUDENT_NOT_FOUND" | "DUPLICATE_ADMISSION_NUMBER" | "PARENT_NOT_FOUND"
  // Attendance
  | "ATTENDANCE_ALREADY_SUBMITTED" | "ATTENDANCE_RECORD_NOT_FOUND"
  // Fee
  | "INVOICE_NOT_FOUND" | "PAYMENT_AMOUNT_EXCEEDS_DUE" | "RECEIPT_NOT_FOUND"
  // Academics
  | "EXAM_NOT_FOUND" | "MARKS_ALREADY_FINALIZED" | "MAX_MARKS_EXCEEDED"
  | "REPORT_CARD_NOT_READY"
  // Communication
  | "WHATSAPP_SEND_FAILED" | "TEMPLATE_NOT_APPROVED"
  // Migration / OCR
  | "MIGRATION_JOB_NOT_FOUND" | "MIGRATION_JOB_IN_WRONG_STATE"
  // General
  | "RESOURCE_NOT_FOUND" | "RATE_LIMIT_EXCEEDED" | "EXTERNAL_SERVICE_ERROR"
  | "INTERNAL_ERROR"
  // Client-synthetic (no server equivalent)
  | "NETWORK_ERROR";

/** Codes for which auto-retry is potentially safe. */
const TRANSIENT_CODES: ReadonlySet<ErrorCodeName> = new Set([
  "RATE_LIMIT_EXCEEDED",
  "EXTERNAL_SERVICE_ERROR",
  "NETWORK_ERROR",
]);

export class ApiError extends Error {
  constructor(
    public readonly code: ErrorCodeName,
    message: string,
    public readonly httpStatus: number,
    public readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }

  /** True if a retry with back-off might succeed; does not include auth/validation/domain codes. */
  get isTransient(): boolean {
    return TRANSIENT_CODES.has(this.code);
  }

  /** Build from the server envelope's `error` object. */
  static from(
    err: { code?: string; message?: string; details?: Record<string, unknown> } | undefined,
    httpStatus: number,
  ): ApiError {
    const code    = (err?.code ?? "INTERNAL_ERROR") as ErrorCodeName;
    const message = err?.message ?? "Unexpected error";
    return new ApiError(code, message, httpStatus, err?.details);
  }

  /** Build for axios network failures with no response. */
  static network(cause: unknown): ApiError {
    const message = cause instanceof Error
      ? `Network error: ${cause.message}`
      : "Network error";
    return new ApiError("NETWORK_ERROR", message, 0);
  }
}

/** Type guard — use in React Query `onError` / error boundaries. */
export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/** Type guard with a specific code — branches on a single case concisely. */
export function hasCode<C extends ErrorCodeName>(
  err: unknown, code: C,
): err is ApiError & { code: C } {
  return isApiError(err) && err.code === code;
}
```

The `NETWORK_ERROR` synthetic is for offline / DNS / CORS — cases where there's no response body to read. Treat it as transient but cap retries (React Query's default is 3).

---

## 5. Retry strategy

Rule of thumb: **retry transport, not semantics**. A 500 from the payment gateway (`EXTERNAL_SERVICE_ERROR`) means "try again in a moment." A 409 from `MARKS_ALREADY_FINALIZED` means "state on the server is not what you thought" — no amount of retrying fixes it.

| Code | Auto-retry? | Strategy |
|---|---|---|
| `NETWORK_ERROR` | yes | 3×, exp back-off 500ms → 4s |
| `RATE_LIMIT_EXCEEDED` | yes | honour `Retry-After`, else 1s → 8s |
| `EXTERNAL_SERVICE_ERROR` | yes | 3×, exp back-off 500ms → 4s |
| `INTERNAL_ERROR` | **no** | Show "something went wrong" and let the user retry manually |
| `TOKEN_EXPIRED` | handled by interceptor | Single-flight refresh + retry once |
| `VALIDATION_ERROR` / any 400 | no | Client must fix the payload |
| `UNAUTHORIZED` / `FORBIDDEN` | no | Redirect / access-denied page |
| any 404 | no | Not-found state |
| any 409 (domain conflict) | no | Refetch to resync and surface the conflict |

**React Query config** — one global default, overrides per-query as needed:

```ts
// src/main.tsx (queryClient setup)
import { QueryClient } from "@tanstack/react-query";
import { isApiError } from "./api/errors";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (!isApiError(error)) return failureCount < 3;  // e.g., unexpected throw
        return error.isTransient && failureCount < 3;
      },
      retryDelay: (attempt) => Math.min(1000 * Math.pow(2, attempt), 8_000),
      staleTime: 30_000,
    },
    mutations: {
      retry: false,  // mutations are never auto-retried; the user decides
    },
  },
});
```

---

## 6. UI patterns

Consistent placement so users don't hunt for the error. Three categories.

### 6.1 Mutation errors → toast

A click / submit went wrong. Show a toast with `error.message`, keep the form intact, clear any optimistic update.

```tsx
const m = useCreateStudentMutation();

const onSubmit = async (values: CreateStudent) => {
  try {
    await m.mutateAsync(values);
    toast.success("Student added");
    navigate(`..`);
  } catch (err) {
    if (applyServerFieldErrors(err, form.setError)) return;  // inline
    toast.error(friendlyMessage(err));                       // fallback
  }
};
```

### 6.2 Query errors → inline banner

A page tried to load and failed. Replace the content with `<ErrorBanner>`, preserve the chrome (sidebar / header) so the user can navigate away.

```tsx
const q = useStudentsQuery(tenantId);

if (q.isLoading) return <Spinner />;
if (q.isError)   return <ErrorBanner error={q.error} onRetry={q.refetch} />;
return <StudentsTable rows={q.data} />;
```

### 6.3 Special paths — handled before UI sees them

| Code | Who handles | Never bubbles to a component |
|---|---|---|
| `TOKEN_EXPIRED` | axios interceptor | if refresh works, the component sees the retried success |
| `TOKEN_INVALID`, `UNAUTHORIZED` (no JWT) | axios interceptor | interceptor clears storage and navigates to `/login` |
| `FORBIDDEN` | route guard + global error boundary | redirect to `/access-denied` |
| `RESOURCE_NOT_FOUND` for the whole page | route loader | 404 page |

The inline banner is only for *recoverable* query failures (network, 5xx, unexpected 4xx).

### 6.4 `<ErrorBanner>` component

```tsx
// src/components/shared/ErrorBanner.tsx
import { AlertCircle, RefreshCw } from "lucide-react";
import { isApiError } from "../../api/errors";
import { friendlyMessage } from "../../api/errorMessages";

interface Props {
  error:     unknown;
  onRetry?:  () => void;
  /** If true, show the machine-readable code (dev / staging only). */
  debug?:    boolean;
}

export function ErrorBanner({ error, onRetry, debug = import.meta.env.DEV }: Props) {
  const message = friendlyMessage(error);
  const code    = isApiError(error) ? error.code : undefined;

  return (
    <div
      role="alert"
      className="border border-destructive/40 bg-destructive/5 text-destructive rounded-md p-4 flex gap-3"
    >
      <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
      <div className="flex-1">
        <p className="font-medium">{message}</p>
        {debug && code && (
          <p className="text-xs opacity-70 mt-1">code: {code}</p>
        )}
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="inline-flex items-center gap-1 text-sm font-medium hover:underline"
        >
          <RefreshCw className="w-4 h-4" /> Retry
        </button>
      )}
    </div>
  );
}
```

### 6.5 `useApiError` hook — single source of display decisions

Inspect an error and produce the right UX side-effect. Useful when a single handler needs to pick between inline, toast, navigate, etc.

```tsx
// src/hooks/useApiError.ts
import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "../components/ui/toast";
import { isApiError } from "../api/errors";
import { friendlyMessage } from "../api/errorMessages";

type Disposition = "inline" | "toast" | "redirect" | "handled";

export function useApiError() {
  const navigate = useNavigate();

  return useCallback((err: unknown): Disposition => {
    if (!isApiError(err)) {
      toast.error("Something went wrong. Please try again.");
      return "toast";
    }
    switch (err.code) {
      case "TOKEN_EXPIRED":
      case "TOKEN_INVALID":
      case "UNAUTHORIZED":
        // The interceptor should have redirected; this is a belt-and-braces fallback.
        navigate("/login", { replace: true });
        return "redirect";
      case "FORBIDDEN":
        navigate("/access-denied", { replace: true });
        return "redirect";
      case "VALIDATION_ERROR":
        // Caller decides — they have setError. We don't touch it here.
        return "inline";
      case "RATE_LIMIT_EXCEEDED":
        toast.warning(friendlyMessage(err));
        return "toast";
      default:
        toast.error(friendlyMessage(err));
        return "toast";
    }
  }, [navigate]);
}
```

### 6.6 `toast(error)` helper + message map

Keep the error → user-message map in one file so the wording is consistent and reviewable. Use the server's `error.message` as the default; override only when the server message leaks an implementation detail or isn't clear enough.

```ts
// src/api/errorMessages.ts
import { ApiError, ErrorCodeName, isApiError } from "./errors";

/** Per-code overrides; fall through to the server message, then a generic. */
const OVERRIDE: Partial<Record<ErrorCodeName, string>> = {
  NETWORK_ERROR:           "We can't reach the server. Check your connection and try again.",
  INTERNAL_ERROR:          "Something went wrong on our side. Please try again, and if it keeps happening let us know.",
  TOKEN_EXPIRED:           "Your session expired. Please sign in again.",
  TOKEN_INVALID:           "Please sign in again.",
  UNAUTHORIZED:            "Please sign in again.",
  FORBIDDEN:               "You don't have permission for this action.",
  RATE_LIMIT_EXCEEDED:     "You're going a bit too fast. Please wait a few seconds and try again.",
  EXTERNAL_SERVICE_ERROR:  "A downstream service is temporarily unavailable. Please try again shortly.",
  // The following have server messages that are already user-ready — leave empty to use them.
  // PHONE_NOT_FOUND, OTP_INVALID, OTP_EXPIRED, VALIDATION_ERROR, MAX_MARKS_EXCEEDED, ...
};

export function friendlyMessage(err: unknown): string {
  if (!isApiError(err)) return "Something went wrong. Please try again.";
  return OVERRIDE[err.code] ?? err.message ?? "Something went wrong.";
}

/** Thin wrapper that picks a toast variant based on the code. */
export function toastForError(err: unknown, toast: { error: (m: string) => void; warning: (m: string) => void }) {
  const msg = friendlyMessage(err);
  if (err instanceof ApiError && err.code === "RATE_LIMIT_EXCEEDED") {
    toast.warning(msg);
    return;
  }
  toast.error(msg);
}
```

---

## 7. Error-message map (user-facing copy)

Paste this table next to the design reviewer — every visible string is here. "Use server message" means the `error.message` from the backend is already user-ready; no override.

| Code | Developer meaning | End-user message |
|---|---|---|
| `PHONE_NOT_FOUND` | No active Staff row for phone/email | Use server message — e.g. "No active account is registered with this phone" |
| `OTP_INVALID` | Wrong OTP or too many attempts | Use server message — e.g. "Incorrect OTP" / "Too many incorrect attempts. Request a new OTP." |
| `OTP_EXPIRED` | Redis `otp:code` key missing | "OTP has expired or was never requested" |
| `OTP_RATE_LIMITED` | Hit `maxSendsPerWindow` | Use server message — "Too many OTP requests. Please try again later." |
| `TOKEN_EXPIRED` | Access/refresh token expired | "Your session expired. Please sign in again." (only shown when refresh itself fails) |
| `TOKEN_INVALID` | Malformed / bad signature | "Please sign in again." |
| `UNAUTHORIZED` | Not authenticated (or deactivated) | Use server message if present, else "Please sign in again." |
| `FORBIDDEN` | Wrong role or wrong tenant | "You don't have permission for this action." |
| `VALIDATION_ERROR` | Bean Validation or domain rule | Server message (domain) or field-level via `details.fieldErrors` |
| `REQUIRED_FIELD_MISSING` | Missing `@RequestParam` | "Please complete all required fields." (developer error; log) |
| `INVALID_PHONE` | Phone failed normalisation | Server message — "Phone must be a valid 10-digit Indian mobile" |
| `INVALID_DATE` | Invalid date payload | "Please enter a valid date." |
| `INVALID_FILE_TYPE` | Bad upload MIME | "That file type isn't supported." |
| `FILE_TOO_LARGE` | Exceeds multipart limit | "File is too large. Please choose a smaller one." |
| `SCHOOL_NOT_FOUND` | Bad `tenantId` | "School not found." |
| `ACADEMIC_YEAR_NOT_FOUND` | Bad academic year id | "Academic year not found." |
| `CLASS_NOT_FOUND` | Bad class id | "Class not found." |
| `SECTION_NOT_FOUND` | Bad section id | "Section not found." |
| `SECTION_NOT_ASSIGNED` | Teacher not on section | "You are not assigned to this section." |
| `STUDENT_NOT_FOUND` | Bad student id | "Student not found." |
| `DUPLICATE_ADMISSION_NUMBER` | Admission no. in use | Server message |
| `PARENT_NOT_FOUND` | Bad parent id | "Parent record not found." |
| `ATTENDANCE_ALREADY_SUBMITTED` | Day already marked | "Attendance for this date has already been submitted." |
| `ATTENDANCE_RECORD_NOT_FOUND` | Bad attendance id | "Attendance record not found." |
| `INVOICE_NOT_FOUND` | Bad invoice id | "Invoice not found." |
| `PAYMENT_AMOUNT_EXCEEDS_DUE` | Amount > outstanding | Server message ("Payment exceeds the outstanding balance") |
| `RECEIPT_NOT_FOUND` | Receipt not yet generated | "Receipt not available yet." |
| `EXAM_NOT_FOUND` | Bad exam id | "Exam not found." |
| `MARKS_ALREADY_FINALIZED` | Marks sheet locked | Server message — "Marks for this exam/section have been finalized" |
| `MAX_MARKS_EXCEEDED` | Mark > max | Server message (includes the offending row) |
| `REPORT_CARD_NOT_READY` | Not all marks entered | Server message |
| `WHATSAPP_SEND_FAILED` | WATI delivery failed | "WhatsApp message couldn't be sent. Retrying in the background." |
| `TEMPLATE_NOT_APPROVED` | WhatsApp template pending | "This template is pending WhatsApp approval." |
| `MIGRATION_JOB_NOT_FOUND` | Bad job id | "Migration job not found." |
| `MIGRATION_JOB_IN_WRONG_STATE` | State transition invalid | Server message |
| `RESOURCE_NOT_FOUND` | Generic 404 fallback | "We couldn't find what you were looking for." |
| `RATE_LIMIT_EXCEEDED` | Too many requests | "You're going a bit too fast. Please wait a moment." |
| `EXTERNAL_SERVICE_ERROR` | Downstream 5xx / timeout | "A downstream service is temporarily unavailable. Please try again." |
| `INTERNAL_ERROR` | Unhandled exception | "Something went wrong. Please try again." |
| `NETWORK_ERROR` | No response (client-synth) | "We can't reach the server. Check your connection." |

---

## 8. 401 vs. 403 — the mental model

Both are about "can you do this?" but they fire at different layers and the UI reaction is different.

- **401** (`TOKEN_EXPIRED`, `TOKEN_INVALID`, `UNAUTHORIZED`) — "we don't know who you are." The interceptor owns this. Refresh if `TOKEN_EXPIRED`, redirect to `/login` otherwise. **Never shows an inline error** on the page.
- **403** (`FORBIDDEN`) — "we know who you are, but you can't do this." Two flavours:
  1. **Tenant mismatch** (`TenantInterceptor` → `TenantContext.validateTenant`). Rare — should be caught by the route guard first. If it fires, the user tampered with the URL or their tab has stale state. Redirect to `/login`.
  2. **Role denial** (`@PreAuthorize` failure → `GlobalExceptionHandler.handleAccessDenied`). Treat this as a bug in the UI — the button should have been hidden per [05-role-matrix.md](05-role-matrix.md). Show an "Access denied" toast/page and log to analytics.

Neither 401 nor 403 should be auto-retried.

---

## 9. Request IDs (for 500s)

The backend [SecurityConfig](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java) exposes the `X-Request-Id` header. Propagate it in your error display:

```ts
// In the axios response interceptor
const requestId = response.headers["x-request-id"];
// Attach to ApiError.details for surface-level display
```

When a user reports an `INTERNAL_ERROR`, the request id lets the backend team pinpoint the log entry. Show it under the generic error message on the 500 screen (`"If this keeps happening, quote request id: <id>"`).

---

## 10. Error-handling checklist

- [ ] All HTTP calls go through `apiClient` — no bare `fetch` / `axios` that skips the interceptor.
- [ ] Envelope unwrap: handler receives `data: T` directly, not `{ success, data }`.
- [ ] Every mutation error goes through `applyServerFieldErrors` → toast fallback.
- [ ] Every query error that blocks rendering uses `<ErrorBanner>` with a `Retry` button.
- [ ] `useApiError()` (or equivalent) centralises redirect decisions for 401 / 403.
- [ ] React Query retry policy: 3× with exp back-off for transient codes only.
- [ ] Mutations never auto-retry.
- [ ] `VALIDATION_ERROR` details are mapped to form fields before fallback.
- [ ] Toast copy comes from `friendlyMessage(error)` — no ad-hoc strings in components.
- [ ] 500 screen shows `X-Request-Id` if available.
- [ ] In dev, show `error.code` under the banner; strip in prod.
- [ ] No `catch {}` swallowing — always `toast` or rethrow.
- [ ] Never retry an auth/validation/domain error. Ever.
