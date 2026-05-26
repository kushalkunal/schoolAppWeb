package in.schoolapp.communication.entity;

import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit trail for every outbound communication. Mirrors the {@code notification_log} table
 * from V1. Populated by {@link in.schoolapp.communication.NotificationLogger} — queued at
 * dispatch time, status-updated by the BSP webhook.
 * <p>
 * {@code wa_message_id} is the BSP's own id (e.g. {@code wamid.HBgN...}) and the correlation
 * key between "we sent a message" and "BSP says it was delivered/read". Indexed partially in
 * V1 so webhook status lookups are O(1).
 */
@Entity
@Table(name = "notification_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    /** Stored as string to match {@link MessageType}'s {@code name()} — drives template joins. */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "recipient_phone", nullable = false, length = 15)
    private String recipientPhone;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "circular_id")
    private UUID circularId;

    @Column(nullable = false, length = 10)
    private String channel = "WHATSAPP";

    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "wa_message_id", length = 100)
    private String waMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
