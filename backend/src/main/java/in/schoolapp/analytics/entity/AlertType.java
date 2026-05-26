package in.schoolapp.analytics.entity;

/**
 * Kind of alert — determines template text and the dashboard group. Stored as a string in the
 * {@code alerts.alert_type} column so a new type can be rolled out without a migration.
 */
public enum AlertType {
    /** Student has been absent for N consecutive school days (ConsecutiveAbsenceDetector). */
    CONSECUTIVE_ABSENCE,
    /** Section's attendance has not been submitted by cut-off time (AttendanceNotSubmittedDetector). */
    ATTENDANCE_NOT_SUBMITTED,
    /** Month-over-month fee collection drop beyond the configured threshold (FeeCollectionDropDetector). */
    FEE_COLLECTION_DROP,
    /** Student flagged at-risk by composite academic/attendance/fee score (AtRiskDetectionService). */
    AT_RISK_STUDENT,
    /** Principal-generated ad-hoc alert or an escalation from another subsystem. */
    OTHER
}
