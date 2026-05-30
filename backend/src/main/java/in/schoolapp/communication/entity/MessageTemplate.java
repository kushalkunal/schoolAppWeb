package in.schoolapp.communication.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant override for a WhatsApp/notification message body. When present, the
 * {@link in.schoolapp.communication.template.MessageTemplateService} uses this body instead of the
 * built-in default. The {@code templateKey} is the name of the
 * {@link in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType} enum constant.
 */
@Entity
@Table(name = "message_templates",
       uniqueConstraints = @UniqueConstraint(name = "uq_message_templates_school_key",
           columnNames = {"school_id", "template_key"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    /** Matches the {@code MessageType.name()} value, e.g. "FEE_RECEIPT", "ABSENCE_ALERT". */
    @Column(name = "template_key", nullable = false, length = 60)
    private String templateKey;

    /**
     * The body text, may contain literal placeholders like {studentName}, {amount}, {date}.
     * The MessageTemplateService is responsible for substituting these at render time.
     */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
