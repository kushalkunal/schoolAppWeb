/**
 * Single-tenant frontend global feature flags.
 *
 * ## Deployment model
 *
 * Recap of the architecture: one backend engine serves many tenants, but the FRONTEND is
 * deployed as one single-tenant Next.js app per school. So while the backend gates features
 * with per-tenant DB-stored flags, the frontend has no notion of "another tenant" — its flag
 * set is a constant baked in at `next build` time via `NEXT_PUBLIC_FEATURE_*` env vars.
 *
 * Concretely:
 *   - Build-time: `NEXT_PUBLIC_FEATURE_ATTENDANCE=true` ⇒ the attendance nav + routes render.
 *   - Runtime backend feature flags still gate the actual API calls; the FE flag is a
 *     PRESENTATION shortcut so we don't render routes the deployment was never meant to expose.
 *
 * ## Naming
 *
 * Keys match the backend {@code FeatureKey} string constants 1:1. This way an ops engineer
 * looking at a feature can grep both repos for the same identifier.
 *
 * ## Defaults
 *
 * The DEFAULTS table below ships every flag turned ON for a "demo / full-fat" deploy. Schools
 * that want a slimmer install set the corresponding env var to "false" at build time.
 */

export const FEATURE_KEYS = [
  // Core
  'STUDENTS', 'ATTENDANCE', 'SCHOOL_PROFILE',
  // Standard
  'FEE', 'FEE_STRUCTURE', 'ACADEMICS',
  'COMMUNICATION_CORE', 'CIRCULARS', 'WHATSAPP_INBOX',
  // Premium
  'ANALYTICS', 'SUBSTITUTE_TEACHERS', 'TEACHER_ASSIGNMENTS',
  'EXAM_ELIGIBILITY', 'AUDIT_LOG',
  // Add-ons
  'PAPER_MIGRATION', 'DATA_EXPORT', 'MOBILE_SYNC',
  'DPDP_REQUESTS', 'PAYMENT_GATEWAY', 'STUDENT_DOCUMENTS',
  // Slice 12+ expansion
  'PDF_GENERATION', 'TRANSFER_CERTIFICATE', 'BONAFIDE_CERTIFICATE',
  'HALL_TICKETS', 'DOCUMENT_VAULT',
  'BULK_IMPORT',
  'SMS_FALLBACK', 'PUSH_NOTIFICATIONS', 'TRANSLATION', 'VOICE_CALLS',
  'LATE_FEE_AUTOMATION', 'FEE_DISCOUNTS', 'FEE_REFUNDS',
  'FEE_INSTALLMENTS', 'FEE_GST',
  'STAFF_ATTENDANCE', 'LEAVE_MANAGEMENT', 'PAYROLL',
  'ADMISSIONS_FUNNEL',
  'HOSTEL', 'CAFETERIA', 'INVENTORY',
  'AI_RISK_SCORING', 'AI_CHATBOT', 'AI_AUTO_GRADE',
  'UDISE_EXPORT', 'DIGILOCKER_PUSH', 'DIKSHA_SYNC', 'NAD_INTEGRATION',
  'AUTO_TIMETABLE', 'PERIOD_ATTENDANCE', 'BIOMETRIC_DEVICES',
  'FACE_RECOGNITION', 'GPS_TRANSPORT',
  // Slice 33 — parent notifications
  'PARENT_NOTIFICATIONS',
  'PARENT_NOTIFY_ABSENCE', 'PARENT_NOTIFY_LATE_ARRIVAL',
  'PARENT_NOTIFY_FEE_RECEIPT', 'PARENT_NOTIFY_FEE_DUE', 'PARENT_NOTIFY_FEE_OVERDUE',
  'PARENT_NOTIFY_REPORT_CARD', 'PARENT_NOTIFY_MARKS',
  'PARENT_NOTIFY_HOMEWORK', 'PARENT_NOTIFY_LIBRARY_OVERDUE',
  'PARENT_NOTIFY_EXAM_SCHEDULE', 'PARENT_NOTIFY_CIRCULAR',
  'PARENT_NOTIFY_BIRTHDAY',
  // Slice 34 — daily-ops gaps
  'VISITOR_MANAGEMENT', 'CASH_RECONCILIATION', 'EXPENSE_TRACKING',
  'INCIDENT_LOG', 'INFIRMARY_LOG', 'STUDENT_DOCUMENT_VAULT',
  'UNIFIED_INBOX', 'ALERTS_FEED', 'LOW_STOCK_ALERTS', 'PTM_SCHEDULING',
] as const;

export type FeatureKey = (typeof FEATURE_KEYS)[number];

/**
 * Read an env var that may be undefined. Returns true unless the value is exactly the string
 * "false" (case-insensitive). Errs on the side of "on" so adding a new feature key doesn't
 * require updating every deployment's env file.
 */
function envOn(key: string): boolean {
  const v = process.env[key];
  if (v == null) return true;          // unset == default on
  const t = String(v).trim().toLowerCase();
  return !(t === 'false' || t === '0' || t === 'off' || t === 'no');
}

/**
 * Build-time constant map. Computed once at module init. `NEXT_PUBLIC_FEATURE_X=false`
 * (where X is the snake-cased flag name) disables it for this build.
 */
export const FEATURE_FLAGS: Readonly<Record<FeatureKey, boolean>> = Object.freeze(
  Object.fromEntries(
    FEATURE_KEYS.map((k) => [k, envOn(`NEXT_PUBLIC_FEATURE_${k}`)]),
  ) as Record<FeatureKey, boolean>,
);

/**
 * Synchronous accessor — feature flags are constants, so no need for context plumbing in 99%
 * of call sites. Use {@link useFeature} only when you want React to re-render if you swap the
 * environment (it never does at runtime, but the hook is conventional).
 */
export function isFeatureEnabled(key: FeatureKey): boolean {
  return FEATURE_FLAGS[key] === true;
}
