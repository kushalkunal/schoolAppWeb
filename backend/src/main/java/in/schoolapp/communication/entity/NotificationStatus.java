package in.schoolapp.communication.entity;

/**
 * Lifecycle of an outbound WhatsApp / email notification. Mirrors the BSP-reported status
 * vocabulary (Meta Cloud API: {@code sent / delivered / read / failed}) with a {@code QUEUED}
 * pre-state for the moment between "we decided to send" and "the BSP has accepted it".
 */
public enum NotificationStatus {
    /** Row created, dispatch not yet attempted. */
    QUEUED,
    /** Dispatcher handed the message to the BSP. */
    SENT,
    /** BSP webhook reported {@code delivered} — message on recipient's device. */
    DELIVERED,
    /** Recipient opened the message (WhatsApp read-receipt). */
    READ,
    /** Dispatch or delivery failed. {@code error_message} captures the cause. */
    FAILED
}
