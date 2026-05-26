package in.schoolapp.communication.dispatcher;

import java.util.UUID;

/**
 * Inputs for a single outbound WhatsApp message. {@code mediaUrl} is optional — populated when
 * the message attaches a receipt PDF or a report-card PDF. {@code audit} is optional context
 * the auditing decorator uses to write a {@code notification_log} row; call-sites that can't
 * supply it (e.g. OTP dispatches that pre-date student creation) pass {@code null}.
 */
public record WhatsAppMessage(
    String toPhone,
    String body,
    String mediaUrl,
    MessageType type,
    Audit audit
) {
    public static WhatsAppMessage text(String toPhone, String body, MessageType type) {
        return new WhatsAppMessage(toPhone, body, null, type, null);
    }

    public static WhatsAppMessage withMedia(String toPhone, String body, String mediaUrl, MessageType type) {
        return new WhatsAppMessage(toPhone, body, mediaUrl, type, null);
    }

    /** Same as {@link #text(String, String, MessageType)} but tags the message with audit context. */
    public static WhatsAppMessage text(String toPhone, String body, MessageType type, Audit audit) {
        return new WhatsAppMessage(toPhone, body, null, type, audit);
    }

    /** Same as {@link #withMedia(String, String, String, MessageType)} but tags audit context. */
    public static WhatsAppMessage withMedia(String toPhone, String body, String mediaUrl,
                                            MessageType type, Audit audit) {
        return new WhatsAppMessage(toPhone, body, mediaUrl, type, audit);
    }

    /**
     * Classifies the template category — drives Meta WhatsApp Business API template selection
     * (UTILITY vs MARKETING) and surfaces in {@link in.schoolapp.communication.entity.NotificationLog}.
     */
    public enum MessageType {
        ABSENCE_ALERT,
        LATE_ARRIVAL_ALERT,
        FEE_RECEIPT,
        FEE_REMINDER,
        CIRCULAR,
        REPORT_CARD,
        OTP,
        EMERGENCY
    }

    /**
     * Optional context for {@code notification_log} correlation. {@code schoolId} is required
     * if a log row is to be written; the other fields enrich the row but are not mandatory.
     */
    public record Audit(
        UUID schoolId,
        UUID studentId,
        UUID parentId,
        String recipientName
    ) {
        public static Audit forSchool(UUID schoolId) {
            return new Audit(schoolId, null, null, null);
        }

        public static Audit forParent(UUID schoolId, UUID studentId, UUID parentId, String name) {
            return new Audit(schoolId, studentId, parentId, name);
        }
    }
}
