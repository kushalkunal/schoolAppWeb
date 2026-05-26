package in.schoolapp.feature;

/**
 * Stable feature-flag keys used by {@code @RequiresFeature} and the platform-admin UI.
 * <p>
 * These mirror the rows seeded into the {@code features} catalog by Flyway V6. Keep the
 * Java constant name and the DB string identical so a grep across the codebase finds both
 * sides of the contract. Never rename a value — clients (and future override rows) persist
 * the string.
 * <p>
 * Note: this is a deliberate plain-string holder rather than an enum, so a new feature can be
 * added by a single SQL insert + adding one constant here, without breaking any switch
 * statements that exhaustively cover a Java enum.
 */
public final class FeatureKey {

    private FeatureKey() {}

    // ---------- Core (bundled with FREE) ----------
    public static final String STUDENTS            = "STUDENTS";
    public static final String ATTENDANCE          = "ATTENDANCE";
    public static final String SCHOOL_PROFILE      = "SCHOOL_PROFILE";

    // ---------- Standard (STARTER+) ----------
    public static final String FEE                 = "FEE";
    public static final String ACADEMICS           = "ACADEMICS";
    public static final String COMMUNICATION_CORE  = "COMMUNICATION_CORE";
    public static final String CIRCULARS           = "CIRCULARS";
    public static final String WHATSAPP_INBOX      = "WHATSAPP_INBOX";

    // ---------- Premium (GROWTH+) ----------
    public static final String ANALYTICS           = "ANALYTICS";
    public static final String SUBSTITUTE_TEACHERS = "SUBSTITUTE_TEACHERS";
    public static final String TEACHER_ASSIGNMENTS = "TEACHER_ASSIGNMENTS";
    public static final String EXAM_ELIGIBILITY    = "EXAM_ELIGIBILITY";
    public static final String AUDIT_LOG           = "AUDIT_LOG";

    // ---------- Add-ons (ENTERPRISE / opt-in) ----------
    public static final String PAPER_MIGRATION     = "PAPER_MIGRATION";
    public static final String DATA_EXPORT         = "DATA_EXPORT";
    public static final String MOBILE_SYNC         = "MOBILE_SYNC";
    public static final String DPDP_REQUESTS       = "DPDP_REQUESTS";
    public static final String PAYMENT_GATEWAY     = "PAYMENT_GATEWAY";
    public static final String STUDENT_DOCUMENTS   = "STUDENT_DOCUMENTS";

    // ---------- Slice 12+ expansion. Every value here also exists as a row in
    // features() seeded by Flyway V14. Keep the two in lock-step.

    // Documents
    public static final String PDF_GENERATION       = "PDF_GENERATION";
    public static final String TRANSFER_CERTIFICATE = "TRANSFER_CERTIFICATE";
    public static final String BONAFIDE_CERTIFICATE = "BONAFIDE_CERTIFICATE";
    public static final String HALL_TICKETS         = "HALL_TICKETS";
    public static final String DOCUMENT_VAULT       = "DOCUMENT_VAULT";

    // Onboarding
    public static final String BULK_IMPORT          = "BULK_IMPORT";

    // Communication
    public static final String SMS_FALLBACK         = "SMS_FALLBACK";
    public static final String PUSH_NOTIFICATIONS   = "PUSH_NOTIFICATIONS";
    public static final String TRANSLATION          = "TRANSLATION";
    public static final String VOICE_CALLS          = "VOICE_CALLS";

    // Finance
    public static final String LATE_FEE_AUTOMATION  = "LATE_FEE_AUTOMATION";
    public static final String FEE_DISCOUNTS        = "FEE_DISCOUNTS";
    public static final String FEE_REFUNDS          = "FEE_REFUNDS";
    public static final String FEE_INSTALLMENTS     = "FEE_INSTALLMENTS";
    public static final String FEE_GST              = "FEE_GST";

    // HR
    public static final String STAFF_ATTENDANCE     = "STAFF_ATTENDANCE";
    public static final String LEAVE_MANAGEMENT     = "LEAVE_MANAGEMENT";
    public static final String PAYROLL              = "PAYROLL";

    // Acquisition
    public static final String ADMISSIONS_FUNNEL    = "ADMISSIONS_FUNNEL";

