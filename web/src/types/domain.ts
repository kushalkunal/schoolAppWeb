/**
 * Domain DTOs — derived 1:1 from the backend Java records in docs/frontend/08-typescript-dto-reference.md.
 * Field names match the JSON wire shape exactly.
 */

import type { StaffRole } from '@/auth/jwt';

export type Board = 'CBSE' | 'ICSE' | 'STATE' | 'IGCSE' | 'OTHER';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'LEAVE';
export type PaymentMode = 'CASH' | 'ONLINE' | 'CHEQUE' | 'DD' | 'BANK_TRANSFER';
export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED';

// ---------- School ----------
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
  createdAt: string;
}

export interface UpdateSchoolRequest {
  name?: string;
  principalName?: string;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
  whatsappNumber?: string;
}

export interface OnboardingStatusResponse {
  percentComplete: number;
  steps: { key: string; label: string; complete: boolean }[];
  pendingStepKeys: string[];
}

// ---------- Class / Section ----------
export interface SectionResponse {
  id: string;
  classId: string;
  name: string;
  classTeacherId: string | null;
  maxStrength: number | null;
}

export interface ClassResponse {
  id: string;
  name: string;
  sortOrder: number;
  sections: SectionResponse[];
}

export interface CreateClassesRequest {
  classes: { name: string; sections: string[]; sortOrder?: number }[];
}

// ---------- Staff ----------
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

export interface CreateStaffRequest {
  firstName: string;
  lastName?: string;
  phone: string;
  email?: string;
  role: StaffRole;
}

// ---------- Student ----------
export type ParentRelation = 'FATHER' | 'MOTHER' | 'GUARDIAN';

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

export interface CreateStudentRequest {
  firstName: string;
  lastName?: string;
  sectionId: string;
  parentPhone: string;
  parentName?: string;
  parentEmail?: string;
  parentRelation?: ParentRelation;
  admissionNumber?: string;
  gender?: string;
  dateOfBirth?: string;
  bloodGroup?: string;
  address?: string;
}

export interface StudentProfileResponse {
  student: StudentResponse;
  currentEnrollment: {
    enrollmentId: string;
    academicYearId: string;
    academicYearName: string;
    sectionId: string;
    className: string;
    sectionName: string;
    rollNumber: number | null;
    status: string;
  } | null;
  parents: {
    id: string;
    name: string | null;
    phone: string | null;
    email: string | null;
    relation: ParentRelation | null;
    primary: boolean;
  }[];
  siblings: StudentResponse[];
}

// ---------- Attendance ----------
export interface AttendanceEntry {
  studentId: string;
  status: AttendanceStatus;
  arrivalTime?: string | null;
  note?: string | null;
}

