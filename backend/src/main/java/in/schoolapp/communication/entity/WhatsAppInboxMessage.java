package in.schoolapp.communication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * A single inbound WhatsApp message from a parent — produced by the BSP webhook when a parent
 * replies to a template we sent (absence alert, receipt, reminder). Mapped to the
 * {@code whatsapp_inbox_messages} table from V1.
 * <p>
 * {@code routedToId} points at the class teacher of the replying parent's child (resolved by
 * {@link in.schoolapp.communication.WhatsAppInboxRoutingService}). When we can't identify the
 * parent (unknown phone) the row still lands with {@code routedToId=null} so nothing is lost —
 * a principal can later assign it.
 */
@Entity
@Table(name = "whatsapp_inbox_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppInboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "from_phone", nullable = false, length = 15)
    private String fromPhone;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "routed_to_id")
    private UUID routedToId;

    @Column(name = "message_body", nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "wa_message_id", length = 100)
    private String waMessageId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "is_resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
