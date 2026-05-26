package in.schoolapp.communication.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A bulk-broadcast message to parents (LLD §5.4). Sent-counts are incremented by the dispatch
 * job; delivery/read counts are updated via the WhatsApp webhook when BSP reports status.
 */
@Entity
@Table(name = "circulars")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Circular {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private CircularTargetType targetType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "target_ids", columnDefinition = "uuid[]")
    private UUID[] targetIds;

    @Column(nullable = false, length = 20)
    private String language = "en";

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Column(name = "sent_count", nullable = false)
    private int sentCount = 0;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount = 0;

    @Column(name = "read_count", nullable = false)
    private int readCount = 0;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
