package in.schoolapp.analytics.entity;

/**
 * Urgency of an {@link Alert}. Surfaces as colour-coding on the principal dashboard and
 * determines whether the alert is included in the daily digest WhatsApp (HIGH + CRITICAL only).
 */
public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