    // Boarding / Ops
    public static final String HOSTEL               = "HOSTEL";
    public static final String CAFETERIA            = "CAFETERIA";
    public static final String INVENTORY            = "INVENTORY";

    // AI
    public static final String AI_RISK_SCORING      = "AI_RISK_SCORING";
    public static final String AI_CHATBOT           = "AI_CHATBOT";
    public static final String AI_AUTO_GRADE        = "AI_AUTO_GRADE";

    // India compliance / govt integrations
    public static final String UDISE_EXPORT         = "UDISE_EXPORT";
    public static final String DIGILOCKER_PUSH      = "DIGILOCKER_PUSH";
    public static final String DIKSHA_SYNC          = "DIKSHA_SYNC";
    public static final String NAD_INTEGRATION     = "NAD_INTEGRATION";

    // Power features
    public static final String AUTO_TIMETABLE       = "AUTO_TIMETABLE";
    public static final String PERIOD_ATTENDANCE    = "PERIOD_ATTENDANCE";
    public static final String BIOMETRIC_DEVICES    = "BIOMETRIC_DEVICES";
    public static final String FACE_RECOGNITION    = "FACE_RECOGNITION";
    public static final String GPS_TRANSPORT        = "GPS_TRANSPORT";

    // Slice 31 — fee structure matrix
    public static final String FEE_STRUCTURE        = "FEE_STRUCTURE";

    // ---------- Slice 33 — parent notification categories ----------
    // Master switch — if false, every per-category check below is a no-op even if enabled.
    public static final String PARENT_NOTIFICATIONS         = "PARENT_NOTIFICATIONS";
    // Per-category fine-grained control. Tenant admins can enable just "receipts only" etc.
    public static final String PARENT_NOTIFY_ABSENCE        = "PARENT_NOTIFY_ABSENCE";
    public static final String PARENT_NOTIFY_LATE_ARRIVAL   = "PARENT_NOTIFY_LATE_ARRIVAL";
    public static final String PARENT_NOTIFY_FEE_RECEIPT    = "PARENT_NOTIFY_FEE_RECEIPT";
    public static final String PARENT_NOTIFY_FEE_DUE        = "PARENT_NOTIFY_FEE_DUE";
    public static final String PARENT_NOTIFY_FEE_OVERDUE    = "PARENT_NOTIFY_FEE_OVERDUE";
    public static final String PARENT_NOTIFY_REPORT_CARD    = "PARENT_NOTIFY_REPORT_CARD";
    public static final String PARENT_NOTIFY_MARKS          = "PARENT_NOTIFY_MARKS";
    public static final String PARENT_NOTIFY_HOMEWORK       = "PARENT_NOTIFY_HOMEWORK";
    public static final String PARENT_NOTIFY_LIBRARY_OVERDUE = "PARENT_NOTIFY_LIBRARY_OVERDUE";
    public static final String PARENT_NOTIFY_EXAM_SCHEDULE  = "PARENT_NOTIFY_EXAM_SCHEDULE";
    public static final String PARENT_NOTIFY_CIRCULAR       = "PARENT_NOTIFY_CIRCULAR";
    public static final String PARENT_NOTIFY_BIRTHDAY       = "PARENT_NOTIFY_BIRTHDAY";

    // ---------- Slice 34 — daily ops gap-fillers ----------
    public static final String VISITOR_MANAGEMENT   = "VISITOR_MANAGEMENT";
    public static final String CASH_RECONCILIATION  = "CASH_RECONCILIATION";
    public static final String EXPENSE_TRACKING     = "EXPENSE_TRACKING";
    public static final String INCIDENT_LOG         = "INCIDENT_LOG";
    public static final String INFIRMARY_LOG        = "INFIRMARY_LOG";
    public static final String STUDENT_DOCUMENT_VAULT = "STUDENT_DOCUMENT_VAULT";
    public static final String UNIFIED_INBOX        = "UNIFIED_INBOX";
    public static final String ALERTS_FEED          = "ALERTS_FEED";
    public static final String LOW_STOCK_ALERTS     = "LOW_STOCK_ALERTS";
    public static final String PTM_SCHEDULING       = "PTM_SCHEDULING";
}
