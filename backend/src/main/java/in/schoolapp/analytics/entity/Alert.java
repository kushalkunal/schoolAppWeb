package in.schoolapp.analytics.entity;

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
 * A surfaced issue requiring school-staff attention — chronic absence, pending attendance
 * submission, fee-collection drop, etc. Mapped to the V1 {@code alerts} table.
 * <p>
 * Alerts are created by scheduled detectors (never by direct API writes — the controller only
 * reads and dismisses) and consumed by the principal dashboard + daily WhatsApp digest.
 * Dismissal is soft: the row stays for audit but {@code isDismissed = true} hides it from the
 * active list.
 */
@Entity
@Table(name = "alerts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "action_url", columnDefinition = "TEXT")
    private String actionUrl;

    @Column(name = "is_dismissed", nullable = false)
    private boolean dismissed = false;

    @Column(name = "dismissed_by_id")
    private UUID dismissedById;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;

    /** Optional TTL — detectors that write daily rows set this to 24h so stale alerts age out. */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
