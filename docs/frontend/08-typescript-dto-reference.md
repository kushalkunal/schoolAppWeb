# 08 — TypeScript DTO Reference

Copy-pasteable TypeScript types derived 1:1 from the Java DTOs under [backend/src/main/java/in/schoolapp/**/dto/](../../backend/src/main/java/in/schoolapp/) plus relevant entity enums. Generated 2026-04-23.

## Mapping rules

| Java | TypeScript |
|---|---|
| `UUID` | `string` (UUID format) |
| `String` | `string` |
| `OffsetDateTime` / `LocalDateTime` | `string` (ISO-8601, UTC) |
| `LocalDate` | `string` (`YYYY-MM-DD`) |
| `int`, `Integer`, `long`, `Long` | `number` |
| `BigDecimal` | `number` (see note below) |
| `boolean`, `Boolean` | `boolean` |
| `List<X>` | `X[]` |
| `Map<String, Object>` | `Record<string, unknown>` |
| Java `enum` | string-literal union |
| `@JsonInclude(NON_NULL)` field | `T \| null` (the server omits null fields; assume optional) |

**`BigDecimal` note**: the server sends these as JSON numbers. JavaScript's `number` loses precision for more than 15 significant digits, but no BigDecimal field in this codebase exceeds that (percentages, marks, amount-in-rupees for display purposes). Monetary fields are always `paise` as `long` → `number`. If you need arithmetic on percentages, round at the boundary.

**Currency**: every `*Paise` field is an integer count of paise. `₹1.00 = 100 paise`. Divide by 100 for rupees; format with `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`.

**Dates**: `OffsetDateTime` is always UTC with offset `Z`. `LocalDate` is date-only (no timezone). Parse at the boundary with `date-fns.parseISO` / `parse`.

---

## 1. Shared envelope + meta

```ts
/** Canonical API response envelope. Null fields are omitted on the wire. */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  meta?: Meta;
}

export interface ApiError {
  code: ErrorCode;
  message: string;
  details?: Record<string, unknown>;
}

export interface Meta {
  total?: number;      // total rows across all pages
  page?: number;       // 0-indexed
  limit?: number;      // size per page
  nextCursor?: string; // opaque — present for cursor-based endpoints
}

/** The ApiResponse envelope is auto-unwrapped by the shared axios client.
 *  Use the `*Response` / `*Dto` types directly in your hooks. */
```

### Error codes

Full list in [03-error-handling.md](03-error-handling.md); enumerated here for the type union.

```ts
export type ErrorCode =
  // Auth
  | 'PHONE_NOT_FOUND'
  | 'OTP_INVALID'
  | 'OTP_EXPIRED'
  | 'OTP_RATE_LIMITED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  // Validation
  | 'VALIDATION_ERROR'
  | 'REQUIRED_FIELD_MISSING'
  | 'INVALID_PHONE'
  | 'INVALID_DATE'
  | 'INVALID_FILE_TYPE'
  | 'FILE_TOO_LARGE'
  // School / config
  | 'SCHOOL_NOT_FOUND'
  | 'ACADEMIC_YEAR_NOT_FOUND'
  | 'CLASS_NOT_FOUND'
  | 'SECTION_NOT_FOUND'
  | 'SECTION_NOT_ASSIGNED'
  // Student / parent
  | 'STUDENT_NOT_FOUND'
  | 'DUPLICATE_ADMISSION_NUMBER'
  | 'PARENT_NOT_FOUND'
  // Attendance
  | 'ATTENDANCE_ALREADY_SUBMITTED'
  | 'ATTENDANCE_RECORD_NOT_FOUND'
  // Fee
  | 'INVOICE_NOT_FOUND'
  | 'PAYMENT_AMOUNT_EXCEEDS_DUE'
  | 'RECEIPT_NOT_FOUND'
  // Academics
  | 'EXAM_NOT_FOUND'
  | 'MARKS_ALREADY_FINALIZED'
  | 'MAX_MARKS_EXCEEDED'
  | 'REPORT_CARD_NOT_READY'
  // Communication
  | 'WHATSAPP_SEND_FAILED'
  | 'TEMPLATE_NOT_APPROVED'
  // Migration / OCR
  | 'MIGRATION_JOB_NOT_FOUND'
  | 'MIGRATION_JOB_IN_WRONG_STATE'
  // General
  | 'RESOURCE_NOT_FOUND'
  | 'RATE_LIMIT_EXCEEDED'
  | 'EXTERNAL_SERVICE_ERROR'
  | 'INTERNAL_ERROR';
```

