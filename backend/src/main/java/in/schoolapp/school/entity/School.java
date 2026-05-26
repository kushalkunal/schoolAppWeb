package in.schoolapp.school.entity;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A School is the tenant root — it does not extend {@link in.schoolapp.common.BaseEntity}
 * because it has no {@code school_id} of its own. Every other tenant-scoped entity holds a
 * reference back via {@code schoolId}.
 */
@Entity
@Table(name = "schools")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "principal_name", nullable = false, length = 255)
    private String principalName;

    /**
     * Normalised 10-digit Indian mobile. Globally unique (partial unique index).
     * Nullable since {@code app.signup.channel=EMAIL} allows signup without a phone.
     */
    @Column(length = 15, unique = true)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Board board;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "whatsapp_number", length = 15)
    private String whatsappNumber;

    @Column(name = "wa_configured", nullable = false)
    private boolean waConfigured = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Flexible JSONB column for per-school config: onboarding progress, grading scale, receipt
     * sequence counter, holiday calendar, notification preferences. Keeps the core schema stable
     * as features are added.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settings = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
