/**
 * The closed set of backend error codes, derived from ErrorCode.java + slice 1/4 additions.
 * Match on `code` (stable) not `message` (human-readable).
 */
export type ErrorCodeName =
  // Auth
  | 'PHONE_NOT_FOUND' | 'OTP_INVALID' | 'OTP_EXPIRED' | 'OTP_RATE_LIMITED'
  | 'TOKEN_EXPIRED'  | 'TOKEN_INVALID' | 'UNAUTHORIZED' | 'FORBIDDEN'
  // Validation
  | 'VALIDATION_ERROR' | 'REQUIRED_FIELD_MISSING' | 'INVALID_PHONE'
  | 'INVALID_DATE' | 'INVALID_FILE_TYPE' | 'FILE_TOO_LARGE'
  // School / config
  | 'SCHOOL_NOT_FOUND' | 'ACADEMIC_YEAR_NOT_FOUND' | 'CLASS_NOT_FOUND'
  | 'SECTION_NOT_FOUND' | 'SECTION_NOT_ASSIGNED'
  // Student / parent
  | 'STUDENT_NOT_FOUND' | 'DUPLICATE_ADMISSION_NUMBER' | 'PARENT_NOT_FOUND'
  // Attendance
  | 'ATTENDANCE_ALREADY_SUBMITTED' | 'ATTENDANCE_RECORD_NOT_FOUND'
  // Fee
  | 'INVOICE_NOT_FOUND' | 'PAYMENT_AMOUNT_EXCEEDS_DUE' | 'RECEIPT_NOT_FOUND'
  // Academics
  | 'EXAM_NOT_FOUND' | 'MARKS_ALREADY_FINALIZED' | 'MAX_MARKS_EXCEEDED'
  | 'REPORT_CARD_NOT_READY'
  // Communication
  | 'WHATSAPP_SEND_FAILED' | 'TEMPLATE_NOT_APPROVED'
  // Migration / OCR
  | 'MIGRATION_JOB_NOT_FOUND' | 'MIGRATION_JOB_IN_WRONG_STATE'
  // SaaS / billing (slice 1)
  | 'FEATURE_DISABLED' | 'PLAN_LIMIT_EXCEEDED' | 'TENANT_SUSPENDED'
  // General
  | 'RESOURCE_NOT_FOUND' | 'RATE_LIMIT_EXCEEDED' | 'EXTERNAL_SERVICE_ERROR'
  | 'INTERNAL_ERROR'
  // Client-synthetic
  | 'NETWORK_ERROR';

/** Codes for which auto-retry is potentially safe. */
const TRANSIENT_CODES: ReadonlySet<ErrorCodeName> = new Set([
  'RATE_LIMIT_EXCEEDED',
  'EXTERNAL_SERVICE_ERROR',
  'NETWORK_ERROR',
]);

export class ApiError extends Error {
  constructor(
    public readonly code: ErrorCodeName,
    message: string,
    public readonly httpStatus: number,
    public readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isTransient(): boolean {
    return TRANSIENT_CODES.has(this.code);
  }

  static from(
    err: { code?: string; message?: string; details?: Record<string, unknown> } | undefined,
    httpStatus: number,
  ): ApiError {
    const code = (err?.code ?? 'INTERNAL_ERROR') as ErrorCodeName;
    const message = err?.message ?? 'Unexpected error';
    return new ApiError(code, message, httpStatus, err?.details);
  }

  static network(cause: unknown): ApiError {
    const message = cause instanceof Error
      ? `Network error: ${cause.message}`
      : 'Network error';
    return new ApiError('NETWORK_ERROR', message, 0);
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

export function hasCode<C extends ErrorCodeName>(err: unknown, code: C): err is ApiError & { code: C } {
  return isApiError(err) && err.code === code;
}