---

## 2. Shared enums (cross-module)

```ts
/** Staff role — matches the JWT role claim (without the ROLE_ prefix Spring Security uses). */
export type StaffRole =
  | 'SUPER_ADMIN'
  | 'SCHOOL_OWNER'
  | 'PRINCIPAL'
  | 'ADMIN'
  | 'CLASS_TEACHER'
  | 'SUBJECT_TEACHER'
  | 'ACCOUNTANT'
  | 'VIEWER';

export type Board = 'CBSE' | 'ICSE' | 'STATE' | 'IGCSE' | 'OTHER';

export type SignupChannel = 'PHONE' | 'EMAIL' | 'BOTH';

export type IdentifierType = 'PHONE' | 'EMAIL';

export type ExportFormat = 'CSV' | 'XLSX';

/** Audit-log verb. */
export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'ACTION';
```

---

## 3. Auth module

Sources: [AuthController](../../backend/src/main/java/in/schoolapp/auth/AuthController.java), [auth/dto/*.java](../../backend/src/main/java/in/schoolapp/auth/dto/).

```ts
/** POST /api/v1/auth/otp/send — exactly one of phone/email required. */
export interface SendOtpRequest {
  phone?: string;     // 10-digit IN mobile (6|7|8|9 prefix)
  email?: string;     // max 255
}

export interface VerifyOtpRequest {
  phone?: string;
  email?: string;
  otp: string;        // exactly 6 digits
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;  // access token ttl
  user: StaffResponse;
}
```

---

## 4. School module

Sources: [school/dto/*.java](../../backend/src/main/java/in/schoolapp/school/dto/), [school/entity/*.java](../../backend/src/main/java/in/schoolapp/school/entity/).

```ts
/** POST /api/v1/tenants — public signup. */
export interface CreateSchoolRequest {
  schoolName: string;        // required, max 255
  principalName: string;     // required, max 255
  phone?: string;            // required-ish per app.signup.channel
  email?: string;            // max 255
  state: string;             // required, max 100
  board: Board;              // required
  city?: string;             // max 100
}

/** PUT /api/v1/tenants/{tenantId} — patch-style, all optional. */
export interface UpdateSchoolRequest {
  name?: string;             // max 255
  principalName?: string;    // max 255
  address?: string;
  city?: string;             // max 100
  state?: string;            // max 100
  pincode?: string;          // max 10
  whatsappNumber?: string;   // max 15
}

export interface SchoolResponse {
  id: string;
  name: string;
  principalName: string;
  phone: string | null;
  email: string | null;
  city: string | null;
  state: string;
  board: Board;
  active: boolean;
  waConfigured: boolean;
  createdAt: string;         // ISO-8601 UTC
}

export interface SchoolSignupResponse {
  school: SchoolResponse;
  principal: StaffResponse;
  academicYear: AcademicYearResponse;
  nextStep: string;          // e.g. "SEND_OTP_PHONE"
}

export interface OnboardingStatusResponse {
  percentComplete: number;
  steps: OnboardingStepStatus[];
  pendingStepKeys: string[];
}
export interface OnboardingStepStatus {
  key: string;
  label: string;
  complete: boolean;
}

export interface AcademicYearResponse {
  id: string;
  name: string;              // e.g. "2025-26"
  startDate: string;         // YYYY-MM-DD
  endDate: string;
  current: boolean;
}

export interface SectionResponse {
  id: string;
  classId: string;
  name: string;              // e.g. "A"
  classTeacherId: string | null;
  maxStrength: number | null;
}

export interface ClassResponse {
  id: string;
  name: string;              // e.g. "Class 6"
  sortOrder: number;
  sections: SectionResponse[];
}

export interface CreateClassesRequest {
  classes: ClassSpec[];      // non-empty
}
export interface ClassSpec {
  name: string;              // required, max 50
  sections: string[];        // non-empty, each max 10
  sortOrder?: number;
}

/** POST /api/v1/tenants/{tenantId}/staff */
export interface CreateStaffRequest {
  firstName: string;         // required, max 100
  lastName?: string;         // max 100
  phone: string;             // required
  email?: string;            // max 255
  role: StaffRole;           // required
}

export interface StaffResponse {
  id: string;
  schoolId: string;
  firstName: string;
  lastName: string | null;
  displayName: string;
  phone: string | null;
  email: string | null;
  role: StaffRole;
  active: boolean;
}

/** Substitute teacher assignment (single-day). */
export interface CreateSubstituteRequest {
  absentTeacherId: string;
  substituteId: string;
  sectionId: string;
  assignedDate: string;      // YYYY-MM-DD
  note?: string;
}

export interface SubstituteResponse {
  id: string;
  absentTeacherId: string;
  substituteId: string;
  sectionId: string;
  assignedDate: string;
  note: string | null;
  createdById: string | null;
  createdAt: string;
}
```

---

## 5. Student module

Sources: [student/dto/*.java](../../backend/src/main/java/in/schoolapp/student/dto/), [student/entity/*.java](../../backend/src/main/java/in/schoolapp/student/entity/).

```ts
export type ParentRelation = 'FATHER' | 'MOTHER' | 'GUARDIAN';

export type EnrollmentStatus = 'ACTIVE' | 'LEFT' | 'GRADUATED';

export type StudentDocumentType =
  | 'ADMISSION_FORM'
  | 'BIRTH_CERT'
  | 'TC'
  | 'PHOTO'
  | 'MEDICAL'
  | 'OTHER';

/** POST /api/v1/tenants/{tenantId}/students — minimum viable payload. */
export interface CreateStudentRequest {
  firstName: string;            // required, max 100
  lastName?: string;            // max 100
  sectionId: string;            // required
  parentPhone: string;          // required (WhatsApp-capable)
  parentName?: string;          // max 200
  parentEmail?: string;         // max 255
  parentRelation?: ParentRelation;
  admissionNumber?: string;     // max 50
  gender?: string;              // max 10
  dateOfBirth?: string;         // YYYY-MM-DD
  bloodGroup?: string;          // max 5
  address?: string;
}

/** PUT /api/v1/tenants/{tenantId}/students/{studentId} — patch-style. */
export interface UpdateStudentRequest {
  firstName?: string;           // max 100
  lastName?: string;            // max 100
  gender?: string;              // max 10
  dateOfBirth?: string;
  bloodGroup?: string;          // max 5
  address?: string;
}

export interface StudentResponse {
  id: string;
  firstName: string;
  lastName: string | null;
  displayName: string;
  admissionNumber: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  bloodGroup: string | null;
  photoUrl: string | null;
  active: boolean;
}

export interface StudentProfileResponse {
  student: StudentResponse;
  currentEnrollment: EnrollmentSummary | null;
  parents: ParentDto[];
  siblings: StudentResponse[];
}

export interface EnrollmentSummary {
  enrollmentId: string;
  academicYearId: string;
  academicYearName: string;
  sectionId: string;
  className: string;
  sectionName: string;
  rollNumber: number | null;
  status: string;               // EnrollmentStatus name
}

export interface ParentDto {
  id: string;
  name: string | null;
  phone: string | null;
  email: string | null;
  relationType: string | null;  // free-form legacy
  relation: ParentRelation | null;
  primary: boolean;
}

export interface FamilyViewResponse {
  primaryParentId: string;
  primaryParentName: string | null;
  primaryParentPhone: string | null;
  primaryParentEmail: string | null;
  children: FamilyMember[];
}
export interface FamilyMember {
  studentId: string;
  displayName: string;
  sectionId: string;
  className: string;
  sectionName: string;
}

export interface StudentDocumentResponse {
  id: string;
  studentId: string;
  docType: StudentDocumentType | null;
  fileUrl: string;
  fileName: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  createdAt: string;
}

export interface StudentTimelineResponse {
  studentId: string;
  studentName: string;
  enrollments: TimelineEnrollmentEntry[];
  reportCards: TimelineReportCardEntry[];
  attendanceSummaries: AttendanceYearSummary[];
}

export interface TimelineEnrollmentEntry {
  enrollmentId: string;
  academicYearId: string;
  academicYearName: string;
  sectionId: string;
  sectionName: string;
  className: string;
  rollNumber: number | null;
  status: string;
  createdAt: string;
}

export interface TimelineReportCardEntry {
  reportCardId: string;
  examId: string;
  examName: string;
  percentage: number | null;
  grade: string | null;
  rankInClass: number | null;
  createdAt: string;
}

export interface AttendanceYearSummary {
  academicYearId: string;
  academicYearName: string;
  windowFrom: string;           // YYYY-MM-DD
  windowTo: string;
  totalDays: number;
  absentDays: number;
  attendancePct: number | null;
}
```

---

## 6. Attendance module

Sources: [attendance/dto/*.java](../../backend/src/main/java/in/schoolapp/attendance/dto/), [attendance/entity/AttendanceStatus.java](../../backend/src/main/java/in/schoolapp/attendance/entity/AttendanceStatus.java).

```ts
export type AttendanceStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'LATE'
  | 'HALF_DAY'
  | 'LEAVE';

/** POST /api/v1/tenants/{tenantId}/sections/{sectionId}/attendance */
export interface SubmitAttendanceRequest {
  date: string;                 // YYYY-MM-DD, past or present
  entries: AttendanceEntryDto[];
}

export interface AttendanceEntryDto {
  studentId: string;
  status: AttendanceStatus;
  arrivalTime?: string | null;  // ISO-8601 UTC
  note?: string | null;
}

export interface AttendanceRecordResponse {
  id: string;
  studentId: string;
  sectionId: string;
  date: string;
  status: AttendanceStatus;
  arrivalTime: string | null;
  note: string | null;
}

export interface AttendanceSubmitResponse {
  date: string;
  sectionId: string;
  total: number;
  present: number;
  absent: number;
  late: number;
  halfDay: number;
  onLeave: number;
  records: AttendanceRecordResponse[];
  notificationsQueued: number;
}

export interface AttendanceSummaryResponse {
  date: string;
  totalMarked: number;
  present: number;
  absent: number;
  late: number;
  halfDay: number;
  leave: number;
}

export interface ChronicAbsenteeResponse {
  studentId: string;
  studentName: string;
  sectionId: string;
  sectionName: string;
  className: string;
  absentDays: number;
  windowDays: number;
}

export interface UnmarkedSectionResponse {
  sectionId: string;
  sectionName: string;
  className: string;
  classTeacherId: string | null;
  classTeacherName: string | null;
  date: string;
}
```

---

## 7. Fee module

Sources: [fee/dto/*.java](../../backend/src/main/java/in/schoolapp/fee/dto/), [fee/entity/*.java](../../backend/src/main/java/in/schoolapp/fee/entity/).

```ts
export type PaymentMode = 'CASH' | 'ONLINE' | 'CHEQUE' | 'DD' | 'BANK_TRANSFER';

export type InvoiceStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'WAIVED';

export type ReminderTriggerType = 'BEFORE_DUE' | 'ON_DUE' | 'AFTER_DUE';

/** POST /api/v1/tenants/{tenantId}/fees/payments — quick collect. */
export interface QuickCollectRequest {
  studentId: string;
  amountPaise: number;          // > 0
  paymentMode: PaymentMode;
  feeHeadId?: string;
  invoiceId?: string;           // if null, FIFO-applied to pending invoices
  paymentDate?: string;         // YYYY-MM-DD
  notes?: string;               // max 500
}

export interface CreateInvoiceRequest {
  studentId: string;
  feeHeadId?: string;
  amountDuePaise: number;       // > 0
  dueDate?: string;
  description?: string;         // max 500
}

export interface OpeningBalanceRequest {
  balances: OpeningBalanceEntry[];  // non-empty
}
export interface OpeningBalanceEntry {
  studentId: string;
  amountPaise: number;          // >= 0
  note?: string;                // max 500
}

export interface BulkReminderRequest {
  studentIds: string[];         // non-empty
  messageOverride?: string;
}

export interface PaymentResponse {
  id: string;
  studentId: string;
  invoiceId: string | null;
  amountPaise: number;
  paymentMode: PaymentMode;
  receiptNumber: string;
  receiptPdfUrl: string | null;
  paymentDate: string;
  outstandingBalancePaise: number;  // student-level, post this payment
  createdAt: string;
}

export interface InvoiceResponse {
  id: string;
  studentId: string;
  feeHeadId: string | null;
  amountDuePaise: number;
  amountPaidPaise: number;
  balancePaise: number;
  dueDate: string | null;
  status: InvoiceStatus;
  openingBalance: boolean;
  description: string | null;
}

export interface StudentFeeSummaryResponse {
  studentId: string;
  totalOutstandingPaise: number;
  totalPaidPaise: number;
  invoices: InvoiceResponse[];
  recentPayments: PaymentResponse[];
}

export interface FeeDashboardResponse {
  collectedTodayPaise: number;
  collectedThisMonthPaise: number;
  totalOutstandingPaise: number;
  totalOverduePaise: number;
  studentsWithDues: number;
  paymentsCollectedToday: number;
}

export interface DefaulterResponse {
  studentId: string;
  studentName: string;
  className: string;
  sectionName: string;
  outstandingPaise: number;
  oldestDueDate: string | null;
  invoiceCount: number;
  daysOverdue: number;
}

export interface CreateFeeHeadRequest {
  name: string;                 // required, max 100
}

export interface FeeHeadResponse {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
}

/** GET/POST/PUT /api/v1/tenants/{tenantId}/fee-reminder-schedules */
export interface FeeReminderScheduleDto {
  id?: string;                  // absent on create
  name: string;                 // required, max 100
  triggerType: ReminderTriggerType;
  daysOffset: number;           // >= 0
  includeUpiLink: boolean;
  active: boolean;
}
```

---

## 8. Academics module

Sources: [academics/dto/*.java](../../backend/src/main/java/in/schoolapp/academics/dto/), [academics/entity/ExamType.java](../../backend/src/main/java/in/schoolapp/academics/entity/ExamType.java).

```ts
export type ExamType = 'UNIT_TEST' | 'TERM' | 'ANNUAL' | 'MOCK' | 'ACTIVITY';

/** POST /api/v1/tenants/{tenantId}/subjects/bulk */
export interface CreateSubjectsRequest {
  subjects: SubjectSpec[];      // non-empty
}
export interface SubjectSpec {
  name: string;                 // required, max 100
  code?: string;                // max 20
}

export interface SubjectResponse {
  id: string;
  name: string;
  code: string | null;
}

export interface CreateExamRequest {
  name: string;                 // required, max 100
  examType?: ExamType;
  startDate?: string;
  endDate?: string;
}

export interface ExamResponse {
  id: string;
  academicYearId: string;
  name: string;
  examType: ExamType | null;
  startDate: string | null;
  endDate: string | null;
  published: boolean;
}

export interface MarkEntryDto {
  studentId: string;
  subjectId: string;
  maxMarks: number;             // BigDecimal, >= 0
  obtainedMarks?: number | null;
  absent: boolean;
}

export interface MarkResponse {
  id: string;
  examId: string;
  studentId: string;
  subjectId: string;
  maxMarks: number;
  obtainedMarks: number | null;
  absent: boolean;
  grade: string | null;
  draft: boolean;
}

export interface MarksEntrySheetResponse {
  examId: string;
  sectionId: string;
  students: MarksEntryStudentRow[];
  existingMarks: MarkResponse[];
}
export interface MarksEntryStudentRow {
  studentId: string;
  displayName: string;
  admissionNumber: string | null;
  rollNumber: number | null;
}

export interface ExamCompletionStatusResponse {
  examId: string;
  sectionId: string;
  totalStudents: number;
  perSubject: SubjectCompletion[];
}
export interface SubjectCompletion {
  subjectId: string;
  subjectName: string;
  enteredCount: number;
  totalStudents: number;
  complete: boolean;
}

export interface BulkMarksRequest {
  sectionId: string;
  entries: MarkEntryDto[];      // non-empty
  submitFinal: boolean;         // flips is_draft = false
}

export interface ReportCardResponse {
  id: string;
  studentId: string;
  examId: string;
  totalMarks: number | null;
  obtainedMarks: number | null;
  percentage: number | null;
  grade: string | null;
  rankInClass: number | null;
  pdfUrl: string | null;
  waSentAt: string | null;
}

export interface ExamEligibilityResponse {
  sectionId: string;
  windowFrom: string;
  windowTo: string;
  minAttendancePct: number;
  students: StudentEligibility[];
}
export interface StudentEligibility {
  studentId: string;
  studentName: string;
  rollNumber: number | null;
  markedDays: number;
  absentDays: number;
  attendancePct: number | null;
  eligible: boolean;
}

export interface CreateTeacherAssignmentRequest {
  staffId: string;
  subjectId: string;
  sectionId: string;
}

export interface TeacherAssignmentResponse {
  id: string;
  staffId: string;
  subjectId: string;
  sectionId: string;
  academicYearId: string;
  createdAt: string;
}
```

---

## 9. Analytics module

Sources: [analytics/dto/*.java](../../backend/src/main/java/in/schoolapp/analytics/dto/), [analytics/entity/*.java](../../backend/src/main/java/in/schoolapp/analytics/entity/).

```ts
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type AlertType =
  | 'CONSECUTIVE_ABSENCE'
  | 'ATTENDANCE_NOT_SUBMITTED'
  | 'FEE_COLLECTION_DROP'
  | 'AT_RISK_STUDENT'
  | 'OTHER';

export type RiskFactor = 'ATTENDANCE' | 'FEE' | 'MARKS';

export type MarksTrend = 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN';

export interface AlertResponse {
  id: string;
  alertType: AlertType | null;  // serialised as string
  severity: AlertSeverity | null;
  studentId: string | null;
  sectionId: string | null;
  title: string;
  description: string | null;
  actionUrl: string | null;
  dismissed: boolean;
  dismissedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

/** GET /api/v1/tenants/{tenantId}/dashboard */
export interface DashboardResponse {
  asOfDate: string;
  attendance: AttendanceSummaryResponse;
  alerts: DashboardAlertCounts;
  fees: DashboardFeeSnapshot;
  unmarkedSectionsCount: number;
  topAtRisk: DashboardAtRiskRow[];
}

export interface DashboardAlertCounts {
  total: number;
  high: number;
  critical: number;
}

export interface DashboardFeeSnapshot {
  mtdCollectedPaise: number;
  activeAtRiskCount: number;
}

export interface DashboardAtRiskRow {
  studentId: string;
  studentName: string;
  score: number;
  topFactor: string;           // serialised RiskFactor name
  marksTrend: string;          // serialised MarksTrend name
}
```

---

## 10. Audit module

Source: [audit/dto/AuditLogResponse.java](../../backend/src/main/java/in/schoolapp/audit/dto/AuditLogResponse.java).

```ts
export interface AuditLogResponse {
  id: string;
  entityType: string;
  entityId: string;
  action: AuditAction | null;
  oldValues: Record<string, unknown> | null;
  newValues: Record<string, unknown> | null;
  changedById: string | null;
  changedByRole: string | null;
  ipAddress: string | null;
  createdAt: string;
}

/** POST /api/v1/tenants/{tenantId}/data-deletion-requests */
export interface DataDeletionRequestBody {
  subjectType: 'STUDENT' | 'PARENT' | 'STAFF';
  subjectId: string;            // UUID as string
  reason?: string;
}

export interface DataDeletionResponse {
  requestId: string;
  status: 'RECORDED';
  message: string;
}
```

---

## 11. Communication module

Sources: [communication/dto/*.java](../../backend/src/main/java/in/schoolapp/communication/dto/), [communication/entity/*.java](../../backend/src/main/java/in/schoolapp/communication/entity/).

```ts
export type CircularTargetType =
  | 'ALL_PARENTS'
  | 'CLASSES'
  | 'SECTIONS'
  | 'STUDENTS';

export type NotificationStatus =
  | 'QUEUED'
  | 'SENT'
  | 'DELIVERED'
  | 'READ'
  | 'FAILED';

/** Categorises outbound WA templates. Internal-ish — surfaces via notification logs. */
export type MessageType =
  | 'ABSENCE_ALERT'
  | 'LATE_ARRIVAL_ALERT'
  | 'FEE_RECEIPT'
  | 'FEE_REMINDER'
  | 'CIRCULAR'
  | 'REPORT_CARD'
  | 'OTP'
  | 'EMERGENCY';

export interface InboxMessageResponse {
  id: string;
  parentId: string | null;
  studentId: string | null;
  routedToId: string | null;    // the staff member the message was routed to
  fromPhoneMasked: string;      // "98765****10" — full phone never exposed
  messageBody: string | null;
  read: boolean;
  resolved: boolean;
  receivedAt: string;
}

export interface CreateCircularRequest {
  title: string;                // required, max 255
  body: string;                 // required, max 4000
  targetType: CircularTargetType;
  targetIds?: string[];         // required for CLASSES/SECTIONS/STUDENTS
  language?: string;            // BCP-47, defaults "en"
}

export interface CircularResponse {
  id: string;
  title: string;
  body: string;
  targetType: string;           // serialised CircularTargetType name
  targetIds: string[];
  language: string | null;
  attachmentUrl: string | null;
  sentCount: number;
  deliveredCount: number;
  readCount: number;
  failedCount: number;
  sentAt: string | null;
  createdAt: string;
}
```

---

## 12. Payment (webhooks / provider-neutral)

Sources: [payment/dto/*.java](../../backend/src/main/java/in/schoolapp/payment/dto/).

These types are mostly internal — gateway webhooks deserialise into `PaymentEvent`, which fee-code listens for. The frontend typically only reads `PaymentResponse` (fee module). Listed for completeness.

```ts
export type PaymentStatus = 'PAID' | 'FAILED' | 'REFUNDED' | 'PENDING';

export interface PaymentLinkRequest {
  tenantId: string;
  studentId: string;
  amountPaise: number;
  currency: string;             // "INR", "USD", …
  purpose: string;              // short description shown to payer
  payer: PayerInfo;
  returnUrl: string | null;     // Stripe-only
  validFor: string;             // ISO-8601 duration (e.g. "PT2H")
}
export interface PayerInfo {
  name: string | null;
  phone: string | null;
  email: string | null;
}

export interface PaymentLink {
  providerReference: string;
  url: string;
  amountPaise: number;
  currency: string;
  expiresAt: string;
}

export interface PaymentEvent {
  providerReference: string;
  status: PaymentStatus;
  amountPaise: number;
  paymentMethod: string | null;  // "upi" | "card" | "netbanking" | …
  metadata: Record<string, unknown>;
}
```

---

## 13. Storage module

Source: [storage/dto/StoredFile.java](../../backend/src/main/java/in/schoolapp/storage/dto/StoredFile.java).

```ts
/** Returned by internal uploads — most frontend code sees the URL via domain DTOs
 *  (e.g. StudentResponse.photoUrl, PaymentResponse.receiptPdfUrl). */
export interface StoredFile {
  key: string;
  url: string;
  sizeBytes: number;
  contentType: string | null;
}
```

---

## 14. Migration module

Sources: [migration/dto/*.java](../../backend/src/main/java/in/schoolapp/migration/dto/), [migration/entity/*.java](../../backend/src/main/java/in/schoolapp/migration/entity/), [migration/llm/dto/*.java](../../backend/src/main/java/in/schoolapp/migration/llm/dto/), [migration/ocr/dto/*.java](../../backend/src/main/java/in/schoolapp/migration/ocr/dto/).

```ts
export type MigrationJobType =
  | 'FEE_RECEIPT'
  | 'ATTENDANCE'
  | 'MARKS'
  | 'ADMISSION_FORM';

export type MigrationJobStatus =
  | 'UPLOADED'
  | 'PROCESSING'
  | 'REVIEW'
  | 'COMMITTED'
  | 'FAILED';

export interface OcrResult {
  text: string;
  confidence: number;           // 0.0–1.0
}

export interface ExtractedRecord {
  studentName?: string | null;
  classHint?: string | null;    // "7B" etc.
  amountPaise?: number | null;
  receiptNumber?: string | null;
  date?: string | null;         // YYYY-MM-DD
  description?: string | null;
  confidence?: number | null;   // 0.0–1.0
  rawFields?: Record<string, unknown> | null;
}

export interface MatchCandidate {
  studentId: string;
  displayName: string;
  admissionNumber: string | null;
  confidence: number;
}

export interface MatchResult {
  matchedStudentId: string | null;
  topConfidence: number;
  candidates: MatchCandidate[];
}

export interface ReviewableRecord {
  rowIndex: number;             // stable client ref
  extracted: ExtractedRecord;
  matchedStudentId: string | null;
  matchConfidence: number;
  candidates: MatchCandidate[];
}

export interface MigrationJobResponse {
  id: string;
  jobType: MigrationJobType;
  status: MigrationJobStatus;
  imageUrl: string | null;
  recordCount: number | null;
  matchedCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  reviewableRecords: ReviewableRecord[] | null; // only when status = REVIEW
}

/** POST /api/v1/tenants/{tenantId}/migration/{jobId}/commit */
export interface CommitMigrationRequest {
  rows: ConfirmedRow[];         // non-empty
}

/** Flat per-row shape — per-type fields are nullable. Validator in the service
 *  layer enforces which fields are required per job type. */
export interface ConfirmedRow {
  rowIndex: number;
  /** Required for FEE_RECEIPT, ATTENDANCE, MARKS. Null for ADMISSION_FORM. */
  studentId?: string | null;

  // FEE_RECEIPT
  amountPaise?: number | null;
  paymentMode?: PaymentMode | null;
  paymentDate?: string | null;
  externalReceiptNumber?: string | null;

  // ATTENDANCE
  sectionId?: string | null;    // shared with ADMISSION_FORM
  attendanceDate?: string | null;
  attendanceStatus?: AttendanceStatus | null;

  // MARKS (one row = one subject mark)
  examId?: string | null;
  subjectId?: string | null;
  maxMarks?: number | null;
  obtainedMarks?: number | null;
  absent?: boolean | null;

  // ADMISSION_FORM
  firstName?: string | null;
  lastName?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  parentName?: string | null;
  parentPhone?: string | null;

  notes?: string | null;
}
```

---

## 15. Sync module (mobile app)

Source: [sync/dto/*.java](../../backend/src/main/java/in/schoolapp/sync/dto/). Mobile-only — listed so the same type library can serve both apps if needed.

```ts
/** GET /api/v1/tenants/{tenantId}/sync/pull */
export interface SyncPullResponse {
  serverTime: string;           // pass back as `since` next time
  academicYear: SyncAcademicYearDto | null;
  sections: SyncSectionDto[];
  students: SyncStudentDto[];
  studentCount: number;
}

export interface SyncAcademicYearDto {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface SyncSectionDto {
  id: string;
  classId: string;
  academicYearId: string;
  name: string;
  classTeacherId: string | null;
}

export interface SyncStudentDto {
  id: string;
  firstName: string;
  lastName: string | null;
  admissionNumber: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  active: boolean;
  updatedAt: string;
  currentSectionId: string | null;
}

/** POST /api/v1/tenants/{tenantId}/sync/push */
export interface SyncPushRequest {
  entries: SyncAttendanceEntry[]; // 1..1000
}

export interface SyncAttendanceEntry {
  localId: string;              // client-generated UUID, echoed in response
  sectionId: string;
  studentId: string;
  date: string;
  status: AttendanceStatus;
  arrivalTime?: string | null;
  note?: string | null;
}

export interface SyncPushResponse {
  accepted: number;
  rejected: number;
  results: SyncPushEntryResult[];
}

export interface SyncPushEntryResult {
  localId: string;
  serverId: string | null;      // null on rejection
  status: SyncPushEntryStatus;
  error: string | null;
}

export type SyncPushEntryStatus = 'CREATED' | 'UPDATED' | 'REJECTED';
```

---

## Cross-links

- Endpoints that consume / return each of these DTOs → [02-api-reference.md](02-api-reference.md)
- Role required to hit each mutating endpoint → [05-role-matrix.md](05-role-matrix.md)
- Error code values (`ErrorCode` union) → [03-error-handling.md](03-error-handling.md)
- Conventions for currency, dates, phones → [00-stack-and-conventions.md](00-stack-and-conventions.md)
