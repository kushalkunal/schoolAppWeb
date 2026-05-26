package in.schoolapp.communication.dto;

import in.schoolapp.communication.entity.WhatsAppInboxMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thread-summary row for the teacher/principal inbox view. {@code fromPhone} is returned in
 * masked form ("98765****10") — the full number is never exposed to the UI; reply dispatch
 * happens server-side via the parent id.
 */
public record InboxMessageResponse(
    UUID id,
    UUID parentId,
    UUID studentId,
    UUID routedToId,
    String fromPhoneMasked,
    String messageBody,
    boolean read,
    boolean resolved,
    OffsetDateTime receivedAt
) {
    public static InboxMessageResponse from(WhatsAppInboxMessage m, String maskedPhone) {
        return new InboxMessageResponse(
            m.getId(),
            m.getParentId(),
            m.getStudentId(),
            m.getRoutedToId(),
            maskedPhone,
            m.getMessageBody(),
            m.isRead(),
            m.isResolved(),
            m.getReceivedAt()
        );
    }
}