export interface SubmitAttendanceRequest {
  date: string;
  entries: AttendanceEntry[];
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

export interface UnmarkedSectionResponse {
  sectionId: string;
  sectionName: string;
  className: string;
  classTeacherId: string | null;
  classTeacherName: string | null;
  date: string;
}

// ---------- Fee ----------
export interface QuickCollectRequest {
  studentId: string;
  amountPaise: number;
  paymentMode: PaymentMode;
  feeHeadId?: string;
  invoiceId?: string;
  paymentDate?: string;
  notes?: string;
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
  outstandingBalancePaise: number;
  createdAt: string;
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

// ---------- Communication ----------
export type CircularTargetType = 'ALL_PARENTS' | 'CLASSES' | 'SECTIONS' | 'STUDENTS';

export interface CreateCircularRequest {
  title: string;
  body: string;
  targetType: CircularTargetType;
  targetIds?: string[];
  language?: string;
}

export interface CircularResponse {
  id: string;
  title: string;
  body: string;
  targetType: string;
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

// ---------- Academics ----------
export type ExamType = 'UNIT_TEST' | 'TERM' | 'ANNUAL' | 'MOCK' | 'ACTIVITY';

export interface SubjectResponse {
  id: string;
  name: string;
  code: string | null;
}

export interface CreateSubjectsRequest {
  subjects: { name: string; code?: string }[];
}

export interface CreateExamRequest {
  name: string;
  examType: ExamType;
  startDate?: string;  // ISO yyyy-MM-dd
  endDate?: string;
}

export interface ExamResponse {
  id: string;
  academicYearId: string;
  name: string;
  examType: ExamType;
  startDate: string | null;
  endDate: string | null;
  published: boolean;
}

export interface StudentRow {
  studentId: string;
  displayName: string;
  admissionNumber: string;
  rollNumber: number | null;
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
  students: StudentRow[];
  existingMarks: MarkResponse[];
}

export interface MarkEntryDto {
  studentId: string;
  subjectId: string;
  maxMarks: number;
  obtainedMarks?: number | null;
  absent: boolean;
}

export interface BulkMarksRequest {
  sectionId: string;
  entries: MarkEntryDto[];
  submitFinal: boolean;
}

export interface ReportCardResponse {
  id: string;
  studentId: string;
  examId: string;
  totalMarks: number;
  obtainedMarks: number;
  percentage: number;
  grade: string | null;
  rankInClass: number | null;
  pdfUrl: string | null;
  waSentAt: string | null;
}

export interface ExamCompletionStatusResponse {
  examId: string;
  sectionId: string;
  totalStudents: number;
  studentsWithAllMarks: number;
  studentsPending: number;
  percentComplete: number;
}

// ---------- Platform admin ----------
export interface PlatformTenantSummary {
  schoolId: string;
  name: string;
  state: string;
  board: string | null;
  phone: string | null;
  email: string | null;
  active: boolean;
  subscriptionId: string | null;
  planCode: string | null;
  status: SubscriptionStatus | null;
  trialEndsAt: string | null;
  createdAt: string;
}

export interface PlatformTenantDetail {
  summary: PlatformTenantSummary;
  features: Record<string, boolean>;
  usage: Record<string, number>;
  limits: Record<string, number>;
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  description: string | null;
  monthlyPricePaise: number;
  active: boolean;
  sortOrder: number;
}

export interface Feature {
  featureKey: string;
  name: string;
  description: string | null;
  defaultEnabled: boolean;
  category: string | null;
}

export interface TenantProviderConfig {
  id: string;
  schoolId: string;
  concern: 'WHATSAPP' | 'EMAIL' | 'PAYMENT' | 'STORAGE' | 'OCR' | 'LLM';
  provider: string;
  config: Record<string, unknown>;
  active: boolean;
  verifiedAt: string | null;
  lastError: string | null;
  note: string | null;
}

// ---------- AI / Risk (Slice 19+23) ----------

export type RiskFactor = 'ATTENDANCE' | 'FEE' | 'MARKS';
export type MarksTrendValue = 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN';

export interface StudentRiskScore {
  id: string;
  schoolId: string;
  studentId: string;
  score: number;
  attendancePct: number | null;
  feeOutstandingPaise: number;
  marksTrend: MarksTrendValue | null;
  topFactor: RiskFactor | null;
  calculatedAt: string;
  /** 2-3 sentence LLM-written narrative; falls back to a templated string. */
  summary: string | null;
}

// ---------- Admissions (Slice 17+23) ----------

export type AdmissionStatus =
  | 'ENQUIRY' | 'APPLICATION_SUBMITTED' | 'TEST_SCHEDULED' | 'TEST_COMPLETED'
  | 'OFFERED' | 'ACCEPTED' | 'DECLINED' | 'ENROLLED' | 'WITHDRAWN' | 'REJECTED';

export interface AdmissionTestScoreDto {
  subjectName: string;
  maxMarks: number;
  obtainedMarks: number;
  remarks: string | null;
}

export interface AdmissionResponse {
  id: string;
  status: AdmissionStatus;
  parentName: string | null;
  parentPhone: string;
  parentEmail: string | null;
  studentFirstName: string;
  studentLastName: string | null;
  studentDisplayName: string;
  studentDateOfBirth: string | null;
  studentGender: string | null;
  intendedClass: string;
  intendedSection: string | null;
  intendedAcademicYear: string | null;
  source: string | null;
  referrerName: string | null;
  notes: string | null;
  testScheduledAt: string | null;
  testVenue: string | null;
  testTotalMarks: number | null;
  testObtainedMarks: number | null;
  testRemarks: string | null;
  testScores: AdmissionTestScoreDto[];
  offerLetterUrl: string | null;
  offerIssuedAt: string | null;
  offerAcceptedAt: string | null;
  offerDeclinedAt: string | null;
  declineReason: string | null;
  enrolledStudentId: string | null;
  enrolledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EnquiryRequest {
  parentName?: string;
  parentPhone: string;
  parentEmail?: string;
  studentFirstName: string;
  studentLastName?: string;
  studentDateOfBirth?: string;
  studentGender?: string;
  intendedClass: string;
  intendedSection?: string;
  intendedAcademicYear?: string;
  source?: string;
  referrerName?: string;
  notes?: string;
}

// ---------- HR (Slice 16+25) ----------

export type StaffAttendanceStatus =
  | 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'LEAVE' | 'HOLIDAY' | 'LATE';

export interface StaffAttendanceRequest {
  staffId: string;
  date: string;            // ISO yyyy-MM-dd
  status: StaffAttendanceStatus;
  notes?: string;
}

export interface StaffAttendanceResponse {
  id: string | null;
  staffId: string;
  date: string;
  status: StaffAttendanceStatus;
  notes: string | null;
}

export interface StaffMonthlySummaryResponse {
  staffId: string;
  year: number;
  month: number;
  totalCalendarDays: number;
  counts: Partial<Record<StaffAttendanceStatus, number>>;
  workingDays: number;
}

export type LeaveType =
  | 'CASUAL' | 'SICK' | 'EARNED' | 'UNPAID'
  | 'MATERNITY' | 'PATERNITY' | 'COMP_OFF' | 'OTHER';

export type LeaveStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LeaveApplicationRequest {
  staffId: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  days: number;
  reason?: string;
}

export interface LeaveDecisionRequest {
  approve: boolean;
  note?: string;
}

export interface LeaveApplicationResponse {
  id: string;
  staffId: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  days: number;
  reason: string | null;
  status: LeaveStatus;
  decidedById: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface PayslipResponse {
  id: string;
  staffId: string;
  year: number;
  month: number;
  version: number;
  workingDays: number;
  leaveDaysUnpaid: number;
  grossPaise: number;
  deductionsPaise: number;
  netPaise: number;
  breakdown: Record<string, unknown>;
  pdfUrl: string | null;
  generatedAt: string;
}
