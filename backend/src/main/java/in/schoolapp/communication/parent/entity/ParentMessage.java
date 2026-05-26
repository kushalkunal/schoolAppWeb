package in.schoolapp.communication.parent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slice 34 — denormalised inbox cache. Every outbound message that ParentNotificationService
 * dispatches also lands here so the /inbox page can render a per-student timeline in O(log n)
 * without joining the wider notification_log.
 */
@Entity
@Table(name = "parent_messages")
@Getter @Setter @NoArgsConstructor
public class ParentMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false) private UUID id;
    @Column(name = "school_id", nullable = false, updatable = false) private UUID schoolId;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(nullable = false, length = 15) private String channel;       // WHATSAPP / EMAIL / SMS
    @Column(nullable = false, length = 40) private String category;
    @Column(length = 255) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "media_url", columnDefinition = "TEXT") private String mediaUrl;
    @Column(nullable = false, length = 15) private String status = "SENT";
    @Column(name = "sent_at", nullable = false) private OffsetDateTime sentAt = OffsetDateTime.now();
    @Column(name = "delivered_at") private OffsetDateTime deliveredAt;
    @Column(name = "read_at") private OffsetDateTime readAt;
}
