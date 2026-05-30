package in.schoolapp.common;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes returned in the API error envelope. Grouped by domain and mapped
 * to HTTP status. Extend freely — but never renumber or rename existing codes since clients
 * may switch on the string value.
 */
public enum ErrorCode {
    // ---------- Auth ----------
    PHONE_NOT_FOUND(HttpStatus.NOT_FOUND),
    OTP_INVALID(HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED(HttpStatus.UNAUTHORIZED),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    // Slice 35 — password login
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    PASSWORD_NOT_SET(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
    IDENTIFIER_NOT_VERIFIED(HttpStatus.UNAUTHORIZED),

    // ---------- Validation ----------
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST),
    INVALID_PHONE(HttpStatus.BAD_REQUEST),
    INVALID_DATE(HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    // ---------- School / config ----------
    SCHOOL_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACADEMIC_YEAR_NOT_FOUND(HttpStatus.NOT_FOUND),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SECTION_NOT_ASSIGNED(HttpStatus.FORBIDDEN),

    // ---------- Student / parent ----------
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_ADMISSION_NUMBER(HttpStatus.CONFLICT),
    PARENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    // Admissions (#14): section at capacity / a duplicate active application for the same child.
    SECTION_FULL(HttpStatus.CONFLICT),
    DUPLICATE_ADMISSION(HttpStatus.CONFLICT),
    // Visitor is not a registered guardian for the student they're collecting (audit #16).
    PICKUP_NOT_AUTHORIZED(HttpStatus.FORBIDDEN),

    // ---------- Attendance ----------
    ATTENDANCE_ALREADY_SUBMITTED(HttpStatus.CONFLICT),
    ATTENDANCE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND),
    LEAVE_OVERLAP(HttpStatus.CONFLICT),
    LEAVE_BALANCE_INSUFFICIENT(HttpStatus.CONFLICT),

    // ---------- Fee ----------
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_EXCEEDS_DUE(HttpStatus.BAD_REQUEST),
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND),
    // Student cannot be withdrawn / issued a TC while fees are outstanding, unless overridden (#13).
    FEE_CLEARANCE_REQUIRED(HttpStatus.CONFLICT),

    // ---------- Academics ----------
    EXAM_NOT_FOUND(HttpStatus.NOT_FOUND),
    MARKS_ALREADY_FINALIZED(HttpStatus.CONFLICT),
    MAX_MARKS_EXCEEDED(HttpStatus.BAD_REQUEST),
    REPORT_CARD_NOT_READY(HttpStatus.CONFLICT),

    // ---------- Communication ----------
    WHATSAPP_SEND_FAILED(HttpStatus.BAD_GATEWAY),
    TEMPLATE_NOT_APPROVED(HttpStatus.FAILED_DEPENDENCY),

    // ---------- Migration / OCR ----------
    MIGRATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    MIGRATION_JOB_IN_WRONG_STATE(HttpStatus.CONFLICT),

    // ---------- SaaS / billing / feature flags ----------
    // Feature is not enabled for this tenant — likely the plan doesn't include it, or a
    // platform-admin override disabled it. Status 403 since the JWT is fine but the action
    // isn't permitted by the subscription.
    FEATURE_DISABLED(HttpStatus.FORBIDDEN),
    // Caller is on a plan whose ceiling for the metric has been reached. Status 402 to be
    // semantically distinct from generic rate-limit / quota errors and to invite the client
    // to surface an "Upgrade plan" CTA. (HTTP 402 = Payment Required.)
    PLAN_LIMIT_EXCEEDED(HttpStatus.PAYMENT_REQUIRED),
    // Subscription is SUSPENDED or CANCELLED — mutating endpoints blocked. Reads still work.
    TENANT_SUSPENDED(HttpStatus.FORBIDDEN),

    // ---------- Approvals (maker-checker) ----------
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    APPROVAL_NOT_PENDING(HttpStatus.CONFLICT),
    // Maker == checker: the requester may not approve their own request (segregation of duties).
    APPROVAL_SELF_NOT_ALLOWED(HttpStatus.FORBIDDEN),

    // ---------- General ----------
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
